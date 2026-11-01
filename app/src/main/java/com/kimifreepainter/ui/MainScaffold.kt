package com.kimifreepainter.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kimifreepainter.R
import com.kimifreepainter.data.db.PainterTask
import com.kimifreepainter.data.repo.PaintEngine
import com.kimifreepainter.data.settings.Settings
import com.kimifreepainter.data.settings.SettingsRepository
import com.kimifreepainter.ui.history.HistoryScreen
import com.kimifreepainter.ui.login.QrLoginDialog
import com.kimifreepainter.ui.history.TaskDetailDialog
import com.kimifreepainter.ui.paint.PaintScreen
import com.kimifreepainter.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    tab: Int,
    onTab: (Int) -> Unit,
    tasks: List<PainterTask>,
    settings: Settings,
    settingsRepo: SettingsRepository,
    engine: PaintEngine,
    running: Boolean,
    detail: PainterTask?,
    onOpenDetail: (PainterTask) -> Unit,
    onCloseDetail: () -> Unit
) {
    var showLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { onTab(0) },
                    icon = { Icon(Icons.Outlined.Brush, null) },
                    label = { Text(stringResource(R.string.tab_paint)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTab(1) },
                    icon = { Icon(Icons.Outlined.History, null) },
                    label = { Text(stringResource(R.string.tab_history)) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { onTab(2) },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> PaintScreen(
                    tasks = tasks,
                    settings = settings,
                    engine = engine,
                    running = running,
                    onOpenSettings = { onTab(2) },
                    onOpenDetail = onOpenDetail
                )

                1 -> HistoryScreen(
                    tasks = tasks,
                    onOpen = onOpenDetail
                )

                else -> SettingsScreen(
                    settings = settings,
                    repo = settingsRepo,
                    onLogin = { showLogin = true },
                    onClearHistory = { scope.launch { engine.clearHistory() } }
                )
            }
        }
    }

    if (showLogin) {
        QrLoginDialog(onDismiss = { showLogin = false })
    }

    detail?.let {
        TaskDetailDialog(task = it, engine = engine, onDismiss = onCloseDetail)
    }
}
