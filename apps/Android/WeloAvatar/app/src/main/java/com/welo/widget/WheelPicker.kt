package com.welo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withClip
import com.taobao.meta.avatar.R
import com.welo.callback.IWheelPicker
import kotlin.math.*
import androidx.core.graphics.withSave

/**
 * 滚轮选择器（图片显示版）
 * 完全基于图片显示的滚轮选择控件，支持多种视觉效果和交互方式
 */
class WheelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), IWheelPicker, Runnable {

    // 滚动器与手势相关
    private val scroller = Scroller(context, DecelerateInterpolator(1.5f))
    private val gestureDetector: GestureDetector
    private val touchSlop: Int
    private val minimumVelocity: Int
    private val maximumVelocity: Int
    private var velocityTracker: VelocityTracker? = null
    private var scrollState = SCROLL_STATE_IDLE
    private var scrollOffsetY = 0
    private var lastY = 0
    private var isDragging = false

    // 数据相关
    private var imageData = mutableListOf<Drawable>()
    private var selectedItemPosition = 0
    private var currentItemPosition = 0

    // 布局相关
    private var visibleItemCount = 7
    private var halfVisibleItemCount = 3
    private var itemHeight = 0
    private var drawnItemCount = 0
    private var halfDrawnItemCount = 0
    private val rectDrawn = Rect()
    private val rectCurrentItem = Rect()
    // 高亮指示器区域 - 宽度将铺满父容器
    private val highlightIndicatorRect = Rect()

    private var drawnCenterY = 0
    private var drawnCenterX = 0
    private var wheelCenterX = 0
    private var wheelCenterY = 0

    // 视觉效果相关
    private var isCyclic = false
    private var hasSameWidth = false
    private var hasIndicator = false
    // 高亮指示器属性
    private var indicatorHighlightColor = 0x33007AFF // 默认高亮蓝色
    private var indicatorCornerRadius = 8f // 圆角半径
    private var indicatorHorizontalPadding = 0 // 指示器水平内边距（左右）
    private var indicatorVerticalPadding = 0 // 指示器垂直内边距（上下）
    private var hasCurtain = false
    private var curtainColor = -0x77000001
    private var hasAtmospheric = false
    private var isCurved = false
    private var itemAlign = ALIGN_CENTER
    private val camera = Camera().apply {
        setLocation(0f, 0f, -6f)
    }

    // 图片相关
    private var imageSize = 0
    private var imageScaleType = SCALE_TYPE_FIT_CENTER

    // 监听器
    private var onItemSelectedListener: OnItemSelectedListener? = null
    private var onWheelChangeListener: OnWheelChangeListener? = null

    // 画笔 - 使用延迟初始化提高性能
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tempRect = Rect()
    // 矩阵缓存 - 避免频繁创建对象
    private val matrixCache = Matrix()

