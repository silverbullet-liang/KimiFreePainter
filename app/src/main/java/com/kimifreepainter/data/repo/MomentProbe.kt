package com.kimifreepainter.data.repo

import com.kimifreepainter.data.api.KimiApi

/**
 * 成品帖识别。四条判据依次尝试，任何一条命中即返回：
 *
 * 1. 列表里出现 refMomentId == 触发帖的动态（成品帖引用触发帖，最直接）
 * 2. 列表里新出现、带图、来源为 AI 的动态
 * 3. 列表不带图时，对每个新动态回查详情再看图
 * 4. 触发帖评论区里机器人回复的 links（富文本链接，正文里没有 URL）
 *
 * 不再单靠 content.source：该字段在部分帖子上为空，会把成品帖整体漏掉。
 * author 只用来排除他人动态——成品帖的 author 就是发起人自己。
 */
object MomentProbe {

    private const val AI_SOURCE = "MOMENT_SOURCE_AI"

    suspend fun scan(
        triggerId: String,
        watermark: Long,
        ownerId: String,
        seen: Set<String>
    ): KimiApi.Moment? {
        val feeds = runCatching { KimiApi.listMyFeeds(20).first }.getOrDefault(emptyList())
        if (feeds.isEmpty()) return null

        val mine = feeds.filter { m ->
            m.id.isNotEmpty() && m.id != triggerId && m.id !in seen &&
                (ownerId.isEmpty() || m.authorId.isEmpty() || m.authorId == ownerId) &&
                (watermark <= 0 || m.createTime <= 0 || m.createTime >= watermark - 120)
        }

        // 判据 1：引用关系
        mine.firstOrNull { it.refMomentId.isNotEmpty() && it.refMomentId == triggerId }
            ?.let { return it }

        // 判据 2：AI 来源且带图
        mine.firstOrNull { it.source == AI_SOURCE && it.images.isNotEmpty() }?.let { return it }

        // 判据 3：新帖回查详情
        for (cand in mine) {
            val detail = runCatching { KimiApi.getMoment(cand.id) }.getOrNull() ?: continue
            if (detail.images.isNotEmpty()) return detail
        }
        return null
    }

    /** 兜底：评论区机器人回复。链接在 links 里，正文只有「查看」两个字的显示文本。 */
    suspend fun viaComments(
        triggerId: String,
        ownerId: String,
        seen: Set<String>
    ): KimiApi.Moment? {
        val comments = runCatching { KimiApi.listComments(triggerId, 20) }.getOrNull().orEmpty()
        for (reply in comments) {
            if (reply.type != "COMMENT_TYPE_BOT" && !reply.aiGenerated) continue
            for (src in reply.links + reply.text) {
                for (id in extractMomentIds(src)) {
                    if (id == triggerId || id in seen) continue
                    val m = runCatching { KimiApi.getMoment(id) }.getOrNull() ?: continue
                    if (m.images.isNotEmpty() || m.source == AI_SOURCE) return m
                }
            }
        }
        return null
    }

    /** 从文本里抽候选 moment id（链接末段）。不做接口调用，纯解析。 */
    fun extractMomentIds(text: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("""https?://[^\s，。、）)】\]"']+""").findAll(text).forEach { hit ->
            val path = hit.value.substringAfter("://").substringAfter('/', "")
                .substringBefore('?').substringBefore('#')
            val last = path.trim('/').split('/').lastOrNull().orEmpty()
            if (last.length >= 8 && last.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                out += last
            }
        }
        return out.toList()
    }
}
