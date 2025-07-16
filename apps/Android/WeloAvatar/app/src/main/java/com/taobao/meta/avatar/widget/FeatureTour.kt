import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.widget.dpToPx

/**
 * 应用内功能引导组件，用于高亮显示特定视图并提供引导信息
 */
class FeatureTour(private val activity: Activity) {
    private val overlayView = OverlayView(activity)
    private var currentStep: Step? = null
    var steps = mutableListOf<Step>()
    private var currentStepIndex = 0
    private var isShowing = false
    private var onTourCompleted: (() -> Unit)? = null
    private var onStepChanged: ((stepIndex: Int, step: Step) -> Unit)? = null

    init {
        // 设置覆盖层的初始属性
        overlayView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        overlayView.visibility = View.VISIBLE
    }

    /**
     * 添加一个引导步骤
     */
    fun addStep(step: Step): FeatureTour {
        steps.add(step)
        return this
    }

    /**
     * 设置引导完成回调
     */
    fun setOnTourCompletedListener(listener: () -> Unit): FeatureTour {
        onTourCompleted = listener
        return this
    }

    /**
     * 设置步骤变更回调
     */
    fun setOnStepChangedListener(listener: (stepIndex: Int, step: Step) -> Unit): FeatureTour {
        onStepChanged = listener
        return this
    }

    /**
     * 开始引导
     */
    fun start() {
        if (steps.isEmpty() || isShowing) return

        isShowing = true
        val rootView = activity.window.decorView as FrameLayout
        rootView.addView(overlayView)
        showStep(0)
    }

    /**
     * 显示指定索引的步骤
     */
    private fun showStep(index: Int) {
        if (index < 0 || index >= steps.size) {
            dismiss()
            return
        }

        currentStepIndex = index
        currentStep = steps[index]
        overlayView.updateButtons() // 更新按钮状态
        overlayView.invalidate()

        onStepChanged?.invoke(index, currentStep!!)
    }

    /**
     * 显示下一个步骤
     */
    fun next() {
        showStep(currentStepIndex + 1)
    }

    /**
     * 显示上一个步骤
     */
    fun previous() {
        showStep(currentStepIndex - 1)
    }

    /**
     * 关闭引导
     */
    fun dismiss() {
        Log.d("FeatureTour", "Dismissing tour at step $currentStepIndex")
        if (!isShowing) return

        isShowing = false
        val rootView = activity.window.decorView as FrameLayout
        rootView.removeView(overlayView)
        onTourCompleted?.invoke()
    }

    /**
     * 引导步骤类，包含高亮视图和提示信息
     */
    data class Step(
        val targetView: View,
        val message: String,
        @ColorInt val backgroundColor: Int = "#DD000000".toColorInt(),
        @ColorInt val highlightColor: Int = Color.WHITE,
        val padding: Int = 16.dpToPx(),
        val cornerRadius: Int = 8.dpToPx(),
        val arrowSize: Int = 16.dpToPx(),
        val animationDuration: Long = 300,
        val overlayAlpha: Int = 220,
        val onClick: (() -> Unit)? = null
    )

    /**
     * 覆盖层视图，用于绘制高亮区域和提示信息
     */
    private inner class OverlayView(context: android.content.Context) : FrameLayout(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private var targetRect = Rect()
        private var messageView: LinearLayout? = null
        private var messageRect = Rect()
        private var btnNext: Button? = null
        private var btnCancel: ImageView? = null
        private val buttonContainer = FrameLayout(context).apply {
            // 按钮容器始终在最上层
            elevation = 8.dpToPx(activity).toFloat()
        }

        init {
            isClickable = true
            isFocusable = true
            setWillNotDraw(false)

            // 设置文本画笔
            textPaint.color = Color.WHITE
            textPaint.textSize = 16.spToPx(activity).toFloat()
            // 初始化按钮容器
            addView(buttonContainer)
            // 初始化按钮
            initButtons()
        }

        private fun initButtons() {
            // 下一步按钮
            btnNext = Button(context).apply {
                text = "下一步"
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.button_bg)
                elevation = 4f
                visibility = GONE
                setPadding(16.dpToPx(activity),0,16.dpToPx(activity),0)
                setOnClickListener {
                    if (currentStepIndex == steps.size - 1) {
                        // 如果是最后一步，完成引导
                        dismiss()
                    } else {
                        // 否则，显示下一步
                        next()
                    }
                }
            }
            buttonContainer.addView(btnNext)

            // 取消按钮
            btnCancel = ImageView(context).apply {
                setBackgroundResource(R.drawable.close_icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE // 设置缩放类型
                adjustViewBounds = true // 保持图片比例
                elevation = 4f
                visibility = GONE
                setOnClickListener { dismiss() }

                layoutParams = LayoutParams(
                    24.dpToPx(activity), // 宽度
                    24.dpToPx(activity)  // 高度
                )
            }
            buttonContainer.addView(btnCancel)
        }

        fun updateButtons() {
            btnNext?.let { nextBtn ->
                nextBtn.text = if (currentStepIndex < steps.size - 1) "下一步" else "完成"
                btnCancel?.let { cancelBtn ->
                    // 测量按钮尺寸
                    nextBtn.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)
                    )

                    cancelBtn.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)
                    )

