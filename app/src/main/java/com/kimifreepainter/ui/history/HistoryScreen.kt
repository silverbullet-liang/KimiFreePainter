package com.kimifreepainter.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kimifreepainter.R
import com.kimifreepainter.data.db.PainterTask
import com.kimifreepainter.data.repo.PaintEngine
import com.kimifreepainter.data.repo.TaskStatus
import com.kimifreepainter.ui.paint.firstLocalPath
import com.kimifreepainter.ui.paint.saveFirst
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(tasks: List<PainterTask>, onOpen: (PainterTask) -> Unit) {
    var pendingDelete by remember { mutableStateOf<PainterTask?>(null) }
    val engine = com.kimifreepainter.App.engine
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_history)) })

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.History, null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.history_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpen(t) },
                            onLongClick = { pendingDelete = t }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val p = firstLocalPath(t)
                    if (p != null && File(p).exists()) {
                        AsyncImage(
                            model = File(p),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        runCatching { TaskStatus.valueOf(t.status).name.take(1) }.getOrDefault("?"),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            fmt(t.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        statusLabel(t.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (t.status == TaskStatus.DONE.name) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_confirm)) },
            text = { Text(stringResource(R.string.history_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { engine.deleteLocal(t.id) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(task: PainterTask, engine: PaintEngine, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    val path = firstLocalPath(task)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_detail)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                        }
                    }
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                ) {
                    if (path != null && File(path).exists()) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = task.description,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.history_prompt), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(task.prompt, style = MaterialTheme.typography.bodyMedium)
                    if (!task.description.isNullOrEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.history_caption), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(task.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!task.errorMsg.isNullOrEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(task.errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            msg = context.getString(
                                if (saveFirst(context, task)) R.string.save_success else R.string.save_failed
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_save)) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { scope.launch { engine.redelete(task); msg = true.toString() } },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_redelete)) }
                        OutlinedButton(
                            onClick = {
                                engine.retry(task)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                    TextButton(
                        onClick = {
                            scope.launch { engine.deleteLocal(task.id); onDismiss() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    TaskStatus.QUEUED.name -> stringResource(R.string.state_queued)
    TaskStatus.POSTING.name -> stringResource(R.string.state_posting)
    TaskStatus.GENERATING.name -> stringResource(R.string.state_generating)
    TaskStatus.DOWNLOADING.name -> stringResource(R.string.state_downloading)
    TaskStatus.CLEANING.name -> stringResource(R.string.state_cleaning)
    TaskStatus.DONE.name -> stringResource(R.string.state_done)
    TaskStatus.CANCELLED.name -> stringResource(R.string.state_cancelled)
    else -> stringResource(R.string.state_failed)
}

private fun fmt(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
