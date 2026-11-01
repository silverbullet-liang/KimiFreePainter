package com.kimifreepainter.data.api

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/**
 * 扫码登录网关。基址是 auth.kimi.com，不是业务接口的 www.kimi.com/apiv2
 * ——后者对 AuthService 全部 404。两个方法都免 Authorization。
 *
 * 二维码内容与官网一致：https://www.kimi.com/wechat/mp/auth?id=<code>&device_id=<webId>
 */
@SuppressLint("HardwareIds")
object AuthApi {

    private val JSON = "application/json".toMediaType()
    private var webId: String = ""

    fun init(context: Context) {
        val sp = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        webId = sp.getString("qr_wid", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            sp.edit().putString("qr_wid", it).apply()
        }
    }

    suspend fun createQRCode(): String = withContext(Dispatchers.IO) {
        val d = post("/account.gateway.v1.AuthService/CreateLoginQRCode", JSONObject())
        d.optString("code")
    }

    data class State(
        val status: String,
        val accessToken: String,
        val refreshToken: String,
        val userId: String
    )

    suspend fun qrStatus(code: String): State = withContext(Dispatchers.IO) {
        val d = post("/account.gateway.v1.AuthService/GetLoginQRCodeStatus", JSONObject().put("code", code))
        State(
            status = d.optString("status"),
            accessToken = pick(d, listOf("accessToken", "access_token", "token")),
            refreshToken = pick(d, listOf("refreshToken", "refresh_token")),
            userId = d.optString("userId")
        )
    }

    fun qrContent(code: String): String =
        "https://www.kimi.com/wechat/mp/auth?id=${enc(code)}&device_id=${enc(webId)}"

    private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")

    private fun pick(d: JSONObject, keys: List<String>): String {
        for (k in keys) d.optString(k).takeIf { it.isNotEmpty() }?.let { return it }
        val names = d.keys()
        while (names.hasNext()) {
            val v = d.optJSONObject(names.next()) ?: continue
            for (k in keys) v.optString(k).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(ApiClient.AUTH_BASE + path)
            .post(body.toString().toRequestBody(JSON))
            .header("content-type", "application/json")
            .header("accept", "application/json")
            .header("x-msh-platform", "android")
            .header("x-msh-version", "3.1.0")
            .header("x-language", "zh-CN")
            .header("r-timezone", "Asia/Shanghai")
            .build()
        ApiClient.http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw ApiException(resp.code, msg.ifEmpty { "HTTP ${resp.code}" })
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}
