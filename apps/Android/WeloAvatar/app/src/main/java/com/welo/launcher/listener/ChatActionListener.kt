package com.welo.launcher.listener

import android.net.Uri
import android.widget.EditText

/**
 * 聊天栏操作监听器接口
 * 包含语音、编辑、文件操作的回调方法
 */
interface ChatActionListener {
    /** 语音按钮点击回调 */
    fun onVoiceClick() {}

    /** 输入框点击回调 */
    fun onEditClick(editText: EditText) {}

    /** 文件按钮点击回调 */
    fun onFileClick(clickType: String) {}

    /**隐藏悬浮窗*/
    fun onCloseWindow(){}

    /**
     * 删除所选图片
     */
    fun deleteImage(uri: Uri)
    fun plusClick() {}
}