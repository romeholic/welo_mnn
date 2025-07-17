package com.welo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.welo.base.ImageLoader

@SuppressLint("ClickableViewAccessibility")
class FloatingIcon(private val context: Context) {
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val floatingIcon: ImageView = ImageView(context)
    // 初始化布局参数
    private var params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        64,
        64,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    init {

        // 设置初始位置
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 100

        // 设置触摸事件监听
        floatingIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置和触摸点
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 触摸结束，图标停留在当前位置
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离并更新图标位置
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingIcon, params)
                    true
                }
                else -> false
            }
        }
    }

    fun setIconResource(resId: Int) {
        ImageLoader.getInstance(context).loadGif(resId, floatingIcon)
    }

    // 显示浮动图标
    fun show() {
        try {
            windowManager.addView(floatingIcon, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 隐藏浮动图标
    fun hide() {
        try {
            windowManager.removeView(floatingIcon)
            ImageLoader.getInstance(context).clear(floatingIcon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}