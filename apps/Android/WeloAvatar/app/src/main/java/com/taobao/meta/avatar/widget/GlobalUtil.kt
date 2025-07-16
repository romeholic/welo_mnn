package com.taobao.meta.avatar.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.NumberPicker
import android.widget.TextView
import com.alibaba.mls.api.ApplicationProvider

@SuppressLint("ClickableViewAccessibility")
fun View.setupHideKeyboardOnOutsideTouch(activity: Activity) {
    setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN) {
            val inputMethodManager = activity.getSystemService(
                Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            true
        } else false
    }
}
/**
 * 工具扩展函数：将dp转换为px
 */
fun Int.dpToPx(): Int {
    return (this * ApplicationProvider.application!!.resources.displayMetrics.density).toInt()
}

/**
 * 工具扩展函数：将sp转换为px
 */
fun Int.spToPx(): Int {
    return (this * ApplicationProvider.application!!.resources.displayMetrics.scaledDensity).toInt()
}
fun TextView.observeHeightChanges(callback: (oldHeight: Int, newHeight: Int) -> Unit) {
    // 初始高度
    var currentHeight = height
    // 监听文本变化
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // 记录变化前的高度
            currentHeight = height
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // 文本变化时不处理，等待布局更新
        }

        override fun afterTextChanged(s: Editable?) {
            // 使用 post 确保在布局完成后获取高度
            post {
                val newHeight = height
                if (newHeight != currentHeight) {
                    val oldHeight = currentHeight
                    currentHeight = newHeight
                    callback(oldHeight, newHeight)
                }
            }
        }
    })
}
// 扩展函数
fun TextView.setColorText(originalText: String, targetText: String, color: Int) {
    val spannableString = SpannableString(originalText)
    val startIndex = originalText.indexOf(targetText)

    if (startIndex != -1) {
        val endIndex = startIndex + targetText.length
        spannableString.setSpan(
            ForegroundColorSpan(color),
            startIndex,
            endIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        this.text = spannableString
    } else {
        this.text = originalText
    }
}
fun NumberPicker.setOnScrollFinishedListener(delayMillis: Long = 200L, action: (Int) -> Unit) {
    var lastValue = value
    var handler: Handler? = null
    var runnable: Runnable? = null

    setOnValueChangedListener { _, _, newVal ->
        lastValue = newVal

        // 移除之前的任务
        handler?.removeCallbacks(runnable!!)

        // 创建新的 Handler 和 Runnable
        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            action(lastValue)
        }

        // 延迟执行
        handler?.postDelayed(runnable!!, delayMillis)
    }

    // 在视图销毁时清理资源
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            handler?.removeCallbacks(runnable!!)
            handler = null
            runnable = null
        }
    })
}
