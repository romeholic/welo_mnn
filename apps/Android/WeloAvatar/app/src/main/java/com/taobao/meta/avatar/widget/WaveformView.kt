package com.taobao.meta.avatar.widget

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class WaveformView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null ) : View(context, attrs) {
    private val barCount = 24
    private val barWidth = 11f
    private val barSpace = 8f
    private val maxBarHeight = 100f
    private val minBarHeight = 20f
    private val barRadius = 8f
    private val barColor = "#2196F3".toColorInt()
    private val barColorLight = "#90CAF9".toColorInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val heights = FloatArray(barCount) {
        // 初始长度不规则，随机高度
        minBarHeight + Math.random().toFloat() * (maxBarHeight - minBarHeight)

        //初始高度是规则的
//        val ratio = if (it < barCount / 2) it.toFloat() / (barCount / 2) else (barCount - it - 1).toFloat() / (barCount / 2)
//        minBarHeight + (maxBarHeight - minBarHeight) * ratio
    }

    private var isAnimating = false

    private val animator = object : Runnable {
        override fun run() {
            // 随机波动高度
            for (i in heights.indices) {
                val base = if (i < barCount / 2) i.toFloat() / (barCount / 2) else (barCount - i - 1).toFloat() / (barCount / 2)
                val fluctuation = (Math.random() - 0.5f) * 0.5f // -0.25~0.25
                heights[i] = (minBarHeight + (maxBarHeight - minBarHeight) * (base + fluctuation).coerceIn(
                    0.0, 1.0
                )).toFloat()
            }
            invalidate()
            if (isAnimating) {
                postDelayed(this, 150)
            }
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            post(animator)
        }
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animator)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val centerY = height / 2f
        val startX = (width - (barCount * barWidth + (barCount - 1) * barSpace)) / 2f
        // 绘制波形条
        for (i in 0 until barCount) {
            val barHeight = heights[i]
            val left = startX + i * (barWidth + barSpace)
            val top = centerY - barHeight / 2
            val right = left + barWidth
            val bottom = centerY + barHeight / 2
            paint.color = if (i <= barCount / 2) barColor else barColorLight
            canvas.drawRoundRect(left, top, right, bottom, barRadius, barRadius, paint)
        }
        // 两端各留3个点
        paint.color = barColor
        val dotRadius = barWidth / 2
        for (i in 0..2) {
            val offset = (dotRadius * 2 + barSpace) * i
            // 左端3个点
            canvas.drawCircle(startX - barSpace - dotRadius - offset, centerY, dotRadius, paint)
            // 右端3个点
            canvas.drawCircle(startX + barCount * (barWidth + barSpace) + dotRadius + offset, centerY, dotRadius, paint)
        }
    }
}