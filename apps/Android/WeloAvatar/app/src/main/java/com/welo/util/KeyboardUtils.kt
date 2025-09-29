package com.welo.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

object KeyboardUtils {

    /**
     * 请求焦点并显示键盘（优化版）
     */
    fun focusAndShowKeyboard(editText: EditText, delayMs: Long = 100) {
        if (isKeyboardShown(editText)) return
        editText.requestFocus()
        editText.postDelayed({
            val imm =
                editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, delayMs)
    }

    /**
     * 判断键盘是否显示
     */
    fun isKeyboardShown(activity: Activity): Boolean {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isActive && imm.isAcceptingText
    }

    /**
     * 判断特定EditText的键盘是否显示
     */
    fun isKeyboardShown(editText: EditText): Boolean {
        val imm =
            editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isActive(editText)
    }

    /**
     * 关闭键盘（通过Activity）
     */
    fun hideKeyboard(activity: Activity) {
        val view = activity.currentFocus
        if (view != null) {
            hideKeyboard(view)
        }
    }

    /**
     * 关闭键盘（通过View）
     */
    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * 关闭键盘（通过EditText）
     */
    fun hideKeyboard(editText: EditText) {
        if (!isKeyboardShown(editText)) return
        val imm =
            editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    /**
     * 切换键盘状态（显示/隐藏）
     */
    fun toggleKeyboard(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    /**
     * 安全地显示键盘（避免重复调用）
     */
    fun showKeyboardSafely(editText: EditText, delayMs: Long = 100) {
        if (!isKeyboardShown(editText)) {
            focusAndShowKeyboard(editText, delayMs)
        }
    }
}