package com.welo.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin


class DashedLineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val dashPaint = Paint().apply {
        color = Color.parseColor("#66FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f) // 虚线效果
        strokeCap = Paint.Cap.ROUND // 圆角端点
    }
    private val centerX: Float
        get() = width / 2f

    private val centerY: Float
        get() = height / 2f

    //线的长度
    private val endRadius = dpToPx(context, 100f)
    //绘制起点距离中心的长度（人像中心）
    private val startRadius = dpToPx(context, 45f)
    fun dpToPx(context: Context, dpValue: Float): Int {
        val density = context.getResources().getDisplayMetrics().density
        return (dpValue * density + 0.5f).toInt() // 加 0.5f 是为了四舍五入
    }
    // 动画属性
    private var animationProgress = 0f
    private var update_line_index = -1

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            animationProgress = animation.animatedValue as Float
            invalidate()
        }
    }



    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    fun startAnimation() {
        animator.start()
    }

    fun stopAnimation() {
        animator.cancel()
    }
    fun updateLine(idx : Int){
        update_line_index =idx
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    // 设置画笔透明度随动画进度变化
        dashPaint.alpha = (animationProgress * 255).toInt()

        // 绘制放射状虚线
        for (angle in 0 until 360 step 60) { // 间隔多少度一条线

            val radians = Math.toRadians(angle.toDouble())
            val endX = centerX + endRadius * cos(radians).toFloat()
            val endY = centerY + endRadius * sin(radians).toFloat()

            val startX = centerX + startRadius * cos(radians).toFloat()
            val startY = centerY + startRadius * sin(radians).toFloat()

            if(update_line_index == radians.toInt()|| update_line_index == -1){
                dashPaint.alpha = 100
            }else{
                dashPaint.alpha = 0
            }
            canvas.drawLine(startX, startY, endX, endY, dashPaint)
        }
    }
}