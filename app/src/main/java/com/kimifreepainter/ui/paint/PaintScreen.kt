package com.kimifreepainter.ui.paint

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    val active = tasks.filter { TaskStatus.valueOf(it.status).ordinal < TaskStatus.DONE.ordinal }
    val finished = tasks.filter { it.status == TaskStatus.DONE.name }.take(6)
    val queueFull = active.size >= 10
    var sourceFile by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ext = context.contentResolver.getType(uri)?.substringAfter('/', "jpg") ?: "jpg"
        val f = File(context.cacheDir, "input_${System.currentTimeMillis()}.$ext")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { input.copyTo(it) }
            } != null
        }.getOrDefault(false)
        if (ok && f.length() > 0) {
            sourceFile?.delete()
            sourceFile = f
        } else {
            f.delete()
            toast = context.getString(R.string.paint_image_failed)
        }
    }
    val canSubmit = prompt.isNotBlank() && !queueFull && AuthManager.isLoggedIn

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text(stringResource(R.string.paint_hint)) },
                    supportingText = {
                        Text(
                            stringResource(R.string.paint_counter, prompt.length),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(stringResource(R.string.paint_pick_image)) }
                    val f = sourceFile
                    if (f != null && f.exists()) {
                        AsyncImage(
                            model = f,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        TextButton(onClick = { f.delete(); sourceFile = null }) {
                            Text(stringResource(R.string.paint_clear_image))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val text = prompt.trim()
                        if (text.isEmpty()) {
                            toast = context.getString(R.string.err_prompt_empty)
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
                        val bytes = sourceFile?.takeIf { it.exists() }?.readBytes()
                        scope.launch {
                            val id = engine.submit(text, bytes)
                            if (id == null) {
                                toast = context.getString(R.string.queue_full)
                            } else {
                                prompt = ""
                                sourceFile?.delete()
                                sourceFile = null
                            }
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(stringResource(R.string.paint_submit))
                }
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
        else -> stringResource(R.string.state_done)
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
            if (status == TaskStatus.QUEUED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
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

fun firstLocalPath(task: PainterTask): String? = runCatching {
    val arr = JSONArray(task.localPaths ?: "[]")
    if (arr.length() > 0) arr.getString(0) else null
}.getOrNull()

fun saveFirst(context: android.content.Context, task: PainterTask): Boolean {
    val p = firstLocalPath(task) ?: return false
    val f = File(p)
    if (!f.exists()) return false
    return ImageSaver.saveToGallery(context, f, "kimpainter_${task.id}")
}
