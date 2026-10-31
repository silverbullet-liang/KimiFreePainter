package com.kimifreepainter.data.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kimifreepainter.data.api.ApiClient
import com.kimifreepainter.data.api.KimiApi
import com.kimifreepainter.data.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 后台登录 WebView：用户在可见页面里完成一次登录，之后由这里持续读取登录态。
 * 不轮询登录接口，只读同源 localStorage 的两个令牌。
 */
@SuppressLint("SetJavaScriptEnabled")
class LoginWebView(private val onResult: (ok: Boolean, message: String) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var polling = false

    fun attach(wv: WebView) {
        web = wv
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = "$userAgentString KimiFreePainter/1.0"
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                extract()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean = true // 交给我们重建，避免整个应用崩溃
        }
        if (web == null || wv.url.isNullOrEmpty()) wv.loadUrl("https://www.kimi.com/")
        startPolling()
    }

    /** 页面里点「已登录，保存」时调用。 */
    fun extract() {
        val wv = web ?: return
        wv.evaluateJavascript(
            "(function(){try{return JSON.stringify({" +
                "a:localStorage.getItem('access_token')||''," +
                "r:localStorage.getItem('refresh_token')||''});}catch(e){return ''}})()"
        ) { raw ->
            val json = unwrap(raw) ?: return@evaluateJavascript
            val a = json.optString("a")
            val r = json.optString("r")
            if (a.isNotEmpty() || r.isNotEmpty()) {
                AuthManager.saveSession(a, r, AuthManager.userId, AuthManager.nickname)
                fillProfile()
                onResult(true, "已保存登录态")
            } else {
                onResult(false, "尚未检测到登录态，请先在页面内完成登录")
            }
        }
    }

    private fun fillProfile() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val me = KimiApi.me()
                val base = me.optJSONObject("userBase")
                val id = base?.optString("userId").orEmpty()
                val name = base?.optString("name").orEmpty()
                if (id.isNotEmpty()) AuthManager.saveSession(
                    AuthManager.accessToken().orEmpty(),
                    AuthManager.refreshToken().orEmpty(),
                    id,
                    name
                )
            }
        }
    }

    private fun unwrap(raw: String?): JSONObject? {
        if (raw.isNullOrEmpty() || raw == "null") return null
        return runCatching {
            val v = JSONTokener(raw).nextValue()
            val s = if (v is String) v else return null
            if (s.isEmpty()) null else JSONObject(s)
        }.getOrNull()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(object : Runnable {
            override fun run() {
                if (!polling) return
                val wv = web ?: return
                wv.evaluateJavascript(
                    "(function(){try{var a=localStorage.getItem('access_token');return a?a.length:0}catch(e){return 0}})()"
                ) { n ->
                    if ((n?.toIntOrNull() ?: 0) > 20 && !AuthManager.hasAccess) extract()
                }
                handler.postDelayed(this, 2500)
            }
        })
    }

    fun detach() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        web = null
    }

    /** 供 Compose 判断当前是否可用。 */
    fun hasToken(): Boolean = AuthManager.hasAccess || AuthManager.hasRefresh

    companion object {
        /** 提前预热，降低首次打开登录页的白屏时间。 */
        fun warmUp() {
            ApiClient.tokens = AuthManager
        }
    }
}
