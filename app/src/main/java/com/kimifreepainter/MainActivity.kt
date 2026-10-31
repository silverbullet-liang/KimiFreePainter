package com.kimifreepainter

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimifreepainter.data.auth.AuthManager
import com.kimifreepainter.data.db.AppDatabase
import com.kimifreepainter.data.db.PainterTask
import com.kimifreepainter.data.settings.SettingsRepository
import com.kimifreepainter.ui.MainScaffold
import com.kimifreepainter.ui.theme.KimiFreePainterTheme

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            KimiFreePainterTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val engine = App.engine
    val db = remember { AppDatabase.get(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val tasks by db.tasks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList<PainterTask>())
    val settings by settingsRepo.flow.collectAsStateWithLifecycle(
        initialValue = com.kimifreepainter.data.settings.Settings()
    )
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<PainterTask?>(null) }
    val running by engine.running.collectAsState()

    LaunchedEffect(Unit) { AuthManager.init(context); com.kimifreepainter.data.api.ApiClient.tokens = AuthManager }

    MainScaffold(
        tab = tab,
        onTab = { tab = it },
        tasks = tasks,
        settings = settings,
        settingsRepo = settingsRepo,
        engine = engine,
        running = running,
        detail = detail,
        onOpenDetail = { detail = it },
        onCloseDetail = { detail = null }
    )
}
