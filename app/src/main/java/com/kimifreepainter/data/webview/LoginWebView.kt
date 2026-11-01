package com.kimifreepainter.data.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
 * 不轮询登录接口，只读同源 localStorage 里的令牌。
 */
@SuppressLint("SetJavaScriptEnabled")
class LoginWebView(private val onResult: (ok: Boolean, message: String) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var polling = false
    private var lastError = ""

    fun attach(wv: WebView) {
        web = wv
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 关键：默认 UA 带 "; wv" 标记，站点会当成 WebView 走降级/拦截分支。
            // 也不要再追加自定义后缀——未知 UA 会被风控直接回空白页。
            userAgentString = WebSettings.getDefaultUserAgent(wv.context).replace("; wv", "")
        }
        wv.setBackgroundColor(0xFFFFFFFF.toInt())

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return !isHttp(url) && openExternal(view, url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val u = url.orEmpty()
                return !isHttp(u) && openExternal(view, u)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                extract()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    lastError = "页面加载失败，网络错误码 ${error?.errorCode ?: -1}"
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                lastError = "证书校验失败"
                handler?.cancel()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean =
                true // 交给我们重建，避免整个应用崩溃
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress >= 90) extract()
            }
        }

        if (wv.url.isNullOrEmpty()) wv.loadUrl(HOME)
        startPolling()
    }

    /** 页面里点「已登录，保存」时调用。 */
    fun extract() {
        val wv = web ?: run {
            onResult(false, lastError.ifEmpty { "登录页尚未就绪" })
            return
        }
        wv.evaluateJavascript(SCAN_JS) { raw ->
            val json = unwrap(raw)
            if (json == null) {
                onResult(false, lastError.ifEmpty { "尚未检测到登录态，请先在页面内完成登录" })
                return@evaluateJavascript
            }
            val a = json.optString("a")
            val r = json.optString("r")
            if (a.isNotEmpty() || r.isNotEmpty()) {
                AuthManager.saveSession(a, r, AuthManager.userId, AuthManager.nickname)
                CookieManager.getInstance().flush()
                fillProfile()
                onResult(true, "已保存登录态")
            } else {
                onResult(false, lastError.ifEmpty { "尚未检测到登录态，请先在页面内完成登录" })
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

    private fun isHttp(url: String): Boolean =
        url.isEmpty() || url.startsWith("http://") || url.startsWith("https://")

    /** 微信/支付宝等第三方登录会跳 intent:// 或自定义 scheme，WebView 自己打不开，交给系统。 */
    private fun openExternal(view: WebView?, url: String): Boolean {
        if (url.isEmpty()) return false
        return runCatching {
            view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrDefault(false)
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(object : Runnable {
            override fun run() {
                if (!polling) return
                val wv = web ?: return
                wv.evaluateJavascript(HAS_TOKEN_JS) { n ->
                    if ((n?.toIntOrNull() ?: 0) > 20 && !AuthManager.hasAccess) extract()
                }
                handler.postDelayed(this, 2500)
            }
        })
    }

    fun detach() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        web = null
    }

    /** 供 Compose 判断当前是否可用。 */
    fun hasToken(): Boolean = AuthManager.hasAccess || AuthManager.hasRefresh

    companion object {
        private const val HOME = "https://www.kimi.com/"

        /**
         * 不写死 key 名：官网改过字段就再也读不到。
         * 先按常见 key 精确匹配，再退到「任意一个形如 JWT 的 localStorage 值」。
         */
        private const val SCAN_JS = """
(function(){
  try {
    var cands = [];
    for (var i = 0; i < localStorage.length; i++) {
      var k = localStorage.key(i) || '';
      var v = localStorage.getItem(k) || '';
      if (v.length > 40 && v.indexOf('eyJ') === 0 && v.split('.').length === 3) cands.push({k: k.toLowerCase(), v: v});
    }
    var byName = function(n) {
      for (var i = 0; i < cands.length; i++) if (cands[i].k === n) return cands[i].v;
      return '';
    };
    var byPart = function(p) {
      for (var i = 0; i < cands.length; i++) if (cands[i].k.indexOf(p) >= 0) return cands[i].v;
      return '';
    };
    var a = byName('access_token') || byName('accesstoken') || byPart('access');
    var r = byName('refresh_token') || byName('refreshtoken') || byPart('refresh');
    if (!a && cands.length) a = cands[0].v;
    if (!r && cands.length > 1) r = cands[1].v;
    return JSON.stringify({a: a, r: r});
  } catch (e) { return ''; }
})()
"""

        private val HAS_TOKEN_JS =
            "(function(){try{var n=0;for(var i=0;i<localStorage.length;i++){var v=localStorage.getItem(localStorage.key(i))||'';if(v.length>40&&v.indexOf('eyJ')===0)n=v.length;}return n}catch(e){return 0}})()"

        /** 提前预热，降低首次打开登录页的白屏时间。 */
        fun warmUp() {
            ApiClient.tokens = AuthManager
        }
    }
}
