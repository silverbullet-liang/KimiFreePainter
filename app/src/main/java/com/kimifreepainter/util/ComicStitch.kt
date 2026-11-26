package com.kimifreepainter.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.File

/** 漫画长图拼接：已有白边直接接，没有则统一加白边。 */
object ComicStitch {

    private fun hasWhiteBorder(b: Bitmap): Boolean {
        val w = b.width
        val h = b.height
        if (w < 8 || h < 8) return false
        val white = { c: Int -> Color.red(c) > 235 && Color.green(c) > 235 && Color.blue(c) > 235 }
        val stepW = maxOf(1, w / 120)
        val stepH = maxOf(1, h / 120)
        var hit = 0
        var total = 0
        for (x in 0 until w step stepW) {
            total++
            if (white(b.getPixel(x, 0)) || white(b.getPixel(x, h - 1))) hit++
        }
        for (y in 0 until h step stepH) {
            total++
            if (white(b.getPixel(0, y)) || white(b.getPixel(w - 1, y))) hit++
        }
        return total > 0 && hit.toFloat() / total >= 0.8f
    }

    /** 成功返回输出文件，任何一步失败返回 null（调用方保留逐格原图）。 */
    fun stitch(paths: List<String>, out: File): File? {
        val cells = paths.mapNotNull { p ->
            runCatching { BitmapFactory.decodeFile(p) }.getOrNull()
        }
        if (cells.isEmpty()) return null
        return try {
            val pad = 24
            val needPad = cells.any { !hasWhiteBorder(it) }
            val cw = cells.maxOf { it.width } + if (needPad) pad * 2 else 0
            val ch = cells.maxOf { it.height } + if (needPad) pad * 2 else 0
            val all = Bitmap.createBitmap(cw, ch * cells.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(all)
            canvas.drawColor(Color.WHITE)
            cells.forEachIndexed { i, b ->
                val x = if (needPad) pad + (cw - pad * 2 - b.width) / 2 else (cw - b.width) / 2
                val y = i * ch + if (needPad) pad + (ch - pad * 2 - b.height) / 2 else (ch - b.height) / 2
                canvas.drawBitmap(b, x.toFloat(), y.toFloat(), null)
            }
            out.outputStream().use { all.compress(Bitmap.CompressFormat.PNG, 100, it) }
            all.recycle()
            cells.forEach { if (!it.isRecycled) it.recycle() }
            out.takeIf { it.length() > 0 }
        } catch (e: Exception) {
            null
        }
    }
}
