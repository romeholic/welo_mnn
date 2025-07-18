package com.welo.util

import android.content.Context
import android.widget.ImageView
import com.taobao.meta.avatar.R
import com.welo.base.ImageLoader

/**
 * 封装不同场景的状态，用于在应用中传递和处理不同的业务逻辑
 */
class LoadScreenAnimUtil private constructor() {
    private lateinit var context: Context
    private lateinit var imageView: ImageView
    private var init = false

    // 静态单例实例
    companion object {
        val instance: LoadScreenAnimUtil by lazy { LoadScreenAnimUtil() }
    }

    // 初始化方法，需在使用前调用
    fun init(context: Context, imageView: ImageView) {
        this.context = context
        this.imageView = imageView
        init = true
        loadScreenAnim()
    }
    /**
     * 根据不同的业务场景加载对应的动画资源
     * @param context 上下文
     * @param imageView 显示动画的 ImageView
     * @param scenario 当前业务场景
     */
    fun loadScreenAnim(scenario: ScenarioSealed = ScenarioSealed.Idle) {
        if (!init) {
            throw IllegalStateException("ScenarioSealedUtil must be initialized before use")
        }
        val resId = when (scenario) {
            is ScenarioSealed.Idle -> R.drawable.anim_default // 空闲状态图标
            is ScenarioSealed.VoiceInput -> R.drawable.anim_voice_input
            is ScenarioSealed.VoiceOutput -> R.drawable.anim_voice_output
            is ScenarioSealed.TextInput -> 0
            is ScenarioSealed.TextOutput -> 0
            is ScenarioSealed.Error -> R.drawable.anim_error
            is ScenarioSealed.Loading -> R.drawable.anim_loading
        }
        // 确保在加载新资源前清理旧资源
        imageView.setImageDrawable(null)

        ImageLoader.Companion.getInstance(context).loadGif(resId, imageView)
    }
}

/**
 * 封装不同场景的状态，用于在应用中传递和处理不同的业务逻辑
 */
sealed class ScenarioSealed {
    object Idle : ScenarioSealed() // 空闲状态，表示没有进行任何操作
    object Loading : ScenarioSealed() // 加载状态，表示正在进行某些操作
    object VoiceInput : ScenarioSealed() // 语音输入
    object VoiceOutput : ScenarioSealed() // 语音输出
    object TextInput : ScenarioSealed() // 文本输入
    object TextOutput : ScenarioSealed() // 文本输出
    object Error : ScenarioSealed() // 错误状态，表示发生了异常或错误
}