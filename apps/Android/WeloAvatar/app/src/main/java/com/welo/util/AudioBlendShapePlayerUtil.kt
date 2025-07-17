// Created by ruoyi.sjd on 2025/3/19.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.welo.util

import android.util.Log
import com.alibaba.mnnllm.android.utils.LogUtils
import com.taobao.meta.avatar.MainActivityWeLoActivity
import com.taobao.meta.avatar.a2bs.AudioToBlendShapeData
import com.taobao.meta.avatar.audio.AudioChunksPlayer
import com.taobao.meta.avatar.debug.DebugModule
import com.taobao.meta.avatar.tts.TtsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class AudioBlendShapePlayerUtil(activity: MainActivityWeLoActivity) {
    companion object {
        const val TAG = "WELO#AudioBlendShapePlayer"
        const val DEBUG_VERBOSE = false
    }

    // 音频播放器实例
    private var audioChunksPlayer: AudioChunksPlayer? = null
    // 存储音频片段数据（仅保留音频相关，移除动画数据）
    private val audioBlendShapeMap: MutableMap<Int, AudioBlendShape> = mutableMapOf()
    private var nextIndex = 0
    @Volatile
    private var stopped = false
    private val listeners: MutableList<Listener> = mutableListOf()
    private var nextSegmentId = 0
    private var nextSegmentText = ""
    private var segmentTokenCount = 0
    private var sessionJob: Job? = null
    private var sessionScope: CoroutineScope? = null
    // 仅保留TTS服务（移除A2BS服务）
    private val ttsService: TtsService = activity.getTtsService()

    // 音频标记映射（仅用于音频分段播放）
    private val audioMarkerMap = Collections.synchronizedMap(mutableMapOf(0 to 0))
    private val audioMarkerMapReverse = Collections.synchronizedMap(mutableMapOf(0 to 0))
    private val markerCompleteTime = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    private var sessionId = 0L
    // 等待音频片段的映射（移除动画相关等待）
    private val waitingBlendShapeMap = Collections.synchronizedMap(mutableMapOf<Int, suspend () -> Unit>())
    private val waitingAudioCompleteMap = Collections.synchronizedMap(mutableMapOf<Int, suspend () -> Unit>())

    // 移除动画相关配置和变量
    private val configThreadNum = 2
    private var lastId = -1

    // 文本处理相关（保留，用于TTS分段）
    private val SENTENCE_DELIMITERS = Regex("[。！？!?\n]")
    private val SPECIAL_CHARS = Regex("[,，:：;；*_\\[\\](){}“”‘’\"'`~]")
    private val MIN_SENTENCE_LENGTH = 5
    private val MAX_SENTENCE_LENGTH = 300
    private val pendingText = StringBuilder()
    private var lastProcessedFullText = ""

    // 播放状态（移除动画过渡相关字段）
    class PlayingStatus {
        var isBuffering: Boolean = true
        var nextPlaySegmentId = 0
        var currentAudioPosition = 0

        fun reset() {
            isBuffering = true
            nextPlaySegmentId = 0
            currentAudioPosition = 0
        }
    }

    private val playingStatus = PlayingStatus()

    init {
        // 移除与动画渲染相关的初始化
    }

    fun startNewSession(sessionId: Long) {
        Log.d(TAG, "startNewSession: $sessionId")
        this.sessionId = sessionId
        stopped = false
        if (sessionJob?.isActive == true) {
            sessionJob?.cancel()
        }
        playingStatus.reset()
        lastId = -1
        audioChunksPlayer = AudioChunksPlayer()
        sessionJob = Job()
        val executor = ThreadPoolExecutor(configThreadNum, 10, 2L, TimeUnit.SECONDS, LinkedBlockingQueue())
            .apply { allowCoreThreadTimeOut(true) }
        sessionScope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob(sessionJob))
        MainScope().launch {
            processAudioBlendShapes()
        }
    }

    // 保留音频片段获取逻辑（仅处理音频）
    private suspend fun getNextAudioBlendShape(): AudioBlendShape {
        return if (audioBlendShapeMap.containsKey(nextIndex)) {
            val result = audioBlendShapeMap[nextIndex]!!
            nextIndex++
            result
        } else {
            suspendCancellableCoroutine { continuation ->
                val id = nextIndex
                waitingBlendShapeMap[id] = {
                    if (continuation.isActive) {
                        val abs = audioBlendShapeMap[id]!!
                        nextIndex++
                        continuation.resume(abs)
                    }
                }
                continuation.invokeOnCancellation {
                    waitingBlendShapeMap.remove(id)
                }
            }
        }
    }

    // 保留音频完成标记逻辑
    private fun markSegmentCompleteTime(markerSize: Int): Long {
        val result = markerCompleteTime[markerSize]!!
        playingStatus.nextPlaySegmentId = audioMarkerMapReverse[markerSize]!!
        return result
    }

    // 保留音频播放完成等待逻辑
    private suspend fun waitAudioComplete(markerSize: Int): Long {
        return if (markerCompleteTime.containsKey(markerSize)) {
            markSegmentCompleteTime(markerSize)
        } else {
            suspendCancellableCoroutine { continuation ->
                waitingAudioCompleteMap[markerSize] = {
                    if (continuation.isActive) {
                        val result = markSegmentCompleteTime(markerSize)
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation {
                    waitingAudioCompleteMap.remove(markerSize)
                }
            }
        }
    }

    // 移除waitSmoothReady方法（动画相关）

    // 核心播放逻辑（移除动画同步等待）
    private suspend fun processAudioBlendShapes() {
        if (DebugModule.DEBUG_DISABLE_A2BS) {
            return
        }
        try {
            while (!stopped) {
                val nextAbs = getNextAudioBlendShape()
                if (stopped) break
                Log.d(TAG, "PlayAudio begin: ${nextAbs.id}")
                if (nextAbs.audio.isNotEmpty()) {
                    if (nextAbs.id == 0) {
                        withContext(Dispatchers.Main) {
                            listeners.forEach { it.onPlayStart() }
                        }
                        audioChunksPlayer?.start()
                        audioChunksPlayer?.setPlaybackSpeed(1.0f)
                    }
                    val nextSize = nextAbs.audio.size + audioChunksPlayer!!.currentSize()
                    audioMarkerMap[nextAbs.id + 1] = nextSize
                    audioMarkerMapReverse[nextSize] = nextAbs.id + 1

                    // 移除动画准备等待（waitSmoothReady相关代码）
                    if (nextAbs.id > 0) {
                        Log.d(TAG, "PlayAudio wait: ${nextAbs.id} marker size: ${audioChunksPlayer!!.currentSize()}")
                        val lastCompleteTime = waitAudioComplete(audioChunksPlayer!!.currentSize())
                        val now = System.currentTimeMillis()
                        Log.d(TAG, "PlayAudio lastCompleteTime: $lastCompleteTime now: $now duration: ${now - lastCompleteTime}")
                    }

                    Log.d(TAG, "PlayAudio listen marker: $nextSize")
                    audioChunksPlayer?.setMarkerSizeListener(nextSize) {
                        markerCompleteTime[nextSize] = System.currentTimeMillis()
                        MainScope().launch {
                            waitingAudioCompleteMap[nextSize]?.invoke()
                            waitingAudioCompleteMap.remove(nextSize)
                        }
                        Log.d(TAG, "PlayAudio marker reached: ${nextAbs.id}")
                    }
                    Log.d(TAG, "PlayAudio startPlayAudio: ${nextAbs.id}")
                    audioChunksPlayer?.playChunk(nextAbs.audio)
                    Log.d(TAG, "PlayAudio end: ${nextAbs.id}")
                }
                if (nextAbs.is_last || nextAbs.id == lastId) {
                    Log.d(TAG, "PlayAudio is last: ${nextAbs.id}")
                    waitAudioComplete(audioChunksPlayer!!.currentSize())
                    Log.d(TAG, "PlayAudio last wait complete ${nextAbs.id}")
                    withContext(Dispatchers.Main) {
                        this@AudioBlendShapePlayerUtil.stop()
                    }
                    Log.d(TAG, "PlayAudio: stopped")
                    return
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "processAudio failed", e)
        }
    }

    // 文本处理逻辑（仅保留TTS相关）
    private fun flushPendingText() {
        val remaining = pendingText.toString().trim()
        if (remaining.isNotEmpty() && remaining.length >= MIN_SENTENCE_LENGTH) {
            playText(remaining, nextSegmentId++, true)
        }
        pendingText.clear()
    }

    private fun splitAndPlay(text: String, forceSplit: Boolean) {
        if (!forceSplit) return
        val splitIndex = minOf(MAX_SENTENCE_LENGTH, text.length)
        val sentence = text.substring(0, splitIndex)
        playText(sentence, nextSegmentId++, false)
        pendingText.clear()
        pendingText.append(text.substring(splitIndex))
    }

    private fun processPendingSentences() {
        val text = pendingText.toString()
        if (text.isEmpty()) return

        val delimiterMatches = SENTENCE_DELIMITERS.findAll(text)
        if (delimiterMatches.none()) {
            if (text.length >= MAX_SENTENCE_LENGTH) {
                splitAndPlay(text, forceSplit = true)
            }
            return
        }

        var lastEnd = 0
        delimiterMatches.forEach { match ->
            val sentenceEnd = match.range.last + 1
            val sentence = text.substring(lastEnd, sentenceEnd)
            lastEnd = sentenceEnd
            if (sentence.length >= MIN_SENTENCE_LENGTH) {
                playText(sentence, nextSegmentId++, false)
            }
        }

        if (lastEnd < text.length) {
            pendingText.clear()
            pendingText.append(text.substring(lastEnd))
        } else {
            pendingText.clear()
        }
    }

    fun playStreamText(currentText: String?) {
        Log.d(TAG, "playStreamText: currentText=#${currentText}#")
        if (currentText == null) {
            flushPendingText()
            return
        }

        val cleanedText = currentText
            .replace(SPECIAL_CHARS, "")
            .replace("\n", " ")
            .trim()

        if (cleanedText.isEmpty()) return

        val addedText = if (cleanedText.startsWith(lastProcessedFullText)) {
            cleanedText.substring(lastProcessedFullText.length)
        } else {
            Log.w(TAG, "文本不匹配，可能服务端重置了回复")
            cleanedText
        }

        if (addedText.isEmpty()) return

        lastProcessedFullText = cleanedText
        pendingText.append(addedText)
        processPendingSentences()
    }

    // 文本转音频（移除A2BS处理）
    fun playText(text: String, id: Int, is_last: Boolean) {
        if (DebugModule.DEBUG_DISABLE_A2BS) {
            sessionScope?.launch {
                withContext(Dispatchers.Default) {
                    ttsService.processSherpa(text, id)
                }
            }
            Log.d(TAG, "stop..")
            return
        }

        val finalText = text.replace(SPECIAL_CHARS, "").trim()
        if (finalText.isEmpty()) {
            Log.d(TAG, "过滤后文本为空，跳过播放: $text")
            return
        }

        Log.d(TAG, "playText: ${finalText} id: ${id} isEnd:${is_last}")
        // 音频数据对象（移除动画相关参数）
        val audioBlendShape = AudioBlendShape(
            id, is_last, finalText, ShortArray(0), null, AudioToBlendShapeData()
        )
        sessionScope?.launch {
            val processTtsStartTime = System.currentTimeMillis()
            Log.d(TAG, "processTextInner TTS begin $id $finalText begin")
            ensureActive()
            ttsService.setCurrentIndex(audioBlendShape.id)
            var audioData = ShortArray(0)
            // 仅处理TTS音频生成
            if (ttsService.useSherpaTts) {
                val generatedAudio = ttsService.processSherpa(audioBlendShape.text, audioBlendShape.id)
                if (generatedAudio != null) {
                    audioChunksPlayer?.sampleRate = generatedAudio.sampleRate
                    audioData = audioChunksPlayer?.convertToShortArray(generatedAudio.samples)!!
                }
            } else {
                audioChunksPlayer?.sampleRate = 44100
                audioData = ttsService.process(audioBlendShape.text, audioBlendShape.id)
            }
            if (audioData.isEmpty()) {
                lastId = id - 1
                Log.d(TAG, "processTextInner: $id $finalText audioData is empty")
                return@launch
            }
            audioBlendShape.audio = audioData // 仅赋值音频数据
            ensureActive()
            val processTtsEndTime = System.currentTimeMillis()
            val processTtsDuration = processTtsEndTime - processTtsStartTime
            val rtf = processTtsDuration.toFloat() / 1000 / (audioData.size / audioChunksPlayer!!.sampleRate.toFloat())
            Log.d(TAG, "processTextInner TTS  $id $finalText end duration: $processTtsDuration rtf: $rtf")

            // 移除A2BS处理逻辑（a2bsService.process相关代码）

            ensureActive()
            Log.d(TAG, "processTextInner: $id $finalText end")
            addAudioBlendShape(audioBlendShape)
            // 移除动画准备时间记录
        }
    }

    // 添加音频片段（移除动画帧相关逻辑）
    private suspend fun addAudioBlendShape(audioBlendShape: AudioBlendShape) {
        Log.d(TAG, "AddAudio: ${audioBlendShape.id} text: ${audioBlendShape.text} audio size: ${audioBlendShape.audio.size}")
        audioBlendShapeMap[audioBlendShape.id] = audioBlendShape
        waitingBlendShapeMap[audioBlendShape.id]?.invoke()
        waitingBlendShapeMap.remove(audioBlendShape.id)
    }

    // 保留音频状态获取方法
    val currentTime: Long
        get() = audioChunksPlayer?.currentTime() ?: 0L

    val totalTime: Long
        get() = audioChunksPlayer?.totalTime() ?: 0L

    val isPlaying: Boolean
        get() = audioChunksPlayer?.isPlaying ?: false && !stopped

    val currentHeadPosition: Int
        get() = audioChunksPlayer?.currentHeadPosition() ?: 0

    val currentPlayingText: String
        get() {
            if (!isPlaying) {
                return ""
            }
            val currentPosition = audioChunksPlayer?.currentHeadPosition() ?: 0
            for (i in audioMarkerMap.keys) {
                if (audioMarkerMap[i]!! <= currentPosition && audioMarkerMap.containsKey(i + 1) && currentPosition <= audioMarkerMap[i + 1]!!) {
                    return audioBlendShapeMap[i]?.text ?: ""
                }
            }
            return ""
        }

    val isBuffering: Boolean
        get() = audioMarkerMapReverse.containsKey(audioChunksPlayer?.currentHeadPosition())

    // 停止逻辑（移除动画相关清理）
    fun stop() {
        Log.d(TAG, "stop: called")
        stopped = true
        playingStatus.reset()
        sessionJob?.cancel()
        audioChunksPlayer?.stop()
        audioChunksPlayer?.destroy()
        audioBlendShapeMap.clear()
        audioMarkerMap.clear()
        audioMarkerMapReverse.clear()
        nextIndex = 0
        nextSegmentId = 0
        nextSegmentText = ""
        segmentTokenCount = 0
        waitingAudioCompleteMap.clear()
        waitingBlendShapeMap.clear()
        listeners.forEach { it.onPlayEnd() }
    }

    // 简化update方法（移除动画过渡状态）
    fun update(): PlayingStatus {
        if (DebugModule.DEBUG_DISABLE_A2BS) {
            return playingStatus
        }
        if (stopped) {
            return playingStatus
        }
        val currentHeadPosition = currentHeadPosition
        playingStatus.currentAudioPosition = currentHeadPosition
        playingStatus.isBuffering = audioMarkerMapReverse.containsKey(currentHeadPosition)
        Log.v(TAG, "update: ${playingStatus.currentAudioPosition} " +
                "nextPlaySegmentId: ${playingStatus.nextPlaySegmentId} " +
                "isBuffering: ${playingStatus.isBuffering}")

        if (playingStatus.isBuffering) {
            playingStatus.nextPlaySegmentId = audioMarkerMapReverse[currentHeadPosition] ?: 0
        }

        return playingStatus
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun playSession(sessionId: Long, texts: List<String>) {
        startNewSession(sessionId)
        texts.forEachIndexed { index, text ->
            this.playText(text, index, index == texts.lastIndex)
        }
    }

    interface Listener {
        fun onPlayStart()
        fun onPlayEnd()
    }
}

// 简化AudioBlendShape类（如果允许修改，移除动画相关字段）
data class AudioBlendShape(
    val id: Int,
    val is_last: Boolean,
    val text: String,
    var audio: ShortArray,
    val extra: Any?,
    val a2bs: AudioToBlendShapeData // 保留但不使用
)