package com.jiangdg.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

object MediaStampUtils {
    fun stampPhoto(file: File, capturedAt: Long, comment: String) {
        val source = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalStateException("Unable to decode captured photo")
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        if (bitmap !== source) source.recycle()
        drawStamp(Canvas(bitmap), bitmap.width, bitmap.height, capturedAt, comment)
        val temporary = File(file.parentFile, "${file.name}.stamped")
        FileOutputStream(temporary).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output))
        }
        bitmap.recycle()
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun drawStamp(
        canvas: Canvas,
        width: Int,
        height: Int,
        capturedAt: Long,
        comment: String
    ) {
        val textSize = (min(width, height) * 0.052f).coerceIn(30f, 80f)
        val padding = textSize * 0.48f
        val lineGap = textSize * 0.18f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFF00.toInt()
            this.textSize = textSize
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val stroke = Paint(fill).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (textSize * 0.10f).coerceAtLeast(3f)
        }
        val date = SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(capturedAt))
        val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(capturedAt))
        val lines = wrapComment(comment, fill, width * 0.72f).takeLast(3) + listOf(date, time)
        val blockWidth = lines.maxOf(fill::measureText)
        val blockHeight = textSize * lines.size + lineGap * (lines.size - 1)
        val right = width - padding
        val bottom = height - padding
        canvas.drawRoundRect(
            RectF(
                right - blockWidth - padding,
                bottom - blockHeight - padding * 0.55f,
                width.toFloat(),
                height.toFloat()
            ),
            padding * 0.35f,
            padding * 0.35f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x82000000.toInt() }
        )
        lines.forEachIndexed { index, line ->
            val baseline = bottom - (lines.lastIndex - index) * (textSize + lineGap)
            canvas.drawText(line, right, baseline, stroke)
            canvas.drawText(line, right, baseline, fill)
        }
    }

    private fun wrapComment(value: String, paint: Paint, maxWidth: Float): List<String> {
        if (value.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        value.trim().lines().forEach { paragraph ->
            var current = ""
            paragraph.trim().split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                    current = candidate
                } else {
                    result.add(current)
                    current = word
                }
            }
            if (current.isNotBlank()) result.add(current)
        }
        return result
    }
}
