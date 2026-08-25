package com.jiangdg.ausbc.render.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLUtils
import com.jiangdg.ausbc.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Incruste la date et l'heure de capture dans les photos et les vidéos. */
class EffectTimestamp(
    context: Context,
    private val capturedAt: Long,
    private val comment: String = ""
) : AbstractEffect(context) {
    private val watermarkTexture = IntArray(1)
    private var watermarkSampler = -1
    private var watermarkBitmap: Bitmap? = null

    override fun getId(): Int = ID

    override fun getClassifyId(): Int = CLASSIFY_ID_TIMESTAMP

    override fun getVertexSourceId(): Int = R.raw.base_vertex

    override fun getFragmentSourceId(): Int = R.raw.effect_timestamp_fragment

    override fun init() {
        watermarkSampler = GLES20.glGetUniformLocation(mProgram, "uWatermarkSampler")
        GLES20.glGenTextures(1, watermarkTexture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, watermarkTexture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun setSize(width: Int, height: Int) {
        super.setSize(width, height)
        if (width <= 0 || height <= 0) return
        watermarkBitmap?.recycle()
        watermarkBitmap = createTimestampBitmap(width, height)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, watermarkTexture[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, watermarkBitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun beforeDraw() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, watermarkTexture[0])
        GLES20.glUniform1i(watermarkSampler, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    override fun clear() {
        GLES20.glDeleteTextures(1, watermarkTexture, 0)
        watermarkBitmap?.recycle()
        watermarkBitmap = null
    }

    private fun createTimestampBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val textSize = (min(width, height) * 0.052f).coerceIn(34f, 82f)
        val padding = textSize * 0.48f
        val lineGap = textSize * 0.18f
        val date = SimpleDateFormat("dd/MM/yy", Locale.FRANCE).format(Date(capturedAt))
        val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(capturedAt))
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TIMESTAMP_YELLOW
            this.textSize = textSize
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val stroke = Paint(fill).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (textSize * 0.10f).coerceAtLeast(3f)
        }
        val commentLines = wrapComment(comment, fill, width * 0.72f).takeLast(MAX_COMMENT_LINES)
        val lines = commentLines + listOf(date, time)
        val blockWidth = lines.maxOf(fill::measureText)
        val blockHeight = textSize * lines.size + lineGap * (lines.size - 1)
        val right = width - padding
        val bottom = height - padding
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x82000000.toInt() }
        canvas.drawRoundRect(
            RectF(
                right - blockWidth - padding,
                bottom - blockHeight - padding * 0.55f,
                width.toFloat(),
                height.toFloat()
            ),
            padding * 0.35f,
            padding * 0.35f,
            background
        )
        lines.forEachIndexed { index, line ->
            val baseline = bottom - (lines.lastIndex - index) * (textSize + lineGap)
            canvas.drawText(line, right, baseline, stroke)
            canvas.drawText(line, right, baseline, fill)
        }
        return bitmap
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

    companion object {
        const val ID = 400
        const val CLASSIFY_ID_TIMESTAMP = 40
        private const val MAX_COMMENT_LINES = 3
        private const val TIMESTAMP_YELLOW = 0xFFFFFF00.toInt()
    }
}
