package com.kimifreepainter.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import com.kimifreepainter.data.api.TokenStore
import org.json.JSONObject

/**
 * 双令牌存储。优先 EncryptedSharedPreferences；设备不支持时降级普通 SP（不阻断使用）。
 * 注意：access 与 refresh 分开存，面板上分别展示状态。
 */
object AuthManager : TokenStore {

    private const val FILE = "kimpainter_auth"
    private lateinit var sp: SharedPreferences

    /** 登录态版本号：任何写入都 +1，UI 订阅它即可实时反映。 */
    val revision = MutableStateFlow(0L)
    private fun touch() {
        revision.value = revision.value + 1
    }

    fun init(context: Context) {
        sp = try {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    override fun accessToken(): String? = sp.getString("access", null)?.takeIf { it.isNotEmpty() }
    override fun refreshToken(): String? = sp.getString("refresh", null)?.takeIf { it.isNotEmpty() }

    override fun onTokens(access: String, refresh: String) {
        sp.edit().putString("access", access).putString("refresh", refresh).apply()
        touch()
    }

    override fun onSessionLost() {
        sp.edit().remove("access").remove("refresh").apply()
        touch()
    }

    fun saveSession(access: String, refresh: String, userId: String, name: String) {
        sp.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putString("userId", userId)
            .putString("name", name)
            .apply()
        touch()
    }

    fun clear() {
        sp.edit().clear().apply()
        touch()
    }

    val isLoggedIn: Boolean get() = !accessToken().isNullOrEmpty() || !refreshToken().isNullOrEmpty()
    val hasAccess: Boolean get() = !accessToken().isNullOrEmpty()
    val hasRefresh: Boolean get() = !refreshToken().isNullOrEmpty()

    val userId: String get() = sp.getString("userId", "").orEmpty()
    var nickname: String
        get() = sp.getString("name", "").orEmpty()
        set(v) {
            sp.edit().putString("name", v).apply()
            touch()
        }

    /** access 剩余秒数（JWT exp - now），解析失败返回 -1。 */
    fun accessLeftSeconds(): Long {
        val t = accessToken() ?: return -1
        val exp = jwtExp(t) ?: return -1
        return exp - System.currentTimeMillis() / 1000
    }

    /** refresh 剩余天数，解析失败返回 -1。 */
    fun refreshLeftDays(): Long {
        val t = refreshToken() ?: return -1
        val exp = jwtExp(t) ?: return -1
        val left = exp - System.currentTimeMillis() / 1000
        return if (left <= 0) 0 else left / 86400
    }

    private fun jwtExp(token: String): Long? {
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
            val o = JSONObject(json)
            if (o.has("exp")) o.getLong("exp") else null
        } catch (e: Exception) {
            null
        }
    }
}
