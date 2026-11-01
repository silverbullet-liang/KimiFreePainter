package com.kimifreepainter.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kimifreepainter.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 自更新：读 GitHub 最新 Release，比对 versionCode，下载 APK 并拉起系统安装器。
 * 只用 api.github.com（releases/latest 免鉴权），不经过任何第三方。
 */
object Updater {

    private const val API =
        "https://api.github.com/repos/silverbullet-liang/KimiFreePainter/releases/latest"

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

    suspend fun latest(): Info? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KimiFreePainter")
                .build()
            val text = ApiClient.http.newCall(req).execute().use { it.body?.string().orEmpty() }
            val j = JSONObject(text)
            val tag = j.optString("tag_name")
            val code = versionCodeOf(tag)
            val assets = j.optJSONArray("assets") ?: JSONArray()
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url")
                    break
                }
            }
            if (code <= 0 || url.isEmpty()) null
            else Info(code, tag.removePrefix("v"), url, j.optString("body"))
        }.getOrNull()
    }

    suspend fun download(url: String, target: File, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                target.parentFile?.mkdirs()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KimiFreePainter")
                    .build()
                ApiClient.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    val total = body.contentLength()
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
                if (target.length() > 0) target else null
            }.getOrNull()
        }

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
