package com.welo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.taobao.meta.avatar.databinding.ViewImageTextBinding

class ImageTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val binding = ViewImageTextBinding.inflate(LayoutInflater.from(context), this, true)

    fun setAiTools(name: String,resId:Int) {
        binding.toolsIcon.setImageResource(resId)
        binding.aiToolsName.text = name
    }

    fun setBackgroundAlpha(alpha: Float) {
        binding.root.alpha = alpha
    }
}