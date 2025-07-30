package com.welo.service

import android.content.Context
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.tts.TtsService
import com.welo.util.AudioPlayerUtil

interface ServiceInitializer {
    fun getRecognizeService(): RecognizeService
    fun getAudioPlayer(ttsService: TtsService): AudioPlayerUtil
    fun getContext(): Context  // 提供 Context（可以是 Activity 或 Application）
}