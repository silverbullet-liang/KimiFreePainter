package com.kimifreepainter.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** 文件落盘与相册导出。原图字节直存，绝不二次编码。 */
object ImageSaver {

    private fun ext(bytes: ByteArray): String {
        if (bytes.size >= 12) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "jpg"
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) return "png"
            if (bytes.size > 12 &&
                bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte()
            ) return "webp"
        }
        return "jpg"
    }

    /** 存到内部 history 目录，返回相对路径。 */
    fun saveToHistory(context: Context, taskId: Long, index: Int, bytes: ByteArray): File {
        val dir = File(context.filesDir, "history/$taskId").apply { mkdirs() }
        val f = File(dir, String.format("%02d.%s", index + 1, ext(bytes)))
        f.writeBytes(bytes)
        return f
    }

    /** 存用户选定的底图（改图用），返回绝对路径。 */
    fun saveInput(context: Context, taskId: Long, bytes: ByteArray): File {
        val dir = File(context.filesDir, "history/$taskId").apply { mkdirs() }
        val f = File(dir, "input.${ext(bytes)}")
        f.writeBytes(bytes)
        return f
    }

    /** 导出到相册。Android 10+ 走 MediaStore，不申请存储权限。 */
    fun saveToGallery(context: Context, file: File, displayName: String): Boolean = runCatching {
        val name = "$displayName.${file.extension}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime(file.extension))
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KimiFreePainter")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(file.readBytes()) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "KimiFreePainter"
            ).apply { mkdirs() }
            file.copyTo(File(dir, name), overwrite = true)
            true
        }
    }.getOrDefault(false)

    private fun mime(ext: String) = when (ext.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    fun deleteTaskDir(context: Context, taskId: Long) {
        File(context.filesDir, "history/$taskId").deleteRecursively()
    }
}
