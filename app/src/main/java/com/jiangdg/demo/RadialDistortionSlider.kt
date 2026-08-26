package com.jiangdg.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import kotlin.math.max

/** Réglette verticale : 0 en bas, correction maximale en haut. */
class RadialDistortionSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val trackWidth = 1.5f * density
    private val thumbRadius = 4.5f * density
    private val thumbStrokeWidth = 1.5f * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(100, 255, 255, 255)
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 102, 0)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 102, 0)
    }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thumbStrokeWidth
        color = Color.WHITE
    }

    private var normalizedValue = 0f
    private var listener: OnValueChangeListener? = null

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setColors(trackColor: Int, activeColor: Int, thumbStrokeColor: Int) {
        trackPaint.color = trackColor
        activePaint.color = activeColor
        thumbPaint.color = activeColor
        thumbStrokePaint.color = thumbStrokeColor
        invalidate()
    }

    fun setValue(value: Float, notifyListener: Boolean = false) {
        val bounded = value.coerceIn(0f, 1f)
        if (bounded == normalizedValue) return
        normalizedValue = bounded
        invalidate()
        if (notifyListener) listener?.onValueChanged(normalizedValue, true)
    }

    fun getValue(): Float = normalizedValue

    fun setOnValueChangeListener(valueChangeListener: OnValueChangeListener?) {
        listener = valueChangeListener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (30f * density).toInt()
        val desiredHeight = (180f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val top = paddingTop + thumbRadius + thumbStrokeWidth
        val bottom = max(top, height - paddingBottom - thumbRadius - thumbStrokeWidth)
        val thumbY = bottom - normalizedValue * (bottom - top)
        val halfTrack = trackWidth / 2f
        val trackRadius = trackWidth / 2f

        canvas.drawRoundRect(
            RectF(centerX - halfTrack, top, centerX + halfTrack, bottom),
            trackRadius,
            trackRadius,
            trackPaint
        )
        canvas.drawRoundRect(
            RectF(centerX - halfTrack, thumbY, centerX + halfTrack, bottom),
            trackRadius,
            trackRadius,
            activePaint
        )
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint)
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.y, finished = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateFromTouch(event.y, finished = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateFromTouch(event.y, finished = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                listener?.onValueChanged(normalizedValue, true)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateFromTouch(y: Float, finished: Boolean) {
        val top = paddingTop + thumbRadius + thumbStrokeWidth
        val bottom = max(top + 1f, height - paddingBottom - thumbRadius - thumbStrokeWidth)
        normalizedValue = (1f - (y - top) / (bottom - top)).coerceIn(0f, 1f)
        invalidate()
        listener?.onValueChanged(normalizedValue, finished)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.isScrollable = true
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
            0f,
            1f,
            normalizedValue
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        val step = 0.05f
        val target = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> normalizedValue + step
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> normalizedValue - step
            else -> return super.performAccessibilityAction(action, arguments)
        }
        setValue(target)
        listener?.onValueChanged(normalizedValue, true)
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED)
        return true
    }

    fun interface OnValueChangeListener {
        fun onValueChanged(value: Float, finished: Boolean)
    }
}
