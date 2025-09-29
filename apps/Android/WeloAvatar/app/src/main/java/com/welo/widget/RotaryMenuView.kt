package com.example.camera_demo.wheel1


import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


class RotaryMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 圆盘和标签数据
    private val labels = listOf("同事", "朋友", "同学", "亲属", "熟人")
    private val labelColors = listOf(
        Color.parseColor("#4CAF50"),
        Color.parseColor("#2196F3"),
        Color.parseColor("#FF9800"),
        Color.parseColor("#E91E63"),
        Color.parseColor("#9C27B0")
    )

    // 绘制相关对象
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // 圆盘属性
    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var innerRadius = 0f

    // 旋转状态
    private var currentAngle = 0f
    private var lastAngle = 0f
    private var isRotating = false
    private var velocity = 0f
    private var lastTime = 0L
    private var rotationAngle = 0f

    // 动画
    private var flingAnimator: ValueAnimator? = null

    init {
        // 初始化画笔
        textPaint.color = Color.WHITE
        textPaint.textSize = 42f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD

        centerCirclePaint.color = Color.parseColor("#00000000")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 计算中心点和半径
        centerX = w / 2f
        centerY = h / 2f
        outerRadius = min(w, h) * 0.4f
        innerRadius = outerRadius * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景
        canvas.drawColor(Color.parseColor("#001A1A1A"))

        // 绘制外圆环
        paint.color = Color.parseColor("#00000000")
        canvas.drawCircle(centerX, centerY, outerRadius, paint)

        // 绘制中心圆
        canvas.drawCircle(centerX, centerY, innerRadius, centerCirclePaint)

        // 绘制标签
        drawLabels(canvas)
    }

    private fun drawLabels(canvas: Canvas) {
        val sliceAngle = 360f / labels.size

        for (i in labels.indices) {
            val startAngle = i * sliceAngle + currentAngle
            val sweepAngle = sliceAngle

            // 绘制标签文本
            val middleAngle = Math.toRadians((startAngle + sweepAngle / 2).toDouble())
            val textRadius = (outerRadius + innerRadius) / 2 * 0.9f
            val textX = centerX + textRadius * cos(middleAngle).toFloat()
            val textY = centerY + textRadius * sin(middleAngle).toFloat()

            textPaint.color = Color.BLACK
            canvas.drawText(labels[i], textX, textY + 15f, textPaint)
            //文本外圈圆形
            var badgeRadius = 0f
            paint.color = Color.parseColor("#80FFA500")
            // 判断是否在顶部区域（默认±5度）
            val isAtTop = isLabelAtTop(centerY, textY, textRadius)
            if (isAtTop) {
//                 顶部图标放大
                badgeRadius = textPaint.textSize * 2.0f
                UpdateLable(canvas, textX, textY)
            } else {
                badgeRadius = textPaint.textSize * 1.5f
            }
            canvas.drawCircle(textX, textY + 8f, badgeRadius, paint)
        }
    }

    //   显示侧上的小图标,依据text的坐标显示
    private fun UpdateLable(canvas: Canvas, textX: Float, textY: Float) {
        canvas.drawCircle(
            textX + textPaint.textSize * 1.5f,
            textY - 20f,
            textPaint.textSize,
            paint
        )
        canvas.drawText("16", textX + textPaint.textSize * 1.5f, textY - 16f, textPaint)
    }

    // 判断是否在顶部区域（默认±5度）
    private fun isLabelAtTop(
        centerY: Float,
        textY: Float,
        radius: Float,
        tolerance: Float = 5f
    ): Boolean {
        val topY = centerY - radius
        return abs(textY - topY) <= tolerance
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 停止当前动画
                flingAnimator?.cancel()

                // 检查是否触摸在圆环上
                val x = event.x
                val y = event.y
                val distance = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))

                if (distance in innerRadius..outerRadius) {
                    lastAngle = calculateAngle(x, y)
                    isRotating = true
                    lastTime = System.currentTimeMillis()
                    velocity = 0f
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isRotating) {
                    val x = event.x
                    val y = event.y
                    val angle = calculateAngle(x, y)

                    // 计算角度变化
                    var delta = angle - lastAngle
                    if (abs(delta) > 180) {
                        delta = if (delta > 0) delta - 360 else delta + 360
                    }

                    currentAngle += delta
                    currentAngle %= 360f

                    // 计算速度
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastTime).coerceAtLeast(1)
                    velocity = delta / elapsed * 1000

                    lastAngle = angle
                    lastTime = now

                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isRotating) {
                    isRotating = false

                    // 添加惯性滚动效果
                    if (abs(velocity) > 5) {
                        startFlingAnimation(velocity)
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // 计算触摸点相对于中心的角度
    private fun calculateAngle(x: Float, y: Float): Float {
        val dx = x - centerX
        val dy = y - centerY
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) angle += 360f
        return angle
    }

    private fun startFlingAnimation(initialVelocity: Float) {
        flingAnimator?.cancel()

        flingAnimator = ValueAnimator.ofFloat(initialVelocity, 0f).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                currentAngle += value * 0.02f
                rotationAngle = value
                currentAngle %= 360f
                invalidate()
            }
            start()
        }
    }
}