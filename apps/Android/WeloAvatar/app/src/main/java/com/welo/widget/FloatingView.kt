package com.welo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.llm.LlmPresenter
import com.welo.callback.IChat
import com.welo.util.LogUtil
import com.welo.util.ScreenState

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("ClickableViewAccessibility", "InflateParams")
open class FloatingView(private val context: Context) {
    protected lateinit var chatCallback: IChat
    protected var llmPresenter: LlmPresenter
    protected var currentChatId: Long = System.currentTimeMillis()
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
        y = context.resources.getDimensionPixelSize(R.dimen.dp_20)

        // 关键设置：确保不永久占用输入法
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
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
    private val floatingView by lazy {
        LayoutInflater.from(context).inflate(R.layout.chat_float_window, null).apply {
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        }
    }

    private val cameraIv: ImageView by lazy { floatingView.findViewById(R.id.camera_float_window) }
    private val microphoneIv: ImageView by lazy { floatingView.findViewById(R.id.microphone_float_window) }
    val sendIv: ImageView by lazy { floatingView.findViewById(R.id.send_float_window) }
    private val waveformView: WaveformView by lazy { floatingView.findViewById(R.id.waveFormView_float_window) }
    val inputEditView: EditText by lazy { floatingView.findViewById(R.id.key_bord_input) }

    private var currentState: ScreenState = ScreenState.Idle

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

    init {
        windowManager.addView(floatingView, params)
        initListeners()
        llmPresenter = LlmPresenter(charOutPutTextView)

    }

    private fun initListeners() {
        microphoneIv.setOnClickListener {
            stateChange(ScreenState.VoiceInput)
            chatCallback.startRecord()
            chatCallback.stopAnswer()
        }
        waveformView.setOnClickListener {
            microphoneIv.visibility = View.VISIBLE

        }
        cameraIv.setOnClickListener {
            if (currentState !is ScreenState.Idle) {
                chatCallback.stopRecord()
            } else {
                stateChange(ScreenState.CameraState)
            }
        }
        sendIv.setOnClickListener {
            if (currentState == ScreenState.Loading){
                chatCallback.stopRequest()
            }else{
                val inputMessage = inputEditView.text.trim().toString()
                if (inputMessage.isEmpty()) return@setOnClickListener
                chatCallback.textSend(inputMessage)
                inputEditView.text = null
                stateChange(ScreenState.Loading)
            }

        }
        inputEditView.setOnClickListener {
            // 确保获取焦点
            inputEditView.requestFocus()

            // 临时修改窗口标志以允许获取焦点
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(floatingView, params)

            // 立即尝试显示软键盘
            showSoftKeyboard(inputEditView)
        }
    }

    fun stateChange(screenState: ScreenState) {
        currentState = screenState
        when (screenState) {
            is ScreenState.Idle -> {
                microphoneIv.isEnabled = true
                waveformView.visibility = View.GONE
                waveformView.stopAnimation()
                cameraIv.setImageResource(R.drawable.icon_camera)
                microphoneIv.visibility = View.VISIBLE
                hideSoftKeyboard(inputEditView)
                inputEditView.text.clear()
                inputEditView.visibility = View.GONE
                sendIv.setImageResource(R.drawable.send_icon)

                // 确保恢复NOT_FOCUSABLE标志,防止其他页面不能弹出软键盘
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(floatingView, params)
            }

            is ScreenState.CameraState -> {
                cameraIv.setImageResource(R.drawable.icon_close)
                inputEditView.isEnabled = true
                inputEditView.visibility = View.VISIBLE
            }

            is ScreenState.Error -> {}
            is ScreenState.Loading -> {
                microphoneIv.isEnabled = false
                inputEditView.isEnabled = false
                sendIv.setImageResource(R.drawable.stop_line)
            }

            is ScreenState.TextInput -> {}
            is ScreenState.TextOutput -> {}
            is ScreenState.VoiceInput -> {
                microphoneIv.visibility = View.GONE
                // 显示语音输入动画
                waveformView.visibility = View.VISIBLE
                inputEditView.visibility = View.GONE
                waveformView.startAnimation()
                cameraIv.setImageResource(R.drawable.icon_close)
            }

            is ScreenState.VoiceOutput -> {}
            is ScreenState.StopRequest -> {
                sendIv.setImageResource(R.drawable.send_icon)
                microphoneIv.isEnabled = true
                inputEditView.isEnabled = true
            }
        }
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
        } else {
            llmPresenter.onLlmTextUpdate(text, currentChatId)
        }
    }

    fun show() {
        try {
            if (floatingView?.parent == null) {
                windowManager.addView(floatingView, params)
            }
        } catch (e: WindowManager.BadTokenException) {
            LogUtil.e("FloatingIcon", "Invalid window token", e)
        } catch (e: IllegalStateException) {
            LogUtil.e("FloatingIcon", "View already added", e)
        }
    }

    fun hide() {
        try {
            hideSoftKeyboard(inputEditView)
            inputEditView.clearFocus()
            if (floatingView?.parent != null) {
                windowManager.removeView(floatingView)
            }
        } catch (e: Exception) {
            LogUtil.e("FloatingIcon", "Error removing view", e)
        }
    }

    fun destroy() {
        runCatching {
            if (charOutPutTextView.parent != null) {
                windowManager.removeView(charOutPutTextView)
            }
            if (floatingView?.parent != null) {
                windowManager.removeView(floatingView)
            }
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