    init {
        // 初始化手势检测器
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (imageData.size <= 1) return false

                scroller.fling(
                    0, scrollOffsetY, 0, velocityY.toInt(),
                    0, 0, Int.MIN_VALUE, Int.MAX_VALUE
                )
                setScrollState(SCROLL_STATE_SCROLLING)
                postInvalidateOnAnimation()
                return true
            }
        })

        // 初始化触摸配置
        val configuration = ViewConfiguration.get(context)
        touchSlop = configuration.scaledTouchSlop
        minimumVelocity = configuration.scaledMinimumFlingVelocity
        maximumVelocity = configuration.scaledMaximumFlingVelocity

        // 解析自定义属性
        context.withStyledAttributes(attrs, R.styleable.WheelPicker) {
            visibleItemCount = getInt(R.styleable.WheelPicker_wheel_visible_item_count, 7)
            isCyclic = getBoolean(R.styleable.WheelPicker_wheel_cyclic, false)
            hasIndicator = getBoolean(R.styleable.WheelPicker_wheel_indicator, false)
            // 高亮指示器属性
            indicatorHighlightColor = getColor(R.styleable.WheelPicker_wheel_indicator_highlight_color, 0x33007AFF)
            indicatorCornerRadius = getDimension(R.styleable.WheelPicker_wheel_indicator_corner_radius, 8f)
            // 分离水平和垂直内边距，便于单独控制
            indicatorHorizontalPadding = getDimensionPixelSize(R.styleable.WheelPicker_wheel_indicator_horizontal_padding, 0)
            indicatorVerticalPadding = getDimensionPixelSize(R.styleable.WheelPicker_wheel_indicator_vertical_padding, 0)

            hasCurtain = getBoolean(R.styleable.WheelPicker_wheel_curtain, false)
            curtainColor = getColor(R.styleable.WheelPicker_wheel_curtain_color, -0x77000001)
            hasAtmospheric = getBoolean(R.styleable.WheelPicker_wheel_atmospheric, false)
            isCurved = getBoolean(R.styleable.WheelPicker_wheel_curved, false)
            itemAlign = getInt(R.styleable.WheelPicker_wheel_item_align, ALIGN_CENTER)
            imageSize = getDimensionPixelSize(
                R.styleable.WheelPicker_wheel_image_size,
                resources.getDimensionPixelSize(R.dimen.dp_48)
            )
            imageScaleType = getInt(R.styleable.WheelPicker_wheel_image_scale_type, SCALE_TYPE_FIT_CENTER)
        }

        // 确保可见项数量为奇数且大于1
        adjustVisibleItemCount()

        // 允许绘制
        setWillNotDraw(false)
    }

    /**
     * 调整可见项数量为奇数
     */
    private fun adjustVisibleItemCount() {
        var count = visibleItemCount.coerceAtLeast(3)
        if (count % 2 == 0) count++
        visibleItemCount = count
        halfVisibleItemCount = count / 2
        drawnItemCount = count + 2
        halfDrawnItemCount = drawnItemCount / 2
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 计算测量尺寸
        val width = measureDimension(widthMeasureSpec,
            paddingLeft + paddingRight + imageSize)
        val height = measureDimension(heightMeasureSpec,
            paddingTop + paddingBottom + visibleItemCount * imageSize)

        setMeasuredDimension(width, height)
    }

    /**
     * 测量尺寸的工具方法
     */
    private fun measureDimension(measureSpec: Int, defaultSize: Int): Int {
        val mode = MeasureSpec.getMode(measureSpec)
        val size = MeasureSpec.getSize(measureSpec)

        return when (mode) {
            MeasureSpec.EXACTLY -> size
            MeasureSpec.AT_MOST -> min(defaultSize, size)
            else -> defaultSize
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // 计算绘图区域
        rectDrawn.set(
            paddingLeft, paddingTop,
            width - paddingRight, height - paddingBottom
        )

        // 计算各项高度
        itemHeight = rectDrawn.height() / visibleItemCount

        // 计算中心位置
        drawnCenterY = rectDrawn.centerY()
        drawnCenterX = rectDrawn.centerX()
        wheelCenterX = drawnCenterX
        wheelCenterY = drawnCenterY

        // 计算当前项区域
        val currentItemTop = drawnCenterY - itemHeight / 2
        rectCurrentItem.set(
            rectDrawn.left, currentItemTop,
            rectDrawn.right, currentItemTop + itemHeight
        )

        // 计算高亮指示器区域（宽度铺满父容器，仅保留水平内边距）
        highlightIndicatorRect.set(
            // 左右边缘与父容器对齐，仅应用水平内边距
             indicatorHorizontalPadding,
            currentItemTop + indicatorVerticalPadding,
            width - indicatorHorizontalPadding,
            currentItemTop + itemHeight - indicatorVerticalPadding
        )
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 回调滚动事件
        onWheelChangeListener?.onWheelScrolled(scrollOffsetY)

        // 无数据则不绘制
        if (imageData.isEmpty()) {
            return
        }

        // 先绘制高亮指示器（在所有项目下方）
        if (hasIndicator) {
            drawIndicator(canvas)
        }

        // 计算绘制起始位置
        val drawnDataStartPos = -scrollOffsetY / itemHeight - halfDrawnItemCount

        // 绘制可见项
        var drawnDataPos = drawnDataStartPos + selectedItemPosition
        var drawnOffsetPos = -halfDrawnItemCount

        // 复用矩阵对象
        matrixCache.reset()

        while (drawnDataPos < drawnDataStartPos + selectedItemPosition + drawnItemCount) {
            val image = getImageForPosition(drawnDataPos)
            if (image == null) {
                drawnDataPos++
                drawnOffsetPos++
                continue
            }

            // 计算绘制位置
            val drawnItemCenterY = drawnCenterY + (drawnOffsetPos * itemHeight) + scrollOffsetY % itemHeight
            var distanceToCenter = 0

            // 处理卷曲效果
            if (isCurved) {
                distanceToCenter = calculateCurvedEffect(drawnItemCenterY, matrixCache)
            }

            // 处理空气感效果
            if (hasAtmospheric) {
                applyAtmosphericEffect(image, drawnItemCenterY)
            }

            // 计算图片绘制区域
            calculateImageBounds(drawnItemCenterY, distanceToCenter,tempRect)

            // 应用图片缩放
            applyScaleType(image, tempRect)

            // 绘制图片
            canvas.withClip(rectDrawn) {
                if (isCurved) {
                    withSave {
                        concat(matrixCache)
                        image.draw(this)
                    }
                } else {
                    image.draw(this)
                }
            }

            // 重置矩阵，为下一次绘制做准备
            matrixCache.reset()

            drawnDataPos++
            drawnOffsetPos++
        }

        // 绘制幕布
        if (hasCurtain) {
            drawCurtain(canvas)
        }
    }

    /**
     * 根据位置获取图片
     */
    private fun getImageForPosition(position: Int): Drawable? {
        if (imageData.isEmpty()) return null

        return if (isCyclic) {
            val actualPos = (position % imageData.size + imageData.size) % imageData.size
            imageData[actualPos]
        } else {
            imageData.getOrNull(position)
        }
    }

    /**
     * 计算卷曲效果
     */
    private fun calculateCurvedEffect(itemCenterY: Int, matrix: Matrix): Int {
        val ratio = (drawnCenterY - abs(drawnCenterY - itemCenterY) - rectDrawn.top).toFloat() /
                (drawnCenterY - rectDrawn.top)
        val unit = if (itemCenterY > drawnCenterY) 1 else -1
        val degree = (-(1 - ratio) * 90 * unit).coerceIn(-90f, 90f)
        val distance = (itemHeight * sin(Math.toRadians(degree.toDouble()))).toInt()

        // 矩阵变换（减少临时对象创建）
        val transX = when (itemAlign) {
            ALIGN_LEFT -> rectDrawn.left
            ALIGN_RIGHT -> rectDrawn.right
            else -> wheelCenterX
        }
        val transY = wheelCenterY - distance

        camera.save()
        camera.rotateX(degree)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-transX.toFloat(), -transY.toFloat())
        matrix.postTranslate(transX.toFloat(), transY.toFloat())

        return distance
    }

    /**
     * 应用空气感效果（透明度）
     */
    private fun applyAtmosphericEffect(image: Drawable, drawnItemCenterY: Int) {
        val alpha = ((drawnCenterY - abs(drawnCenterY - drawnItemCenterY))
            .toFloat() / drawnCenterY * 255).toInt().coerceIn(0, 255)
        image.alpha = alpha
    }

    /**
     * 计算图片绘制区域
     */
    private fun calculateImageBounds(drawnItemCenterY: Int, distanceToCenter: Int, rect: Rect) {
        val drawnCenterY = if (isCurved) this.drawnCenterY - distanceToCenter else drawnItemCenterY

        val (left, right) = when (itemAlign) {
            ALIGN_CENTER -> Pair(drawnCenterX - imageSize / 2, drawnCenterX + imageSize / 2)
            ALIGN_LEFT -> Pair(rectDrawn.left, rectDrawn.left + imageSize)
            ALIGN_RIGHT -> Pair(rectDrawn.right - imageSize, rectDrawn.right)
            else -> Pair(0, 0)
        }

        val top = drawnCenterY - imageSize / 2
        val bottom = drawnCenterY + imageSize / 2
        rect.set(left, top, right, bottom)
    }

    /**
     * 应用图片缩放类型
     */
    private fun applyScaleType(image: Drawable, rect: Rect) {
        val dw = image.intrinsicWidth
        val dh = image.intrinsicHeight
        if (dw <= 0 || dh <= 0) return

        val w = rect.width()
        val h = rect.height()

        // 计算缩放比例（减少条件判断层级）
        val scale = when (imageScaleType) {
            SCALE_TYPE_CENTER_CROP -> max(w.toFloat() / dw, h.toFloat() / dh)
            SCALE_TYPE_CENTER_INSIDE -> min(w.toFloat() / dw, h.toFloat() / dh)
            else -> min(w.toFloat() / dw, h.toFloat() / dh) // SCALE_TYPE_FIT_CENTER
        }

        // 计算偏移量
        val dx = (w - dw * scale).toInt() / 2
        val dy = (h - dh * scale).toInt() / 2

        image.setBounds(
            rect.left + dx, rect.top + dy,
            rect.left + dx + (dw * scale).toInt(),
            rect.top + dy + (dh * scale).toInt()
        )
    }

    /**
     * 绘制幕布
     */
    private fun drawCurtain(canvas: Canvas) {
        paint.color = curtainColor
        canvas.drawRect(rectCurrentItem, paint)
    }

    /**
     * 绘制高亮指示器（宽度铺满父容器）
     */
    private fun drawIndicator(canvas: Canvas) {
        paint.color = indicatorHighlightColor
        // 绘制带圆角的矩形作为高亮背景，宽度已铺满父容器
        canvas.drawRoundRect(
            highlightIndicatorRect.left.toFloat(),
            highlightIndicatorRect.top.toFloat(),
            highlightIndicatorRect.right.toFloat(),
            highlightIndicatorRect.bottom.toFloat(),
            indicatorCornerRadius,
            indicatorCornerRadius,
            paint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageData.size <= 1) {
            return super.onTouchEvent(event)
        }

        // 处理手势
        gestureDetector.onTouchEvent(event)

        val action = event.actionMasked
        val y = event.y.toInt()

        // 处理触摸事件
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    setScrollState(SCROLL_STATE_IDLE)
                }
                lastY = y
                isDragging = false
                obtainVelocityTracker()
                velocityTracker?.addMovement(event)
            }

            MotionEvent.ACTION_MOVE -> {
                obtainVelocityTracker()
                velocityTracker?.addMovement(event)

                val deltaY = y - lastY
                if (!isDragging && abs(deltaY) > touchSlop) {
                    isDragging = true
                    setScrollState(SCROLL_STATE_DRAGGING)
                }

                if (isDragging) {
                    scrollOffsetY += deltaY
                    clampScrollOffset()
                    lastY = y
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                    val initialVelocity = velocityTracker?.yVelocity?.toInt() ?: 0

                    if (abs(initialVelocity) > minimumVelocity && abs(scrollOffsetY) > touchSlop) {
                        scroller.fling(
                            0, scrollOffsetY, 0, initialVelocity,
                            0, 0, Int.MIN_VALUE, Int.MAX_VALUE
                        )
                        setScrollState(SCROLL_STATE_SCROLLING)
                        post(this)
                        invalidate()
                    } else {
                        // 滚动到最近的项
                        scrollToNearestItem()
                        setScrollState(SCROLL_STATE_IDLE)
                    }
                }
                recycleVelocityTracker()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging && scroller.isFinished) {
                    scrollToNearestItem()
                }
                recycleVelocityTracker()
                setScrollState(SCROLL_STATE_IDLE)
            }
        }

        return true
    }

    /**
     * 获取速度追踪器
     */
    private fun obtainVelocityTracker() {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
    }

    /**
     * 回收速度追踪器
     */
    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    /**
     * 滚动到最近的项
     */
    private fun scrollToNearestItem() {
        var targetPos = scrollOffsetY / itemHeight
        val remainder = scrollOffsetY % itemHeight

        if (abs(remainder) > itemHeight / 2) {
            targetPos += if (remainder > 0) 1 else -1
        }

        scrollOffsetY = if (abs(remainder) > itemHeight / 2) {
            (targetPos + if (remainder > 0) 1 else -1) * itemHeight
        } else {
            targetPos * itemHeight
        }
        updateCurrentItemPosition()
        invalidate()
    }

    /**
     * 限制滚动偏移
     */
    private fun clampScrollOffset() {
        if (!isCyclic && imageData.isNotEmpty()) {
            val minOffset = -(selectedItemPosition * itemHeight)
            val maxOffset = (imageData.size - 1 - selectedItemPosition) * itemHeight
            scrollOffsetY = scrollOffsetY.coerceIn(minOffset, maxOffset)
        }
    }

    /**
     * 更新当前项位置
     */
    private fun updateCurrentItemPosition() {
        var newPos = selectedItemPosition - scrollOffsetY / itemHeight

        newPos = if (isCyclic) {
            (newPos % imageData.size + imageData.size) % imageData.size
        } else {
            newPos.coerceIn(0, imageData.size - 1)
        }

        if (newPos != currentItemPosition) {
            currentItemPosition = newPos
            onItemSelectedListener?.onItemSelected(this, newPos)
        }
    }

    /**
     * 设置滚动状态
     */
    private fun setScrollState(newState: Int) {
        if (scrollState != newState) {
            scrollState = newState
            onWheelChangeListener?.onWheelScrollStateChanged(newState)
        }
    }

    override fun run() {
        if (scroller.computeScrollOffset()) {
            scrollOffsetY = scroller.currY
            clampScrollOffset()
            postInvalidateOnAnimation()
            post(this)
        } else {
            scrollToNearestItem()
            setScrollState(SCROLL_STATE_IDLE)
        }
    }

    // 实现IWheelPicker接口方法
    override fun getVisibleItemCount() = visibleItemCount

    override fun setVisibleItemCount(count: Int) {
        var newCount = count
        if (newCount < 3) newCount = 3
        if (visibleItemCount != newCount) {
            visibleItemCount = newCount
            adjustVisibleItemCount()
            requestLayout()
            invalidate()
        }
    }

    override fun isCyclic() = isCyclic

    override fun setCyclic(cyclic: Boolean) {
        if (isCyclic != cyclic) {
            isCyclic = cyclic
            invalidate()
        }
    }

    override fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        onItemSelectedListener = listener
    }

    override fun getSelectedItemPosition() = selectedItemPosition

    override fun setSelectedItemPosition(position: Int) {
        if (imageData.isEmpty()) return

        val newPos = position.coerceIn(0, imageData.size - 1)
        if (selectedItemPosition != newPos) {
            selectedItemPosition = newPos
            currentItemPosition = newPos
            scrollOffsetY = 0
            invalidate()
        }
    }

    override fun getCurrentItemPosition() = currentItemPosition

    override fun getImageData() = imageData

    override fun setImageData(data: MutableList<Drawable>) {
        imageData = ArrayList(data)

        // 重置位置
        val newPos = selectedItemPosition.coerceIn(0, max(0, imageData.size - 1))
        if (selectedItemPosition != newPos) {
            selectedItemPosition = newPos
            currentItemPosition = newPos
        }

        scrollOffsetY = 0
        requestLayout()
        invalidate()
        // 首次设置数据或位置变化时，主动触发选中回调
        if (imageData.isNotEmpty() && onItemSelectedListener != null) {
            onItemSelectedListener?.onItemSelected(this, currentItemPosition)
        }
    }

    override fun setSameWidth(hasSameSize: Boolean) {
        if (hasSameWidth != hasSameSize) {
            hasSameWidth = hasSameSize
            requestLayout()
            invalidate()
        }
    }

    override fun hasSameWidth() = hasSameWidth

    override fun setOnWheelChangeListener(listener: OnWheelChangeListener?) {
        onWheelChangeListener = listener
    }

    override fun getImageSize() = imageSize

    override fun setImageSize(size: Int) {
        if (size > 0 && imageSize != size) {
            imageSize = size
            requestLayout()
            invalidate()
        }
    }

    override fun setIndicator(hasIndicator: Boolean) {
        if (this.hasIndicator != hasIndicator) {
            this.hasIndicator = hasIndicator
            invalidate()
        }
    }

    override fun hasIndicator() = hasIndicator

    // 设置高亮指示器颜色
    override fun setIndicatorHighlightColor(color: Int) {
        if (indicatorHighlightColor != color) {
            indicatorHighlightColor = color
            invalidate()
        }
    }

    // 获取高亮指示器颜色
    override fun getIndicatorHighlightColor() = indicatorHighlightColor

    // 设置指示器圆角半径
    override fun setIndicatorCornerRadius(radius: Float) {
        if (indicatorCornerRadius != radius) {
            indicatorCornerRadius = radius
            invalidate()
        }
    }

    // 获取指示器圆角半径
    override fun getIndicatorCornerRadius() = indicatorCornerRadius

    // 设置指示器水平内边距（左右）
    override fun setIndicatorHorizontalPadding(padding: Int) {
        if (indicatorHorizontalPadding != padding) {
            indicatorHorizontalPadding = padding
            requestLayout()
            invalidate()
        }
    }

    // 获取指示器水平内边距
    override fun getIndicatorHorizontalPadding() = indicatorHorizontalPadding

    // 设置指示器垂直内边距（上下）
    override fun setIndicatorVerticalPadding(padding: Int) {
        if (indicatorVerticalPadding != padding) {
            indicatorVerticalPadding = padding
            requestLayout()
            invalidate()
        }
    }

    // 获取指示器垂直内边距
    override fun getIndicatorVerticalPadding() = indicatorVerticalPadding

    override fun getIndicatorSize() = 0 // 不再使用线条指示器的大小

    override fun setIndicatorSize(size: Int) {
        // 不再使用线条指示器的大小，保持接口兼容性
    }

    override fun getIndicatorColor() = indicatorHighlightColor

    override fun setIndicatorColor(color: Int) {
        indicatorHighlightColor = color
    }

    override fun setCurtain(hasCurtain: Boolean) {
        if (this.hasCurtain != hasCurtain) {
            this.hasCurtain = hasCurtain
            invalidate()
        }
    }

    override fun hasCurtain() = hasCurtain

    override fun getCurtainColor() = curtainColor

    override fun setCurtainColor(color: Int) {
        if (curtainColor != color) {
            curtainColor = color
            invalidate()
        }
    }

    override fun setAtmospheric(hasAtmospheric: Boolean) {
        if (this.hasAtmospheric != hasAtmospheric) {
            this.hasAtmospheric = hasAtmospheric
            invalidate()
        }
    }

    override fun hasAtmospheric() = hasAtmospheric

    override fun isCurved() = isCurved

    override fun setCurved(curved: Boolean) {
        if (isCurved != curved) {
            isCurved = curved
            invalidate()
        }
    }

    override fun getItemAlign() = itemAlign

    override fun setItemAlign(align: Int) {
        if (align in ALIGN_CENTER..ALIGN_RIGHT && itemAlign != align) {
            itemAlign = align
            invalidate()
        }
    }

    override fun getImageScaleType() = imageScaleType

    override fun setImageScaleType(scaleType: Int) {
        if (scaleType in SCALE_TYPE_FIT_CENTER..SCALE_TYPE_CENTER_INSIDE
            && imageScaleType != scaleType) {
            imageScaleType = scaleType
            invalidate()
        }
    }

    /**
     * 滚轮Item选中监听器
     */
    fun interface OnItemSelectedListener {
        /**
         * 当选中项改变时回调
         *
         * @param picker  滚轮选择器
         * @param position 选中项的位置
         */
        fun onItemSelected(picker: WheelPicker?, position: Int)
    }

    /**
     * 滚轮滚动状态改变监听器
     */
    interface OnWheelChangeListener {
        /**
         * 当滚轮滚动时回调
         *
         * @param offsetY Y轴偏移量
         */
        fun onWheelScrolled(offsetY: Int)

        /**
         * 当滚轮滚动状态改变时回调
         *
         * @param state 滚动状态
         */
        fun onWheelScrollStateChanged(state: Int)
    }

    companion object {
        // 滚动状态常量
        const val SCROLL_STATE_IDLE: Int = 0
        const val SCROLL_STATE_DRAGGING: Int = 1
        const val SCROLL_STATE_SCROLLING: Int = 2

        // 对齐方式常量
        const val ALIGN_CENTER: Int = 0
        const val ALIGN_LEFT: Int = 1
        const val ALIGN_RIGHT: Int = 2

        // 图片缩放类型常量
        const val SCALE_TYPE_FIT_CENTER: Int = 0
        const val SCALE_TYPE_CENTER_CROP: Int = 1
        const val SCALE_TYPE_CENTER_INSIDE: Int = 2
    }
}
