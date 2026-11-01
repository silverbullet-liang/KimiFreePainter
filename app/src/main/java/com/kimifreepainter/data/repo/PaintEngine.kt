package com.kimifreepainter.data.repo

import android.content.Context
import com.kimifreepainter.data.api.ApiException
import com.kimifreepainter.data.api.KimiApi
import com.kimifreepainter.data.auth.AuthManager
import com.kimifreepainter.data.db.AppDatabase
import com.kimifreepainter.data.db.PainterTask
import com.kimifreepainter.data.db.SeenMoment
import com.kimifreepainter.data.db.Watermark
import com.kimifreepainter.data.settings.CleanMode
import com.kimifreepainter.data.settings.Settings
import com.kimifreepainter.data.settings.SettingsRepository
import com.kimifreepainter.util.ImageSaver
import com.kimifreepainter.util.TextUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

enum class TaskStatus {
    QUEUED, POSTING, GENERATING, DOWNLOADING, CLEANING, DONE, FAILED, CANCELLED
}

/**
 * 串行任务引擎：并发度恒为 1。
 * 串行既天然满足平台限流，也避免两个任务同时轮询导致成品帖串位。
 */
class PaintEngine(context: Context) {

    private val app = context.applicationContext
    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Long>(Channel.UNLIMITED)

