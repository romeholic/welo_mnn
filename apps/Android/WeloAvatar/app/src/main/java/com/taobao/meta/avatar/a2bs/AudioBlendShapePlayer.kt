// Created by ruoyi.sjd on 2025/3/19.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.a2bs
import android.bluetooth.BluetoothClass.Device
import android.util.Log
import com.alibaba.mnnllm.android.utils.LogUtils
import com.taobao.meta.avatar.MHConfig
import com.taobao.meta.avatar.debug.DebugModule
import com.taobao.meta.avatar.MainActivity
import com.taobao.meta.avatar.audio.AudioChunksPlayer
import com.taobao.meta.avatar.nnr.NnrAvatarRender
import com.taobao.meta.avatar.tts.TtsService
import com.taobao.meta.avatar.utils.DeviceUtils
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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

class AudioBlendShapePlayer(nnrAvatarRender: NnrAvatarRender, activity: MainActivity) {

    companion object {
        const val TAG = "WELO#AudioBlendShapePlayer"
        const val DEBUG_VERBOSE = false
    }
    private var audioChunksPlayer:AudioChunksPlayer? = null
    private val audioBlendShapeMap: MutableMap<Int, AudioBlendShape> = mutableMapOf()
    private var nextIndex= 0
    @Volatile
    private var stopped = false
    private val listeners: MutableList<Listener> = mutableListOf()
    private var nextSegmentId = 0
    private var nextSegmentText = ""
    private var segmentTokenCount = 0
    private var sessionJob:Job? = null
    private var sessionScope: CoroutineScope? = null
    private val ttsService:TtsService = activity.getTtsService()
    private val a2bsService: A2BSService = activity.getA2bsService()
    //key: segementId, value: start audio size of the segment
    private val audioMarkerMap = Collections.synchronizedMap(mutableMapOf(0 to 0))
    private val audioMarkerMapReverse = Collections.synchronizedMap(mutableMapOf(0 to 0))
    private val markerCompleteTime = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    private var sessionId = 0L
    private val waitingBlendShapeMap = Collections.synchronizedMap(mutableMapOf<Int, suspend () -> Unit>())
    private val waitingAudioCompleteMap = Collections.synchronizedMap(mutableMapOf<Int, suspend () -> Unit>())
    private val waitingSmoothReadyMap = Collections.synchronizedMap(mutableMapOf<Int, suspend () -> Unit>())
    private val smoothReadyMap = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    private val readyTimeMap = Collections.synchronizedMap(mutableMapOf<Int, Long>())
    //200ms smooth time
    private val CONFIG_SMOOTH_DURATION = 200
    private val CONFIG_CHAT_SMOOTH_DURATION = 100
    private val CONFIG_NEED_SMOOTH_TO_CHAT = false
    private val configThreadNum = 2

    private var totalAudioBsFrame = 0
    private var lastId = -1

    // >>>>>>>>>>>>>>>>>>>>>>>>>>>> add for speech optimization start
    // 句子结束符（用于断句）
    private val SENTENCE_DELIMITERS = Regex("[。！？!?\n]")
    // 需过滤的特殊字符（不朗读）
    private val SPECIAL_CHARS = Regex("[,，:：;；*_\\[\\](){}“”‘’\"'`~]")
    // 最小/最大句子长度（控制分段大小）
    private val MIN_SENTENCE_LENGTH = 5
    private val MAX_SENTENCE_LENGTH = 300
    // 缓存待处理文本
    private val pendingText = StringBuilder()
    // 记录已处理的完整文本（用于计算增量）
    private var lastProcessedFullText = ""
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<< add for speech optimization end

    class PlayingStatus {
        var isBuffering: Boolean = true
        var smoothToIdlePercent = -1f
        var smoothToTalkPercent = -1f
        var nextPlaySegmentId = 0
        var currentAudioPosition = 0
        var smoothToTalkStartTime = 0L
        fun reset() {
            isBuffering = true
            smoothToIdlePercent = -1f
            smoothToTalkPercent = -1f
            nextPlaySegmentId = 0
            currentAudioPosition = 0
            smoothToTalkStartTime = 0L
        }
    }

    private val playingStatus = PlayingStatus()

    init {
        nnrAvatarRender.setAudioBlendShapePlayer(this)
    }

