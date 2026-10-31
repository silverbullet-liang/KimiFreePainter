package com.kimifreepainter.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("kimpainter_settings")

enum class CleanMode { NONE, TRIGGER_ONLY, ALL }

data class Settings(
    val cleanMode: CleanMode = CleanMode.TRIGGER_ONLY,
    val cleanDelaySec: Int = 30,
    val pollIntervalSec: Int = 10,
    val timeoutSec: Int = 300,
    val botUserId: String = "",
    val botName: String = "",
    val autoCleanHistory: Boolean = false,
    val draft: String = ""
)

class SettingsRepository(private val context: Context) {

    private object K {
        val cleanMode = stringPreferencesKey("clean_mode")
        val cleanDelay = intPreferencesKey("clean_delay")
        val poll = intPreferencesKey("poll_interval")
        val timeout = intPreferencesKey("timeout")
        val botId = stringPreferencesKey("bot_id")
        val botName = stringPreferencesKey("bot_name")
        val autoClean = booleanPreferencesKey("auto_clean")
        val draft = stringPreferencesKey("draft")
    }

    val flow: Flow<Settings> = context.store.data.map { p ->
        Settings(
            cleanMode = runCatching { CleanMode.valueOf(p[K.cleanMode] ?: "TRIGGER_ONLY") }
                .getOrDefault(CleanMode.TRIGGER_ONLY),
            cleanDelaySec = p[K.cleanDelay] ?: 30,
            pollIntervalSec = p[K.poll] ?: 10,
            timeoutSec = p[K.timeout] ?: 300,
            botUserId = p[K.botId].orEmpty(),
            botName = p[K.botName].orEmpty(),
            autoCleanHistory = p[K.autoClean] ?: false,
            draft = p[K.draft].orEmpty()
        )
    }

    suspend fun setCleanMode(v: CleanMode) = context.store.edit { it[K.cleanMode] = v.name }
    suspend fun setCleanDelay(v: Int) = context.store.edit { it[K.cleanDelay] = v.coerceIn(0, 300) }
    suspend fun setPoll(v: Int) = context.store.edit { it[K.poll] = v.coerceIn(8, 60) }
    suspend fun setTimeout(v: Int) = context.store.edit { it[K.timeout] = v.coerceIn(120, 600) }
    suspend fun setBot(id: String, name: String) = context.store.edit {
        it[K.botId] = id
        it[K.botName] = name
    }
    suspend fun setAutoClean(v: Boolean) = context.store.edit { it[K.autoClean] = v }
    suspend fun setDraft(v: String) = context.store.edit { it[K.draft] = v }
}
