package com.welo.launcher.listener

import android.net.Uri
import com.welo.launcher.entity.AIOptionItem

interface IInputBarListener {
    fun textSend(text: String){}

    fun onKeyUp(){}

    fun onKeyDown(){}

    fun onCancelVoiceInput(){}

    fun onClickListener(){}

    /**
     * 删除所选图片
     */
    fun deleteImage(uri: Uri){}

    /** 文件按钮点击回调 */
    fun onFileClick(clickType: String){}

    fun toolItem(aiOptionItem: AIOptionItem? = null){}
}