    fun startNewSession(sessionId: Long) {
        this.sessionId = sessionId
        stopped = false
        if (sessionJob?.isActive == true) {
            sessionJob?.cancel()
        }
        totalAudioBsFrame = 0
        playingStatus.reset()
        lastId = -1
        audioChunksPlayer = AudioChunksPlayer()
        sessionJob = Job()
        val executor = ThreadPoolExecutor(configThreadNum, 10, 2L, TimeUnit.SECONDS, LinkedBlockingQueue())
            .apply {
                this.allowCoreThreadTimeOut(true)
            }
        sessionScope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob(sessionJob))
        MainScope().launch {
            processAudioBlendShapes()
        }
    }

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

    private fun markSegmentCompleteTime(markerSize: Int):Long {
        val result = markerCompleteTime[markerSize]!!
        playingStatus.nextPlaySegmentId = audioMarkerMapReverse[markerSize]!!
        return result
    }

    private suspend fun waitAudioComplete(markerSize: Int):Long {
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

    private suspend fun waitSmoothReady(segmentId: Int):Long {
        return if (segmentId == 0) {
            0
        } else if (smoothReadyMap.containsKey(segmentId)) {
            smoothReadyMap[segmentId]!!
        } else {
            suspendCancellableCoroutine { continuation ->
                waitingSmoothReadyMap[segmentId] = {
                    if (continuation.isActive) {
                        Log.d(TAG, "waitSmoothReady segmentId: $segmentId")
                        val result = smoothReadyMap[segmentId]!!
                        continuation.resume(result)
                    }
                }
                continuation.invokeOnCancellation {
                    waitingSmoothReadyMap.remove(segmentId)
                }
            }
        }
    }

    suspend fun getFirstAudioBlendShape(): AudioBlendShape {
        val nextAbs = getNextAudioBlendShape()
        return nextAbs
    }

    private suspend fun processAudioBlendShapes() {
        if (DebugModule.DEBUG_DISABLE_A2BS) {
            return
        }
        try {
            while (!stopped) {
                val nextAbs = getNextAudioBlendShape()
                if (stopped) break
                Log.d(TAG, "PlayBlendShapex begin: ${nextAbs.id}")
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
                    if (nextAbs.id > 0) {
                        Log.d(TAG, "PlayBlendShape wait: ${nextAbs.id} marker size: ${audioChunksPlayer!!.currentSize()}")
                        val lastCompleteTime = waitAudioComplete(audioChunksPlayer!!.currentSize())
                        var now = System.currentTimeMillis()
                        Log.d(TAG, "PlayBlendShape lastCompleteTime: $lastCompleteTime now: $now duration: ${now - lastCompleteTime}")
                        val smoothReadyTime = waitSmoothReady(nextAbs.id)
                        now = System.currentTimeMillis()
                        Log.d(TAG, "PlayBlendShape smoothReadyTime: $smoothReadyTime now: $now duration: ${now - smoothReadyTime}")
                    }
                    Log.d(TAG, "PlayBlendShape listen marker: $nextSize")
                    audioChunksPlayer?.setMarkerSizeListener(nextSize) {
                        markerCompleteTime[nextSize] = System.currentTimeMillis()
                        MainScope().launch {
                            waitingAudioCompleteMap[nextSize]?.invoke()
                            waitingAudioCompleteMap.remove(nextSize)
                        }
                        Log.d(TAG, "PlayBlendShape marker reached: ${nextAbs.id}")
                    }
                    Log.d(TAG, "PlayBlendShape startPlayAudio: ${nextAbs.id}")
                    audioChunksPlayer?.playChunk(nextAbs.audio)
                    Log.d(TAG, "PlayBlendShape end: ${nextAbs.id}")
                }
                if (nextAbs.is_last || nextAbs.id == lastId) {
                    Log.d(TAG, "PlayBlendShape is last: ${nextAbs.id}")
                    waitAudioComplete(audioChunksPlayer!!.currentSize())
                    Log.d(TAG, "PlayBlendShape last wait complete ${nextAbs.id}")
                    withContext(Dispatchers.Main) {
                        this@AudioBlendShapePlayer.stop()
                    }
                    Log.d(TAG, "PlayBlendShape: stopped")
                    return
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "processAudioBlendShapes failed", e)
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    // >>>>>>>>>>>>>>>>>>>>>>>>>>>> add for speech optimization start
    /** 处理剩余未完成的句子 */
    private fun flushPendingText() {
        val remaining = pendingText.toString().trim()
        if (remaining.isNotEmpty() && remaining.length >= MIN_SENTENCE_LENGTH) {
            playText(remaining, nextSegmentId++, true)
        }
        pendingText.clear()
    }

    /** 强制拆分过长文本（无结束符时） */
    private fun splitAndPlay(text: String, forceSplit: Boolean) {
        if (!forceSplit) return
        val splitIndex = minOf(MAX_SENTENCE_LENGTH, text.length)
        val sentence = text.substring(0, splitIndex)
        playText(sentence, nextSegmentId++, false)

        // 剩余文本继续缓存
        pendingText.clear()
        pendingText.append(text.substring(splitIndex))
    }
    /** 处理缓存中的文本，按句子结束符拆分 */
    private fun processPendingSentences() {
        val text = pendingText.toString()
        if (text.isEmpty()) return

        // 查找所有句子结束符的位置
        val delimiterMatches = SENTENCE_DELIMITERS.findAll(text)
        if (delimiterMatches.none()) {
            // 无结束符但文本过长：强制分段
            if (text.length >= MAX_SENTENCE_LENGTH) {
                splitAndPlay(text, forceSplit = true)
            }
            return
        }

        // 按结束符拆分并播放完整句子
        var lastEnd = 0
        delimiterMatches.forEach { match ->
            val sentenceEnd = match.range.last + 1 // 包含结束符本身
            val sentence = text.substring(lastEnd, sentenceEnd)
            lastEnd = sentenceEnd

            // 过滤过短句子（避免单个字符）
            if (sentence.length >= MIN_SENTENCE_LENGTH) {
                playText(sentence, nextSegmentId++, false)
            }
        }

        // 保留未处理的剩余文本（未到句子结束符）
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
            // 处理最终剩余文本
            flushPendingText()
            return
        }

        // 1. 清洗文本：移除特殊字符、替换换行为空格
        val cleanedText = currentText
            .replace(SPECIAL_CHARS, "") // 过滤不朗读的特殊字符
            .replace("\n", " ") // 换行替换为空格，避免中断
            .trim()

        if (cleanedText.isEmpty()) return

        // 2. 计算增量文本（只处理新增内容）
        val addedText = if (cleanedText.startsWith(lastProcessedFullText)) {
            cleanedText.substring(lastProcessedFullText.length)
        } else {
            Log.w(TAG, "文本不匹配，可能服务端重置了回复")
            cleanedText // 全量处理新文本
        }

        if (addedText.isEmpty()) return

        // 3. 更新已处理文本记录
        lastProcessedFullText = cleanedText

        // 4. 追加到待处理缓存
        pendingText.append(addedText)

        // 5. 按句子结束符分段处理
        processPendingSentences()
    }

    fun playText(text:String, id: Int, is_last:Boolean) {
        if (DebugModule.DEBUG_DISABLE_A2BS) {
            sessionScope?.launch {
                withContext(Dispatchers.Default) {
                    ttsService.processSherpa(text, id)
                }
            }
            Log.d(TAG, "stop..")
            return
        }

        // 确认文本干净
        val finalText = text.replace(SPECIAL_CHARS, "").trim()
        if (finalText.isEmpty()) {
            Log.d(TAG, "过滤后文本为空，跳过播放: $text")
            return
        }

        Log.d(TAG, "playText: ${finalText} id: ${id} isEnd:${is_last}")
        val audioBlendShape = AudioBlendShape(
            id,
            is_last,
            finalText,
            ShortArray(0),
            null,
            AudioToBlendShapeData())
        sessionScope?.launch {
            val processTtsStartTime = System.currentTimeMillis()
            Log.d(TAG, "processTextInner TTS begin $id $finalText begin")
            ensureActive()
            ttsService.setCurrentIndex(audioBlendShape.id)
            var audioData = ShortArray(0)
            if (ttsService.useSherpaTts) {
                val generatedAudio = ttsService.processSherpa(audioBlendShape.text, audioBlendShape.id)
                if (generatedAudio != null) {
                    audioChunksPlayer?.sampleRate = generatedAudio.sampleRate
                    audioData =  audioChunksPlayer?.convertToShortArray(generatedAudio.samples)!!
                }
            } else {
                audioChunksPlayer?.sampleRate = 44100
                audioData = ttsService.process(audioBlendShape.text, audioBlendShape.id)
            }
            if (audioData.isEmpty()) {
                lastId = id -1
                Log.d(TAG, "processTextInner: $id $finalText audioData is empty")
                return@launch
            }
            audioBlendShape.audio = audioData
            ensureActive()
            val processTtsEndTime = System.currentTimeMillis()
            val processTtsDuration = processTtsEndTime - processTtsStartTime
            val rtf = processTtsDuration.toFloat()/ 1000 / (audioData.size / audioChunksPlayer!!.sampleRate.toFloat())
            Log.d(TAG, "processTextInner TTS  $id $finalText end duration: $processTtsDuration rtf: $rtf")
            val a2bsData = a2bsService.process(audioBlendShape.id, audioData, audioChunksPlayer?.sampleRate!!)
            Log.d(TAG, "processTextInner A2BS  $id  end duration: ${System.currentTimeMillis() - processTtsEndTime}")
            audioBlendShape.a2bs = a2bsData
            ensureActive()
            Log.d(TAG, "processTextInner: $id $finalText end")
            addAudioBlendShape(audioBlendShape)
            readyTimeMap[audioBlendShape.id] = System.currentTimeMillis()
        }
    }
    // <<<<<<<<<<<<<<<<<<<<<<<<<<<< add for speech optimization end

    private suspend fun addAudioBlendShape(audioBlendShape: AudioBlendShape) {
        totalAudioBsFrame += audioBlendShape.a2bs.frame_num
        Log.d(TAG, "AddBlendShape: ${audioBlendShape.id} " +
                "totalAudioBsFrame: ${totalAudioBsFrame} " +
                "text: ${audioBlendShape.text} " +
                "a2bs size: ${audioBlendShape.a2bs.frame_num} " +
                "audio size: ${audioBlendShape.audio.size}" +
                "per_frame: ${if(audioBlendShape.a2bs.frame_num > 0)
                    audioBlendShape.audio.size / audioBlendShape.a2bs.frame_num else
                    0}")
        audioBlendShapeMap[audioBlendShape.id] = audioBlendShape
        waitingBlendShapeMap[audioBlendShape.id]?.invoke()
        waitingBlendShapeMap.remove(audioBlendShape.id)
    }

    val currentTime: Long
        get() {
            return audioChunksPlayer?.currentTime()?:0L
        }

    val totalTime: Long
        get() = audioChunksPlayer?.totalTime()?:0L

    val isPlaying: Boolean
        get() = audioChunksPlayer?.isPlaying?:false && !stopped

    val currentHeadPosition:Int
        get() = audioChunksPlayer?.currentHeadPosition()?:0

    val currentPlayingText:String
        get() {
            if (!isPlaying) {
                return ""
            }
            val currentPosition = audioChunksPlayer?.currentHeadPosition()?:0
            for (i in audioMarkerMap.keys) {
                if (audioMarkerMap[i]!! <= currentPosition && audioMarkerMap.containsKey(i + 1) && currentPosition <= audioMarkerMap[i + 1]!!) {
                    return audioBlendShapeMap[i]?.text ?: ""
                }
            }
            return ""
        }

    val isBuffering:Boolean
        get() {
            return audioMarkerMapReverse.containsKey(audioChunksPlayer?.currentHeadPosition())
        }

    fun stop() {
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
        waitingSmoothReadyMap.clear()
        listeners.forEach{
            it.onPlayEnd()
        }
    }

    fun playSession(sessionId: Long, texts: List<String>) {
        startNewSession(sessionId)
        texts.forEachIndexed { index, text ->
            this.playText(text, index, index == texts.lastIndex)
        }
    }

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
        val now = System.currentTimeMillis()
        LogUtils.v(TAG, "update: ${playingStatus.currentAudioPosition} " +
                "nextPlaySegmentId: ${playingStatus.nextPlaySegmentId} " +
                "isBuffering: ${playingStatus.isBuffering} " +
                "markerCompleteTime: $markerCompleteTime" +
                "currentHeadPosition : ${currentHeadPosition}" +
                "audioMarkerMapReverse: ${audioMarkerMapReverse}"
        )
        if (playingStatus.isBuffering) {
            playingStatus.nextPlaySegmentId = audioMarkerMapReverse[currentHeadPosition]!!
            val nextReady = readyTimeMap.containsKey(playingStatus.nextPlaySegmentId)
            val previousComplete = markerCompleteTime.containsKey(currentHeadPosition) || currentHeadPosition == 0
            LogUtils.v(TAG, "update previousComplete:${previousComplete}  " +
                    "percent is : ${playingStatus.smoothToIdlePercent} nextReady:${nextReady} " +
                    "smoothToTalkStartTime : ${playingStatus.smoothToTalkStartTime} " +
                    "smoothToTalkStartTime < 0 : ${playingStatus.smoothToTalkStartTime < 0}")
            if (previousComplete && playingStatus.smoothToIdlePercent < 1.0f && playingStatus.nextPlaySegmentId > 0) {
                playingStatus.smoothToTalkPercent = -1f
                val lastPlayEndTime = markerCompleteTime[playingStatus.currentAudioPosition]!!
                playingStatus.smoothToIdlePercent = if(now - lastPlayEndTime > CONFIG_SMOOTH_DURATION) {
                    1.0f
                } else {
                    ((now - lastPlayEndTime).toFloat() / CONFIG_SMOOTH_DURATION)
                }
                LogUtils.v(TAG, "update previousComplete now: $now after  ${now - lastPlayEndTime} percent is : ${playingStatus.smoothToIdlePercent}")
            } else if (nextReady && previousComplete) {
                if (CONFIG_NEED_SMOOTH_TO_CHAT) {
                    if (playingStatus.smoothToTalkStartTime < 0) {
                        playingStatus.smoothToTalkStartTime = now
                        Log.v(TAG, "update nextReady after Ready reset smoothToTalkStartTime: ${playingStatus.smoothToTalkStartTime} " +
                                "smoothToTalkPercent is : ${playingStatus.smoothToTalkPercent}")
                    }
                    val elapsedTime = now - playingStatus.smoothToTalkStartTime
                    playingStatus.smoothToIdlePercent = 1.0f
                    playingStatus.smoothToTalkPercent = if(elapsedTime >= CONFIG_CHAT_SMOOTH_DURATION) {
                        1.0f
                    } else {
                        elapsedTime.toFloat() / CONFIG_CHAT_SMOOTH_DURATION
                    }
                }
                if ((!CONFIG_NEED_SMOOTH_TO_CHAT || playingStatus.smoothToTalkPercent >= 1.0f) && waitingSmoothReadyMap.containsKey(playingStatus.nextPlaySegmentId)) {
                    smoothReadyMap[playingStatus.nextPlaySegmentId] = now
                    val smoothReadySuspend = waitingSmoothReadyMap[playingStatus.nextPlaySegmentId]
                    waitingSmoothReadyMap.remove(playingStatus.nextPlaySegmentId)
                    Log.d(TAG, "update mark smoothReady: ${playingStatus.nextPlaySegmentId}")
                    MainScope().launch {
                        smoothReadySuspend?.invoke()
                    }
                }
            } else {
                LogUtils.v(TAG, "update previous not complete")
            }
            LogUtils.v(TAG, "update end: ${playingStatus.currentAudioPosition} " +
                    "nextPlaySegmentId: ${playingStatus.nextPlaySegmentId} " +
                    "isBuffering: ${playingStatus.isBuffering} " +
                    "smoothToIdlePercent: ${playingStatus.smoothToIdlePercent} smoothToTalkPercent: ${playingStatus.smoothToTalkPercent} " +
                    "nextReady: ${nextReady}")
        } else {
            LogUtils.v(TAG, "update reset")
            playingStatus.smoothToTalkStartTime = -1
            playingStatus.smoothToIdlePercent = -1f
            playingStatus.smoothToTalkPercent = -1f
        }

        return playingStatus
    }


    interface Listener {
        fun onPlayStart()
        fun onPlayEnd()
    }
}