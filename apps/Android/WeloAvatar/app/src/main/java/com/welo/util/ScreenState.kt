package com.welo.util

/**
 * 场景状态
 */
sealed class ScreenState {
    object Idle : ScreenState() // 空闲状态
    object VoiceInput : ScreenState() // 语音输入状态
    object VoiceOutput : ScreenState() // 语音输出状态
    object TextInput : ScreenState() // 文本输入状态
    object TextOutput : ScreenState() // 文本输出状态
    object CameraState: ScreenState()//进入相机
    object Error : ScreenState() // 错误状态
    object Loading : ScreenState() // 加载状态
    object StopRequest: ScreenState()//停止请求
}