    /** 当前是否有任务在跑，UI 用它在提交按钮上做置灰。 */
    val running = MutableStateFlow(false)

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (taskId in queue) {
                running.value = true
                runCatching { runTask(taskId) }
                running.value = false
            }
        }
        // 进程被杀后恢复未完成任务
        scope.launch {
            db.tasks().pending().forEach { queue.send(it.id) }
        }
    }

    suspend fun submit(prompt: String): Long? {
        if (prompt.isBlank()) return null
        if (db.tasks().activeCount() >= 10) return null
        val cfg = settingsRepo.flow.first()
        val id = db.tasks().insert(
            PainterTask(
                prompt = prompt.trim(),
                status = TaskStatus.QUEUED.name,
                botUserId = cfg.botUserId,
                botName = cfg.botName,
                createdAt = System.currentTimeMillis()
            )
        )
        queue.send(id)
        return id
    }

    fun retry(old: PainterTask) {
        scope.launch {
            val id = db.tasks().insert(
                old.copy(
                    id = 0,
                    status = TaskStatus.QUEUED.name,
                    triggerMomentId = null,
                    resultMomentId = null,
                    imageUrls = "[]",
                    localPaths = null,
                    errorCode = null,
                    errorMsg = null,
                    cleanFailed = false,
                    deletedFromCommunity = false,
                    retryOf = old.id,
                    createdAt = System.currentTimeMillis(),
                    finishedAt = null
                )
            )
            queue.send(id)
        }
    }

    fun cancel(id: Long) {
        scope.launch {
            val t = db.tasks().byId(id) ?: return@launch
            if (t.status == TaskStatus.QUEUED.name) {
                db.tasks().update(t.copy(status = TaskStatus.CANCELLED.name))
            }
        }
    }

    private suspend fun patch(id: Long, block: (PainterTask) -> PainterTask) {
        val t = db.tasks().byId(id) ?: return
        db.tasks().update(block(t))
    }

    private suspend fun fail(id: Long, code: Int, msg: String) = patch(id) {
        it.copy(status = TaskStatus.FAILED.name, errorCode = code, errorMsg = msg, finishedAt = System.currentTimeMillis())
    }

    private suspend fun runTask(taskId: Long) {
        val task = db.tasks().byId(taskId) ?: return
        if (task.status != TaskStatus.QUEUED.name) return
        val cfg = settingsRepo.flow.first()

        try {
            // 1) 解析画师
            val bot = ensureBot(cfg)
            if (bot == null) {
                fail(taskId, -2, "未找到可 @ 的画师")
                return
            }
            patch(taskId) { it.copy(status = TaskStatus.POSTING.name, botUserId = bot.first, botName = bot.second) }

            // 2) 水位线
            val ownerId = AuthManager.userId
            val watermark = currentWatermark(ownerId)
            val seen = db.moments().all().toHashSet()

            // 3) 发帖
            val triggerId = createWithRetry(task.prompt, bot.first, bot.second)
            if (triggerId.isEmpty()) {
                fail(taskId, -1, "发帖未返回 ID")
                return
            }
            patch(taskId) {
                it.copy(status = TaskStatus.GENERATING.name, triggerMomentId = triggerId)
            }

            // 4) 轮询成品
            val moment = awaitResult(taskId, triggerId, watermark, ownerId, seen, cfg)
            if (moment == null) {
                fail(taskId, -3, "等待超时")
                return
            }
            db.moments().remember(SeenMoment(moment.id, System.currentTimeMillis()))
            db.moments().trim()
            patch(taskId) {
                it.copy(
                    status = TaskStatus.DOWNLOADING.name,
                    resultMomentId = moment.id,
                    title = moment.title.ifEmpty { null },
                    description = TextUtil.extractIntro(moment.text, task.prompt).ifEmpty { null }
                )
            }

            // 5) 下载（字节直存）
            val paths = mutableListOf<String>()
            moment.images.forEachIndexed { idx, img ->
                val bytes = downloadWithRetry(img.best)
                if (bytes != null) {
                    val f = ImageSaver.saveToHistory(app, taskId, idx, bytes)
                    paths += f.absolutePath
                }
            }
            if (paths.isEmpty()) {
                fail(taskId, -4, "图片下载失败")
                return
            }
            db.tasks().update(
                db.tasks().byId(taskId)!!.copy(
                    status = TaskStatus.CLEANING.name,
                    imageUrls = JSONArray(moment.images.map { it.best }).toString(),
                    localPaths = JSONArray(paths).toString()
                )
            )

            // 6) 清理
            cleanIfNeeded(cfg, triggerId, moment.id)
            patch(taskId) {
                it.copy(
                    status = TaskStatus.DONE.name,
                    deletedFromCommunity = cfg.cleanMode != CleanMode.NONE,
                    finishedAt = System.currentTimeMillis()
                )
            }
        } catch (e: ApiException) {
            fail(taskId, e.code, e.message ?: "请求失败")
        } catch (e: Exception) {
            fail(taskId, -99, e.message ?: "未知错误")
        }
    }

    private suspend fun ensureBot(cfg: Settings): Pair<String, String>? {
        if (cfg.botUserId.isNotEmpty() && cfg.botName.isNotEmpty()) {
            return cfg.botUserId to cfg.botName
        }
        val bots = runCatching { KimiApi.listBots() }.getOrNull().orEmpty()
        val bot = bots.firstOrNull { it.name.contains("画师") } ?: bots.firstOrNull() ?: return null
        settingsRepo.setBot(bot.userId, bot.name)
        return bot.userId to bot.name
    }

    private suspend fun currentWatermark(ownerId: String): Long {
        val cached = db.moments().watermark(ownerId)
        val latest = runCatching { KimiApi.listMyFeeds(10).first.maxOfOrNull { it.createTime } }
            .getOrNull() ?: 0L
        val w = maxOf(cached ?: 0L, latest)
        db.moments().setWatermark(Watermark(ownerId, w, System.currentTimeMillis()))
        return w
    }

    private suspend fun createWithRetry(prompt: String, botId: String, botName: String): String {
        var attempt = 0
        while (true) {
            try {
                return KimiApi.createMoment(prompt, botId, botName)
            } catch (e: ApiException) {
                attempt++
                if (e.code == 429 && attempt <= 3) {
                    delay(120_000)
                    continue
                }
                if (attempt >= 2) throw e
                delay(if (attempt == 1) 3_000 else 9_000)
            }
        }
    }

    private suspend fun awaitResult(
        taskId: Long,
        triggerId: String,
        watermark: Long,
        ownerId: String,
        seen: Set<String>,
        cfg: Settings
    ): KimiApi.Moment? {
        val deadline = System.currentTimeMillis() + cfg.timeoutSec * 1000L
        val interval = cfg.pollIntervalSec * 1000L
        var strikes = 0
        while (System.currentTimeMillis() < deadline) {
            delay(interval)
            val hit = runCatching { MomentProbe.scan(triggerId, watermark, ownerId, seen) }.getOrNull()
            if (hit != null) return hit
            // 中途被取消
            val cur = db.tasks().byId(taskId) ?: return null
            if (cur.status == TaskStatus.CANCELLED.name) return null
            strikes++
            if (strikes % 3 == 0) {
                val fb = runCatching { MomentProbe.viaComments(triggerId, ownerId, seen) }.getOrNull()
                if (fb != null) return fb
            }
        }
        return runCatching { MomentProbe.viaComments(triggerId, ownerId, seen) }.getOrNull()
    }

    private suspend fun downloadWithRetry(url: String): ByteArray? {
        repeat(3) { i ->
            runCatching { return KimiApi.download(url) }
            delay(1_000L * (i + 1))
        }
        return null
    }

    private suspend fun cleanIfNeeded(cfg: Settings, triggerId: String, resultId: String) {
        if (cfg.cleanMode == CleanMode.NONE) return
        if (cfg.cleanDelaySec > 0) delay(cfg.cleanDelaySec * 1000L)
        runCatching { KimiApi.deleteMoment(triggerId) }
        if (cfg.cleanMode == CleanMode.ALL) {
            runCatching { KimiApi.deleteMoment(resultId) }
        }
    }

    suspend fun deleteLocal(taskId: Long) {
        ImageSaver.deleteTaskDir(app, taskId)
        db.tasks().delete(taskId)
    }

    suspend fun clearHistory() {
        db.tasks().observeAll().first().forEach { ImageSaver.deleteTaskDir(app, it.id) }
        db.tasks().clear()
    }

    suspend fun redelete(task: PainterTask) {
        task.triggerMomentId?.let { runCatching { KimiApi.deleteMoment(it) } }
        if (task.deletedFromCommunity || task.cleanFailed) {
            task.resultMomentId?.let { runCatching { KimiApi.deleteMoment(it) } }
        }
        db.tasks().update(task.copy(deletedFromCommunity = true, cleanFailed = false))
    }
}