                    val btnWidth = nextBtn.measuredWidth
                    val btnHeight = nextBtn.measuredHeight
                    val cancelWidth = cancelBtn.measuredWidth
                    val cancelHeight = cancelBtn.measuredHeight
                    val spacing = 16.dpToPx(activity)

                    // 如果消息框存在，按钮显示在消息框下方
                    messageView?.let { msgView ->
                        val totalBtnWidth = btnWidth.coerceAtLeast(cancelWidth)
                        // 计算水平居中位置
                        val x = (messageRect.centerX() - totalBtnWidth / 2).coerceIn(
                            24.dpToPx(activity),
                            width - totalBtnWidth - 24.dpToPx(activity)
                        )
                        val y = messageRect.bottom + 16.dpToPx(activity)

                        // 更新下一步按钮位置（上方）
                        nextBtn.updateLayoutParams<LayoutParams> {
                            leftMargin = x + (totalBtnWidth - btnWidth) / 2 // 水平居中
                            topMargin = y
                            width = btnWidth
                            height = btnHeight
                        }

                        // 更新取消按钮位置（下方）
                        cancelBtn.updateLayoutParams<LayoutParams> {
                            leftMargin = x + (totalBtnWidth - cancelWidth) / 2 // 水平居中
                            topMargin = y + btnHeight + spacing
                            width = cancelWidth
                            height = cancelHeight
                        }
                    }

