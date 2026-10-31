package com.kimifreepainter.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 服务端返回 code != 0 时抛出，code 语义见文档 26.8。 */
class ApiException(val code: Int, val serverMessage: String) : Exception("code=$code $serverMessage")

/** 由 AuthManager 实现，ApiClient 只依赖这个接口，避免循环依赖。 */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun onTokens(access: String, refresh: String)
    fun onSessionLost()
}

@SuppressLint("HardwareIds")
object ApiClient {

    const val BASE = "https://www.kimi.com/apiv2"
    const val AUTH_BASE = "https://auth.kimi.com/api"

    private val JSON = "application/json".toMediaType()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile var tokens: TokenStore? = null
    private val refreshMutex = Mutex()

    private lateinit var deviceId: String
    private lateinit var sessionId: String

    fun init(context: Context) {
        val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        deviceId = sp.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("device_id", it).apply()
        }
        sessionId = UUID.randomUUID().toString()
    }

    private fun headers(b: Request.Builder, auth: Boolean) {
        b.header("R-Timezone", "Asia/Shanghai")
        b.header("x-msh-platform", "android")
        b.header("x-msh-os-version", Build.VERSION.SDK_INT.toString())
        b.header("x-msh-device-model", Build.MODEL ?: "unknown")
        b.header("x-msh-device-id", deviceId)
        b.header("x-msh-session-id", sessionId)
        b.header("x-msh-version", "3.1.0")
        b.header("x-msh-app-channel", "official")
        b.header("x-msh-android-version", Build.VERSION.RELEASE ?: "unknown")
        b.header("X-Language", "zh-CN")
        b.header("Accept", "application/json")
        if (auth) tokens?.accessToken()?.let { b.header("Authorization", "Bearer $it") }
    }

    /** Unary RPC。401/403 时刷新一次并重放；刷新失败抛 401。 */
    suspend fun post(path: String, body: JSONObject, auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val first = execute("$BASE$path", body, auth)
            val code = first.optInt("code", 0)
            if (code == 401 || code == 403) {
                if (refresh()) execute("$BASE$path", body, auth) else throw ApiException(401, "session lost")
            } else first
        }

    /** 刷新令牌，双令牌轮换。并发调用只会真正刷新一次。 */
    private suspend fun refresh(): Boolean = refreshMutex.withLock {
        val rt = tokens?.refreshToken() ?: return@withLock false
        try {
            val body = JSONObject().put("refresh_token", rt)
            val req = Request.Builder()
                .url("$AUTH_BASE/account.gateway.v1.AuthService/RefreshToken")
                .post(body.toString().toRequestBody(JSON))
                .header("content-type", "application/json")
                .header("x-msh-device-id", deviceId)
                .header("x-msh-platform", "android")
                .header("x-msh-session-id", sessionId)
                .header("x-msh-version", "3.1.0")
                .header("r-timezone", "Asia/Shanghai")
                .header("connect-protocol-version", "1")
                .build()
            val resp = http.newCall(req).execute()
            resp.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) return@withLock false
                val json = JSONObject(text)
                val at = json.optString("accessToken")
                val newRt = json.optString("refreshToken")
                if (at.isNullOrEmpty()) return@withLock false
                tokens?.onTokens(at, newRt.ifEmpty { rt })
                true
            }
        } catch (e: Exception) {
            tokens?.onSessionLost()
            false
        }
    }

    private fun execute(url: String, body: JSONObject, auth: Boolean): JSONObject {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
        headers(b, auth)
        http.newCall(b.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) {
                if (resp.isSuccessful) return JSONObject()
                throw ApiException(resp.code, "empty body")
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException(-1, "unexpected response")
            }
            val code = json.optInt("code", 200)
            if (code != 0 && code != 200) throw ApiException(code, json.optString("message"))
            return json
        }
    }
}
