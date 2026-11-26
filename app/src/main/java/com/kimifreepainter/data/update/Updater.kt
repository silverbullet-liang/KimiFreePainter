package com.kimifreepainter.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kimifreepainter.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 自更新：读 GitHub 最新 Release，比对 versionCode，下载 APK 并拉起系统安装器。
 * 检查与下载都走多候选地址：代理优先，官方直连兜底；任一成功即用。
 * 候选代理于 2026-11-26 实测返回了 release JSON / APK 字节。
 */
object Updater {

    private const val API =
        "https://api.github.com/repos/silverbullet-liang/KimiFreePainter/releases/latest"

    /** 检查更新：gh-proxy.com 与 gh-proxy.org 都能转发 api.github.com；直连最后兜底。 */
    private val apiPrefixes = listOf("https://gh-proxy.com/", "https://gh-proxy.org/", "")

    /** APK 下载：四个代理实测均可返回完整字节，直连最后兜底。 */
    private val downloadPrefixes = listOf(
        "https://gh-proxy.org/",
        "https://gh-proxy.com/",
        "https://ghproxy.vip/",
        "https://ghproxy.net/",
        ""
    )

    /** 下载用短连接超时：一个代理不通时尽快换下一个。 */
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Info(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    /** tag 形如 v1.4.0 → 140，与 build.gradle.kts 的 versionCode 同一套规则。 */
    fun versionCodeOf(v: String): Int {
        val p = v.removePrefix("v").split('.')
        val major = p.getOrNull(0)?.toIntOrNull() ?: return 0
        val minor = p.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = p.getOrNull(2)?.toIntOrNull() ?: 0
        return major * 100 + minor * 10 + patch
    }

    private fun candidates(target: String, prefixes: List<String>): List<String> =
        prefixes.map { it + target }.distinct()

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        for (url in candidates(API, apiPrefixes)) {
            val info = parseInfo(url)
            if (info != null) return@withContext info
        }
        null
    }

    private fun parseInfo(url: String): Info? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "KimiFreePainter")
            .build()
        val text = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val j = JSONObject(text)
        val tag = j.optString("tag_name")
        val code = versionCodeOf(tag)
        val assets = j.optJSONArray("assets") ?: JSONArray()
        var apk = ""
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apk = a.optString("browser_download_url")
                break
            }
        }
        if (code <= 0 || apk.isEmpty()) null
        else Info(code, tag.removePrefix("v"), apk, j.optString("body"))
    }.getOrNull()

    suspend fun download(url: String, target: File, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val shown = intArrayOf(0)
            val report: (Int) -> Unit = { p ->
                if (p > shown[0]) {
                    shown[0] = p
                    onProgress(p)
                }
            }
            for (candidate in candidates(url, downloadPrefixes)) {
                val file = downloadOne(candidate, target, report)
                if (file != null) return@withContext file
            }
            null
        }

    /**
     * 代理可能回 HTML，也可能传一半就断开但状态码仍是 200。
     * 校验 ZIP 头 + 末尾 EOCD 记录，并核对 content-length。
     */
    private fun completeApk(f: File, expected: Long): Boolean {
        if (f.length() < 1024) return false
        if (expected > 0 && f.length() != expected) return false
        val b = f.readBytes()
        if (b[0] != 0x50.toByte() || b[1] != 0x4b.toByte()) return false
        for (i in (b.size - 22).coerceAtLeast(0) until b.size - 3) {
            if (b[i] == 0x50.toByte() && b[i + 1] == 0x4b.toByte() &&
                b[i + 2] == 0x05.toByte() && b[i + 3] == 0x06.toByte()
            ) return true
        }
        return false
    }

    private fun downloadOne(url: String, target: File, onProgress: (Int) -> Unit): File? = runCatching {
        target.parentFile?.mkdirs()
        var total = -1L
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "KimiFreePainter")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            val body = resp.body ?: return@runCatching null
            total = body.contentLength()
            var read = 0L
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt())
                    }
                }
            }
        }
        if (!completeApk(target, total)) {
            target.delete()
            return@runCatching null
        }
        target
    }.getOrNull()

    /** 拉起系统安装器。Android 8+ 需要「安装未知应用」权限，缺失时系统会自己弹提示。 */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