                    // 设置按钮可见性
                    nextBtn.visibility = VISIBLE
                    cancelBtn.visibility = VISIBLE
                }
            }
        }

        @SuppressLint("DrawAllocation")
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            currentStep?.let { step ->
                // 计算目标视图的位置
                calculateTargetRect(step.targetView, targetRect)

                drawBackgroundWithHole(canvas, step)

                // 绘制提示信息
                drawMessage(step)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                // 如果点击在按钮区域外，且没有设置点击回调，则默认下一步
                if (!isClickOnButton(event) && currentStep?.onClick == null) {
                    next()
                }
            }
            return true
        }

        private fun isClickOnButton(event: MotionEvent): Boolean {
            btnNext?.let { nextBtn ->
                if (event.x >= nextBtn.left && event.x <= nextBtn.right &&
                    event.y >= nextBtn.top && event.y <= nextBtn.bottom) {
                    return true
                }
            }

            btnCancel?.let { cancelBtn ->
                if (event.x >= cancelBtn.left && event.x <= cancelBtn.right &&
                    event.y >= cancelBtn.top && event.y <= cancelBtn.bottom) {
                    return true
                }
            }

            return false
        }

        private fun drawBackground(canvas: Canvas,step: Step) {
            // 绘制半透明背景
            paint.color = step.backgroundColor
            paint.alpha = step.overlayAlpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            // 清除高亮区域
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(
                targetRect.left.toFloat() - step.padding,
                targetRect.top.toFloat() - step.padding,
                targetRect.right.toFloat() + step.padding,
                targetRect.bottom.toFloat() + step.padding,
                step.cornerRadius.toFloat(),
                step.cornerRadius.toFloat(),
                paint
            )
            paint.xfermode = null
        }

        /**
         * 计算目标视图在屏幕中的位置
         */
        private fun calculateTargetRect(view: View, outRect: Rect) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            // 考虑状态栏高度
            val insets = ViewCompat.getRootWindowInsets(this)
            val statusBarHeight = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0

            outRect.left = location[0]
            outRect.top = location[1] - statusBarHeight
            outRect.right = location[0] + view.width
            outRect.bottom = location[1] + view.height - statusBarHeight
        }
        /**
         * 绘制带"孔洞"的背景（孔洞位置为目标视图）
         */
        private fun drawBackgroundWithHole(canvas: Canvas, step: Step) {
            // 重置路径
            path.reset()
            // 添加整个屏幕区域
            path.addRect(
                0f, 0f,
                width.toFloat(), height.toFloat(),
                Path.Direction.CW
            )

            // 减去目标视图区域（形成孔洞）
            path.addRoundRect(
                targetRect.left.toFloat() - step.padding,
                targetRect.top.toFloat() - step.padding,
                targetRect.right.toFloat() + step.padding,
                targetRect.bottom.toFloat() + step.padding,
                step.cornerRadius.toFloat(),
                step.cornerRadius.toFloat(),
                Path.Direction.CCW
            )

            // 绘制半透明背景（仅在路径区域内）
            paint.alpha = step.overlayAlpha
            canvas.drawPath(path, paint)

            // 2. 绘制虚线边框
            val dashPath = Path()
            dashPath.addRoundRect(
                targetRect.left.toFloat() - step.padding,
                targetRect.top.toFloat() - step.padding,
                targetRect.right.toFloat() + step.padding,
                targetRect.bottom.toFloat() + step.padding,
                step.cornerRadius.toFloat(),
                step.cornerRadius.toFloat(),
                Path.Direction.CW
            )

            // 设置虚线画笔
            val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.dpToPx(activity).toFloat()
                color = Color.WHITE
                pathEffect = DashPathEffect(floatArrayOf(10.dpToPx(activity).toFloat(), 5.dpToPx(activity).toFloat()), 0f)
            }
            canvas.drawPath(dashPath, dashPaint)
        }
        /**
         * 绘制提示信息（在背景之上）
         */
        private fun drawMessage(step: Step) {
            // 创建或更新消息视图
            if (messageView == null) {
                // 创建水平布局容器，用于包含图片和文本
                messageView = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL // 水平排列
                    setPadding(
                        16.dpToPx(activity),
                        12.dpToPx(activity),
                        16.dpToPx(activity),
                        12.dpToPx(activity)
                    )
                    background = ContextCompat.getDrawable(
                        context,
                        R.drawable.toast_bg
                    )
                    visibility = View.VISIBLE

                    // 添加左侧图片
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.welo_icon) // 替换为你的图片资源
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        layoutParams = LinearLayout.LayoutParams(
                            24.dpToPx(activity), // 图片宽度
                            24.dpToPx(activity)  // 图片高度
                        ).apply {
                            marginEnd = 8.dpToPx(activity) // 图片与文本的间距
                        }
                    })

                    // 添加文本
                    addView(TextView(context).apply {
                        textSize = 20f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    })
                }
                addView(messageView)
            }

            messageView?.let { view ->
                (messageView as LinearLayout).getChildAt(1)?.let { textView ->
                    (textView as TextView).text = step.message
                }
                view.measure(
                    MeasureSpec.makeMeasureSpec(width - 48.dpToPx(activity), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)
                )

                val messageWidth = view.measuredWidth
                val messageHeight = view.measuredHeight


                // 计算屏幕中央位置
                val x = (width / 2 - messageWidth / 2).coerceIn(24.dpToPx(activity), width - messageWidth - 24.dpToPx(activity))
                val y = (height / 2 - messageHeight / 2).coerceIn(24.dpToPx(activity), height - messageHeight - 24.dpToPx(activity))

                messageRect.set(x, y, x + messageWidth, y + messageHeight)

                // 更新消息视图位置
                view.updateLayoutParams<LayoutParams> {
                    leftMargin = x
                    topMargin = y
                    width = messageWidth
                    height = messageHeight
                }
                // 消息框更新后，更新按钮位置
                updateButtons()
            }
        }
    }
}

// 扩展工具函数（放在顶层）
fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

fun Int.spToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.scaledDensity).toInt()
}