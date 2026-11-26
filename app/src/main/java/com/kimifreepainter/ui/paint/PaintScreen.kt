package com.kimifreepainter.ui.paint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kimifreepainter.R
import com.kimifreepainter.data.auth.AuthManager
import com.kimifreepainter.data.db.PainterTask
import com.kimifreepainter.data.repo.PaintEngine
import com.kimifreepainter.data.repo.TaskStatus
import com.kimifreepainter.data.settings.Settings
import com.kimifreepainter.util.ImageSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintScreen(
    tasks: List<PainterTask>,
    settings: Settings,
    engine: PaintEngine,
    running: Boolean,
    onOpenSettings: () -> Unit,
    onOpenDetail: (PainterTask) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf(settings.draft) }
    var toast by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    fun statusOf(t: PainterTask) = runCatching { TaskStatus.valueOf(t.status) }.getOrDefault(TaskStatus.QUEUED)
    val active = tasks.filter { statusOf(it).ordinal < TaskStatus.DONE.ordinal }
    val finished = tasks.filter { it.status == TaskStatus.DONE.name }.take(6)
    // 失败与取消必须露出来：之前这两个状态既不进进行中也不进已完成，任务直接消失
    val failed = tasks.filter {
        it.status == TaskStatus.FAILED.name || it.status == TaskStatus.CANCELLED.name
    }.take(6)
    var mode by rememberSaveable { mutableStateOf("normal") }
    var comicCount by rememberSaveable { mutableIntStateOf(4) }
    var comicBg by rememberSaveable { mutableStateOf("") }
    var comicStyle by rememberSaveable { mutableStateOf("") }
    var cellTexts by remember { mutableStateOf(listOf("", "", "", "")) }
    var wmPos by rememberSaveable { mutableStateOf("左上角") }
    var wmCustom by rememberSaveable { mutableStateOf("") }
    var wmCustomOn by rememberSaveable { mutableStateOf(false) }
    var wmText by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<File>>(emptyList()) }
    val needSlots = if (mode == "comic") comicCount else 1
    val queueFull = active.size + needSlots > 10
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val added = mutableListOf<File>()
        for (uri in uris.take(9 - sources.size)) {
            val ext = context.contentResolver.getType(uri)?.substringAfter('/', "jpg") ?: "jpg"
            val f = File(context.cacheDir, "input_${System.currentTimeMillis()}_${added.size}.$ext")
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    f.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (ok && f.length() > 0) added += f else f.delete()
        }
        if (added.isNotEmpty()) sources = sources + added
        else toast = context.getString(R.string.paint_image_failed)
    }
    val canSubmit = (!queueFull && AuthManager.isLoggedIn) && when (mode) {
        "comic" -> cellTexts.take(comicCount).all { it.isNotBlank() }
        "clean" -> wmText.isNotBlank() && sources.isNotEmpty()
        else -> prompt.isNotBlank()
    }

    fun cleanPromptText(): String {
        val where = if (wmCustomOn) wmCustom.ifBlank { "指定位置" } else wmPos
        return "请去除图片${where}的水印「${wmText.trim()}」。只移除水印本身，完整保留原图的构图、人物、色彩、光影和清晰度，其余部分不要改动。输出一张不带任何水印的完整图片。"
    }

    fun comicPromptText(i: Int): String {
        val cell = cellTexts.getOrNull(i).orEmpty().ifBlank { "第 ${i + 1} 格画面" }
        return listOf(comicBg.trim(), comicStyle.trim(), cell).filter { it.isNotEmpty() }
            .joinToString("，") + "。这是 ${comicCount} 格漫画中的第 ${i + 1} 格，只画这一格，输出一张单幅图片。"
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
            },
            actions = {
                val rev = AuthManager.revision.collectAsState().value
                val ok = remember(rev) { AuthManager.isLoggedIn }
                val days = remember(rev) { AuthManager.refreshLeftDays() }
                Text(
                    text = if (ok) {
                        val who = AuthManager.nickname.ifEmpty { "已登录" }
                        if (days >= 0) "$who · 剩 ${days.toInt()} 天" else who
                    } else "未登录 · 点设置登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 14.dp)
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("normal" to R.string.mode_normal, "comic" to R.string.mode_comic, "clean" to R.string.mode_clean).forEach { (key, label) ->
                        OutlinedButton(
                            onClick = { mode = key },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }

            if (mode == "normal") {
                item {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text(stringResource(R.string.paint_hint)) },
                        supportingText = {
                            Text(stringResource(R.string.paint_counter, prompt.length), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            if (mode == "comic") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.comic_count), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                comicCount = (comicCount - 1).coerceAtLeast(1)
                                cellTexts = cellTexts.take(comicCount)
                            }) { Text("−") }
                            Text("$comicCount", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                comicCount = (comicCount + 1).coerceAtMost(9)
                                cellTexts = cellTexts + List(comicCount - cellTexts.size) { "" }
                            }) { Text("+") }
                        }
                        OutlinedTextField(value = comicBg, onValueChange = { comicBg = it }, label = { Text(stringResource(R.string.comic_bg)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(value = comicStyle, onValueChange = { comicStyle = it }, label = { Text(stringResource(R.string.comic_style)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Text(stringResource(R.string.comic_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(comicCount) { i ->
                    OutlinedTextField(
                        value = cellTexts.getOrNull(i).orEmpty(),
                        onValueChange = { v -> cellTexts = cellTexts.toMutableList().also { if (i < it.size) it[i] = v else it.add(v) } },
                        label = { Text(stringResource(R.string.comic_cell, i + 1)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            if (mode == "clean") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.wm_position), style = MaterialTheme.typography.labelLarge)
                        if (wmCustomOn) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = wmCustom, onValueChange = { wmCustom = it }, label = { Text(stringResource(R.string.wm_custom)) }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
                                IconButton(onClick = { wmCustomOn = false }) { Icon(Icons.Outlined.Brush, stringResource(R.string.wm_custom_tip)) }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("左上角", "右上角", "左下角", "右下角", "中央", "四周").forEach { p ->
                                    FilterChip(selected = wmPos == p, onClick = { wmPos = p }, label = { Text(p) })
                                }
                            }
                            TextButton(onClick = { wmCustomOn = true }) { Text(stringResource(R.string.wm_custom)) }
                        }
                        OutlinedTextField(value = wmText, onValueChange = { wmText = it }, label = { Text(stringResource(R.string.wm_content)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Text(stringResource(R.string.wm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.paint_pick_refs, sources.size)) }
                }
            }
            if (sources.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sources.forEachIndexed { i, f ->
                                Box {
                                    AsyncImage(model = f, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                    IconButton(onClick = { f.delete(); sources = sources.filterIndexed { k, _ -> k != i } }, modifier = Modifier.size(24.dp).align(Alignment.TopEnd)) {
                                        Icon(Icons.Outlined.Warning, stringResource(R.string.paint_clear_image), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                }
            }

            item {
                Button(
                    onClick = {
                        if (mode == "normal" && prompt.isBlank() || mode == "clean" && wmText.isBlank()) {
                            toast = context.getString(R.string.err_prompt_empty)
                            return@Button
                        }
                        if (mode == "clean" && sources.isEmpty()) {
                            toast = context.getString(R.string.err_clean_image)
                            return@Button
                        }
                        if (!AuthManager.isLoggedIn) {
                            toast = context.getString(R.string.err_need_login)
                            onOpenSettings()
                            return@Button
                        }
                        if (queueFull) {
                            toast = context.getString(R.string.queue_full)
                            return@Button
                        }
                        scope.launch {
                            val bytesList = withContext(Dispatchers.IO) {
                                sources.filter { it.exists() }.mapNotNull { runCatching { it.readBytes() }.getOrNull() }
                            }
                            if (mode == "comic") {
                                val group = "comic-${System.currentTimeMillis()}"
                                var submitted = 0
                                for (i in 0 until comicCount) {
                                    val id = engine.submit(comicPromptText(i), refs = bytesList, mode = "comic", comicGroup = group, comicIndex = i, comicTotal = comicCount)
                                    if (id == null) break
                                    submitted++
                                }
                                if (submitted == 0) toast = context.getString(R.string.queue_full)
                                else { cellTexts = List(comicCount) { "" }; sources.forEach { it.delete() }; sources = emptyList() }
                            } else {
                                val id = engine.submit(if (mode == "clean") cleanPromptText() else prompt.trim(), refs = bytesList, mode = mode)
                                if (id == null) toast = context.getString(R.string.queue_full)
                                else { prompt = ""; wmText = ""; sources.forEach { it.delete() }; sources = emptyList() }
                            }
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(if (mode == "comic") R.string.comic_start else R.string.paint_submit)) }
            }

            if (toast != null) {
                item {
                    Text(
                        toast.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            items(active, key = { it.id }) { t ->
                ActiveTaskCard(task = t, now = now, onCancel = { engine.cancel(t.id) })
            }

            if (finished.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.paint_recent),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(finished, key = { it.id }) { t ->
                    ResultCard(task = t, onOpen = { onOpenDetail(t) }, onSave = {
                        val saved = saveFirst(context, t)
                        toast = context.getString(if (saved) R.string.save_success else R.string.save_failed)
                    })
                }
            }

            if (failed.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.paint_failed_section),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(failed, key = { "f${it.id}" }) { t ->
                    FailedCard(
                        task = t,
                        onRetry = { engine.retry(t) },
                        onDelete = { scope.launch { engine.deleteLocal(t.id) } }
                    )
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Brush, null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.paint_empty_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.paint_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ActiveTaskCard(task: PainterTask, now: Long, onCancel: () -> Unit) {
    val status = runCatching { TaskStatus.valueOf(task.status) }.getOrDefault(TaskStatus.QUEUED)
    val label = when (status) {
        TaskStatus.QUEUED -> stringResource(R.string.state_queued)
        TaskStatus.POSTING -> stringResource(R.string.state_posting)
        TaskStatus.GENERATING -> stringResource(R.string.state_generating)
        TaskStatus.DOWNLOADING -> stringResource(R.string.state_downloading)
        TaskStatus.CLEANING -> stringResource(R.string.state_cleaning)
        TaskStatus.DONE -> stringResource(R.string.state_done)
        TaskStatus.FAILED -> stringResource(R.string.state_failed)
        TaskStatus.CANCELLED -> stringResource(R.string.state_cancelled)
    }
    val waited = ((now - task.createdAt) / 1000).toInt().coerceAtLeast(0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.state_wait, waited),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                task.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
private fun FailedCard(task: PainterTask, onRetry: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.state_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                task.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                task.errorMsg?.takeIf { it.isNotBlank() } ?: stringResource(R.string.state_failed),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun ResultCard(task: PainterTask, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column {
            val path = firstLocalPath(task)
            if (path != null) {
                AsyncImage(
                    model = File(path),
                    contentDescription = task.description,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(0.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(14.dp)) {
                if (!task.title.isNullOrEmpty()) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    stringResource(R.string.history_prompt) + task.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

fun firstLocalPath(task: PainterTask): String? = task.stitchedPath?.takeIf { File(it).exists() } ?: runCatching {
    val arr = JSONArray(task.localPaths ?: "[]")
    if (arr.length() > 0) arr.getString(0) else null
}.getOrNull()

fun saveFirst(context: android.content.Context, task: PainterTask): Boolean {
    val p = firstLocalPath(task) ?: return false
    val f = File(p)
    if (!f.exists()) return false
    return ImageSaver.saveToGallery(context, f, "kimpainter_${task.id}")
}
