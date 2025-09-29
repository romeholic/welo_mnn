package com.welo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.llm.LlmPresenter
import com.welo.base.gone
import com.welo.callback.IChat
import com.welo.launcher.listener.ChatActionListener
import com.welo.util.LogUtil
import com.welo.util.ScreenState

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ClickableViewAccessibility", "InflateParams")
open class FloatingView(private val context: Context) {
    companion object{
        private const val TAG = "FloatingView"
    }
    protected lateinit var chatCallback: IChat
    protected var currentChatId: Long = System.currentTimeMillis()

    private val marginBottom = context.resources.getDimensionPixelSize(R.dimen.dp_17)
    private val windowManager: WindowManager by lazy {
        context.getSystemService(WINDOW_SERVICE) as WindowManager
    }
    // 底部按钮位置
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        x = 0
        // 初始：floatingView隐藏在屏幕底部下方（y = -自身高度 - 导航栏高度，避免露边）
        y = -context.resources.getDimensionPixelSize(R.dimen.dp_20)

        // 关键设置：确保不永久占用输入法
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }
    private val floatingView by lazy {
        LayoutInflater.from(context).inflate(R.layout.chat_float_window, null).apply {
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            setOnTouchListener { _, event ->
                when (event.action) {
                    // 重点：处理窗口外部触摸事件
                    MotionEvent.ACTION_OUTSIDE -> {
                        if (isExpanded) {  // 仅当展开时才收起
                            toggleSidebar()  // 收起侧边栏
                            return@setOnTouchListener true  // 消费事件
                        }
                        false  // 未展开时不处理
                    }
                    // ... 其他事件处理（如内部空白点击）...
                    else -> false
                }
            }
        }
    }

    //把手view
    private val handleView by lazy {
        LayoutInflater.from(context).inflate(R.layout.sidebar_handle, null).apply {
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

            ivArrow = findViewById(R.id.iv_arrow)
            // 新增：点击事件（触发展开/收起）
            setOnClickListener {
                toggleSidebar()  // 切换侧边栏状态
            }
        }
    }
    private val handleParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        x = 0
        y = 0
    }

    // 顶部文本输出悬浮窗参数
    private val textViewParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = context.resources.getDimensionPixelSize(R.dimen.dp_40) // 顶部距离
    }
    val charOutPutTextView by lazy {
        TextView(context).apply {
            text = "快捷操作"
            textSize =
                context.resources.getDimensionPixelSize(R.dimen.sp_16) / context.resources.displayMetrics.scaledDensity
            setTextColor(ContextCompat.getColor(context, R.color.black))
            movementMethod = ScrollingMovementMethod()
            background = ContextCompat.getDrawable(context, R.drawable.out_put_background)
            // 显式启用滚动
            isVerticalScrollBarEnabled = true
            isScrollContainer = true
            setScrollbarFadingEnabled(false)
            maxHeight = context.resources.getDimensionPixelSize(R.dimen.dp_180)
            val padding = context.resources.getDimensionPixelSize(R.dimen.dp_8)
            setPadding(padding,padding,padding,padding)
            // Q对应API 29
            try {
                val scrollbarThumb = ContextCompat.getDrawable(context, R.drawable.scrollbar_thumb)
                val scrollbarTrack = ContextCompat.getDrawable(context, R.drawable.scrollbar_track)
                verticalScrollbarThumbDrawable = scrollbarThumb
                verticalScrollbarTrackDrawable = scrollbarTrack
            } catch (e: Exception) {
                e.printStackTrace()
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                context.resources.getDimensionPixelSize(R.dimen.dp_180)
            ).apply {
                marginStart = context.resources.getDimensionPixelSize(R.dimen.dp_24)
                topMargin = context.resources.getDimensionPixelSize(R.dimen.dp_16)
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.dp_24)
                bottomMargin = context.resources.getDimensionPixelSize(R.dimen.dp_20)
            }
        }
    }
    private val orbChatBarView: ChatBarView by lazy { floatingView.findViewById(R.id.orb_chat_bar) }

    private var currentState: ScreenState = ScreenState.Idle

    private var isExpanded = false
    private lateinit var ivArrow: ImageView
    init {
        windowManager.addView(floatingView, params)
        windowManager.addView(handleView, handleParams)

        params.y = -floatingView.measuredHeight

        // 初始确认箭头方向（避免状态不一致）
        collapseSidebar()
        initListeners()

        orbChatBarView.setParentWinType(ChatBarView.Companion.WINDOW_TYPE_FLOAT)

        orbChatBarView.gone()
        handleView.gone()
    }

    private fun initListeners() {
        orbChatBarView.setChatActionListener(object : ChatActionListener{
            override fun onEditClick(editText: EditText) {
                editText.requestFocus()

                // 临时修改窗口标志以允许获取焦点
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(floatingView, params)

                // 立即尝试显示软键盘
                showSoftKeyboard(editText)
            }

            override fun onCloseWindow() {
                if (isExpanded){
                    toggleSidebar()
                }
            }

            override fun deleteImage(uri: Uri) {
            }

            override fun onVoiceClick() {
            }

            override fun onFileClick(clickType: String) {
                super.onFileClick(clickType)
            }
        })
    }
    fun stateChange(screenState: ScreenState) {
        currentState = screenState
    }

    fun showChatOutput() {
        // 检查是否已添加，避免重复添加
        if (charOutPutTextView.parent == null) {
            windowManager.addView(charOutPutTextView, textViewParams)
        }
    }

    fun hideChatOutput() {
        try {
            if (charOutPutTextView.parent != null) {
                charOutPutTextView.visibility = View.GONE
                windowManager.removeView(charOutPutTextView)
                charOutPutTextView.text = ""
            }
        } catch (e: Exception) {
            LogUtil.e("FloatingIcon", "Error removing text view", e)
        }
    }

    fun addChatOutput(text: String) {
        LogUtil.d("FloatingIcon", "addChatOutput: $text")
        showChatOutput()
        if (charOutPutTextView.visibility != View.VISIBLE) {
            charOutPutTextView.visibility = View.VISIBLE
        }
        if (charOutPutTextView.text.isEmpty()) {
            charOutPutTextView.text = text
        }
    }


    // 切换侧边栏状态
    private fun toggleSidebar() {
        if (isExpanded) {
            ivArrow.visibility = View.VISIBLE
            collapseSidebar()
        } else {
            ivArrow.visibility = View.GONE
            expandSidebar()
        }
    }

    // 修正：展开侧边栏（从隐藏→显示）
    private fun expandSidebar() {
        // 动画：Y轴从「-自身高度」（隐藏）→ 「0」（显示）（Y轴正方向向下）
        val animation = TranslateAnimation(
            0f, 0f,  // X轴不变
            -getFloatingViewHeight(), marginBottom.toFloat()   // Y轴：从隐藏位置→显示位置
        ).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            fillAfter = true  // 动画结束后保持最终状态
        }

        floatingView.startAnimation(animation)
        // 更新布局参数：将floatingView固定到显示位置（y=0 + 导航栏高度）
        params.y = marginBottom
        windowManager.updateViewLayout(floatingView, params)

        isExpanded = true
    }

    // 修正：收起侧边栏（从显示→隐藏）
    private fun collapseSidebar() {
        // 动画：Y轴从「0」（显示）→ 「-自身高度」（隐藏）
        val animation = TranslateAnimation(
            0f, 0f,  // X轴不变
            0f, -getFloatingViewHeight()  // Y轴：从显示位置→隐藏位置
        ).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            fillAfter = true
        }

        floatingView.startAnimation(animation)
        // 更新布局参数：将floatingView固定到隐藏位置
        params.y = -getFloatingViewHeight().toInt()
        windowManager.updateViewLayout(floatingView, params)

        isExpanded = false
    }

    // 后续需要使用时
    fun getFloatingViewHeight(): Float {
        return floatingView.measuredHeight.toFloat()
    }

    /** 销毁视图（避免内存泄漏） */
    fun destroy() {
        runCatching {
            listOf(charOutPutTextView, floatingView, handleView).forEach { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view) // 使用removeViewImmediate确保同步移除
                }
            }
        }.onFailure {
            LogUtil.e(TAG, "销毁视图失败", it)
        }
    }

    // 添加隐藏软键盘的工具方法
    @SuppressLint("ServiceCast")
    protected fun hideSoftKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        // 确保恢复NOT_FOCUSABLE标志
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(floatingView, params)
        view.clearFocus()
    }

    // 添加显示软键盘的工具方法
    private fun showSoftKeyboard(view: View) {
        if (view.isAttachedToWindow) {
            // 确保在主线程执行
            view.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

                // 先尝试隐藏已有键盘，避免冲突
//                imm.hideSoftInputFromWindow(view.windowToken, 0)

                // 延迟一小段时间确保系统准备好
                view.postDelayed({
                    // 再次确认焦点
                    if (!view.hasFocus()) {
                        view.requestFocus()
                    }

                    // 显示软键盘
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)

                    // 设置焦点变化监听器，用于在失去焦点时恢复窗口标志
                    view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                        if (!hasFocus) {
                            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            windowManager.updateViewLayout(floatingView, params)
                            v.onFocusChangeListener = null
                        }
                    }
                }, 50L) // 50毫秒延迟通常足够
            }
        }
    }
}