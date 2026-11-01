package com.kimifreepainter.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kimifreepainter.data.api.AuthApi
import com.kimifreepainter.data.api.KimiApi
import com.kimifreepainter.data.auth.AuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 扫码登录。App 内生成二维码并轮询登录码状态，不再经过 WebView 打开官网，
 * 令牌也就不用从网页里抠——这是普通用户唯一需要走的路。
 */
@Composable
fun QrLoginDialog(onDismiss: () -> Unit) {
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var hint by remember { mutableStateOf("正在获取二维码…") }
    var tone by remember { mutableStateOf("") }
    var nonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(nonce) {
        qr = null
        tone = ""
        hint = "正在获取二维码…"
        val code = runCatching { AuthApi.createQRCode() }.getOrElse {
            hint = "获取二维码失败：${it.message ?: "网络异常"}"
            tone = "bad"
            return@LaunchedEffect
        }
        if (code.isEmpty()) {
            hint = "服务端未返回登录码"
            tone = "bad"
            return@LaunchedEffect
        }
        qr = makeQr(AuthApi.qrContent(code))
        if (qr == null) {
            hint = "二维码生成失败"
            tone = "bad"
            return@LaunchedEffect
        }
        hint = "请用 Kimi App 扫描二维码"

        val deadline = System.currentTimeMillis() + 5 * 60 * 1000
        while (isActive && System.currentTimeMillis() < deadline) {
            delay(1200)
            val st = runCatching { AuthApi.qrStatus(code) }.getOrNull() ?: continue
            when {
                st.status.contains("SUCCESS") -> {
                    if (st.accessToken.isEmpty()) {
                        hint = "登录成功但未取到令牌，请重试"
                        tone = "bad"
                        break
                    }
                    AuthManager.saveSession(st.accessToken, st.refreshToken, st.userId, AuthManager.nickname)
                    val name = runCatching {
                        KimiApi.me().optJSONObject("userBase")?.optString("name").orEmpty()
                    }.getOrNull().orEmpty()
                    if (name.isNotEmpty()) AuthManager.nickname = name
                    hint = "登录成功"
                    tone = "ok"
                    delay(700)
                    onDismiss()
                    break
                }
                st.status.contains("EXPIRED") -> {
                    hint = "二维码已过期，点「刷新二维码」重试"
                    tone = "bad"
                    break
                }
                st.status.contains("SCANNED") -> {
                    hint = "已扫码，请在手机上确认"
                    tone = "wait"
                }
                else -> {
                    hint = "请用 Kimi App 扫描二维码"
                    tone = ""
                }
            }
        }
    }

    val toneColor = when (tone) {
        "ok" -> Color(0xFF18A058)
        "bad" -> MaterialTheme.colorScheme.error
        "wait" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "扫码登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "不需要填任何令牌，扫码即完成授权",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .size(224.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = qr
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "登录二维码",
                            modifier = Modifier.fillMaxSize().padding(10.dp)
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = toneColor,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { nonce++ }) { Text("刷新二维码") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

/** 用 ZXing 编码二维码，逐像素写入 IntArray（比 setPixel 循环快一个量级）。 */
private fun makeQr(content: String, size: Int = 640): ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )
    val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val off = y * size
        for (x in 0 until size) {
            pixels[off + x] = if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    bmp.asImageBitmap()
}.getOrNull()
