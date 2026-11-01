package com.kimifreepainter.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kimifreepainter.R
import com.kimifreepainter.data.auth.AuthManager
import com.kimifreepainter.data.settings.CleanMode
import com.kimifreepainter.data.settings.Settings
import com.kimifreepainter.data.settings.SettingsRepository
import com.kimifreepainter.data.update.Updater
import android.content.Context
import java.io.File
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    repo: SettingsRepository,
    onLogin: () -> Unit,
    onClearHistory: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    // 订阅登录态版本号：扫码成功后立刻反映，不用手动刷新页面
    val rev = AuthManager.revision.collectAsState().value
    val loggedIn = remember(rev) { AuthManager.isLoggedIn }
    val accessOk = remember(rev) { AuthManager.hasAccess }
    val refreshDays = remember(rev) { AuthManager.refreshLeftDays() }

    var showClean by remember { mutableStateOf(false) }
    var showDelay by remember { mutableStateOf(false) }
    var showPoll by remember { mutableStateOf(false) }
    var showTimeout by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val appVersion = remember {
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            "${pi.versionName} (${code})"
        }.getOrNull() ?: "?"
    }
    var updateText by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) })

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            Section(stringResource(R.string.settings_account))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (loggedIn) (AuthManager.nickname.ifEmpty { "已登录" }) else stringResource(R.string.settings_sign_in),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (accessOk) stringResource(R.string.settings_token_valid)
                        else stringResource(R.string.settings_token_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (refreshDays >= 0) stringResource(R.string.settings_refresh_days, refreshDays.toInt())
                        else stringResource(R.string.settings_refresh_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (loggedIn) {
                    OutlinedButton(onClick = {
                        AuthManager.clear()
                        tick++
                        onLogin()
                    }) { Text(stringResource(R.string.settings_sign_in)) }
                } else {
                    OutlinedButton(onClick = onLogin) { Text(stringResource(R.string.settings_sign_in)) }
                }
            }
            if (loggedIn) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    TextButton(onClick = {
                        AuthManager.clear()
                        tick++
                    }) { Text(stringResource(R.string.settings_sign_out)) }
                }
            }

            Divider()
            Section(stringResource(R.string.settings_create))

            ItemRow(
                stringResource(R.string.settings_clean_mode),
                when (settings.cleanMode) {
                    CleanMode.NONE -> stringResource(R.string.settings_clean_none)
                    CleanMode.TRIGGER_ONLY -> stringResource(R.string.settings_clean_trigger)
                    CleanMode.ALL -> stringResource(R.string.settings_clean_all)
                }
            ) { showClean = true }

            ItemRow(stringResource(R.string.settings_clean_delay), "${settings.cleanDelaySec} 秒") { showDelay = true }
            ItemRow(stringResource(R.string.settings_poll), "${settings.pollIntervalSec} 秒") { showPoll = true }
            ItemRow(stringResource(R.string.settings_timeout), "${settings.timeoutSec} 秒") { showTimeout = true }
            ItemRow(
                stringResource(R.string.settings_bot),
                settings.botName.ifEmpty { stringResource(R.string.settings_bot_unknown) }
            ) { }

            Divider()
            Section(stringResource(R.string.settings_history))

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_auto_clean), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.autoCleanHistory,
                    onCheckedChange = { scope.launch { repo.setAutoClean(it) } }
                )
            }
            ItemRow(stringResource(R.string.settings_clear_history), "") { showClear = true }

            Divider()
            Section(stringResource(R.string.settings_about))
            ItemRow(
                title = stringResource(R.string.settings_version),
                value = if (updating) updateText else appVersion,
                onClick = {},
                onDoubleClick = {
                    if (updating) return@ItemRow
                    updating = true
                    updateText = ctx.getString(R.string.update_checking)
                    scope.launch {
                        val info = Updater.latest()
                        if (info == null) {
                            updateText = ctx.getString(R.string.update_failed)
                            delay(2500)
                            updateText = ""
                            updating = false
                            return@launch
                        }
                        val cur = runCatching {
                            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt()
                            else @Suppress("DEPRECATION") pi.versionCode
                        }.getOrDefault(0)
                        if (info.versionCode <= cur) {
                            updateText = ctx.getString(R.string.update_latest)
                            delay(2500)
                            updateText = ""
                            updating = false
                            return@launch
                        }
                        updateText = ctx.getString(R.string.update_downloading, 0)
                        val main = android.os.Handler(android.os.Looper.getMainLooper())
                        val apk = Updater.download(info.apkUrl, File(ctx.cacheDir, "update.apk")) { p ->
                            main.post { updateText = ctx.getString(R.string.update_downloading, p) }
                        }
                        if (apk == null) {
                            updateText = ctx.getString(R.string.update_download_failed)
                            delay(3000)
                            updateText = ""
                            updating = false
                            return@launch
                        }
                        updateText = ctx.getString(R.string.update_installing)
                        if (!Updater.install(ctx, apk)) {
                            updateText = ctx.getString(R.string.update_install_failed)
                            delay(3000)
                            updateText = ""
                        }
                        updating = false
                    }
                }
            )
            ItemRow(stringResource(R.string.settings_license), "GPL-3.0") { showAbout = "license" }
            ItemRow(stringResource(R.string.settings_privacy), "") { showAbout = "privacy" }
            ItemRow(stringResource(R.string.settings_disclaimer), "") { showAbout = "disclaimer" }

            Spacer(Modifier.height(24.dp))
            Text(
                "本应用不含任何第三方分析、广告或追踪组件。所有数据仅保存在本机。",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showClean) {
        ChoiceDialog(
            title = stringResource(R.string.settings_clean_mode),
            options = listOf(
                stringResource(R.string.settings_clean_none) to CleanMode.NONE,
                stringResource(R.string.settings_clean_trigger) to CleanMode.TRIGGER_ONLY,
                stringResource(R.string.settings_clean_all) to CleanMode.ALL
            ),
            onPick = { scope.launch { repo.setCleanMode(it) } },
            onDismiss = { showClean = false }
        )
    }

    if (showDelay) SliderDialog(
        title = stringResource(R.string.settings_clean_delay),
        value = settings.cleanDelaySec.toFloat(), range = 0f..120f, suffix = " 秒",
        onDone = { scope.launch { repo.setCleanDelay(it) } },
        onDismiss = { showDelay = false }
    )
    if (showPoll) SliderDialog(
        title = stringResource(R.string.settings_poll),
        value = settings.pollIntervalSec.toFloat(), range = 8f..30f, suffix = " 秒",
        onDone = { scope.launch { repo.setPoll(it) } },
        onDismiss = { showPoll = false }
    )
    if (showTimeout) SliderDialog(
        title = stringResource(R.string.settings_timeout),
        value = settings.timeoutSec.toFloat(), range = 120f..600f, suffix = " 秒",
        onDone = { scope.launch { repo.setTimeout(it) } },
        onDismiss = { showTimeout = false }
    )
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.clear_history_confirm)) },
            text = { Text(stringResource(R.string.history_delete_desc)) },
            confirmButton = {
                TextButton(onClick = { onClearHistory(); showClear = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
    showAbout?.let { kind ->
        val text = when (kind) {
            "license" -> "本应用以 GPL-3.0 协议开源。你可以自由使用、修改与分发，但衍生作品需以相同协议开源。"
            "privacy" -> "本应用不收集、不上传、不存储任何个人数据。登录态仅保存在本机加密存储中，图片与历史仅保存在本机。"
            else -> "本应用使用平台的非公开接口，非官方产品，与平台方无关联。接口可能随时变更导致功能失效，由此产生的风险由使用者自行承担。"
        }
        AlertDialog(
            onDismissRequest = { showAbout = null },
            confirmButton = { TextButton(onClick = { showAbout = null }) { Text(stringResource(R.string.action_close)) } },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium) }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 18.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Divider() = HorizontalDivider(
    modifier = Modifier.padding(top = 10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    title: String,
    value: String,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onDoubleClick = onDoubleClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value.isNotEmpty()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, CleanMode>>,
    onPick: (CleanMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, mode) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(mode); onDismiss() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun SliderDialog(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var v by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("${v.roundToInt()}$suffix", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Slider(value = v, onValueChange = { v = it }, valueRange = range)
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(v.roundToInt()); onDismiss() }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
