package com.taobao.meta.avatar.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

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
