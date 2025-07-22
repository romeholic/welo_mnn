// Created by ruoyi.sjd on 2025/3/12.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.asr

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.mnn.OnlineCtcFstDecoderConfig
import com.k2fsa.sherpa.mnn.OnlineRecognizer
import com.k2fsa.sherpa.mnn.OnlineRecognizerConfig
import com.k2fsa.sherpa.mnn.getEndpointConfig
import com.k2fsa.sherpa.mnn.getFeatureConfig
import com.k2fsa.sherpa.mnn.getModelConfig
import com.k2fsa.sherpa.mnn.getOnlineLMConfig
import com.taobao.meta.avatar.record.RecordPermission.REQUEST_RECORD_AUDIO_PERMISSION
import com.taobao.meta.avatar.utils.DeviceUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class RecognizeService(private val activity: Activity) {

    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    companion object {
        const val TAG = "WELO#OnlineRecorder"
    }

    private val initComplete = CompletableDeferred<Boolean>()
    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val isRecording = AtomicBoolean(false)
    @Volatile
    private var isLoaded = false
    var onRecognizeText: ((String) -> Unit)? = null

    private val acceptTimeNs = AtomicLong(0)
    private val decodeTimeNs = AtomicLong(0)
    private val chunkCount = AtomicLong(0)
    private val decodeCount = AtomicLong(0)

    private var utteranceProcTimeNs: Long = 0
    private var utteranceAudioTimeSec: Double = 0.0
    private var totalRtf: Double = 0.0
    private var utteranceCount: Int = 0

    suspend fun initRecognizer() {
        val type = if (DeviceUtils.isChinese) 0 else 1
        Log.i(TAG, "Select model type $type")
        val config = getModelConfig(type)?.let {
            OnlineRecognizerConfig(
                getFeatureConfig(sampleRateInHz, 80),
                it,
                getOnlineLMConfig(type),
                OnlineCtcFstDecoderConfig("", 3000),
                getEndpointConfig(),
                true,
                "greedy_search",
                4,
                "",
                1.5f,
                "", ""
            )
        }!!
        CoroutineScope(Dispatchers.IO).async {
            recognizer = OnlineRecognizer(null, config)
        }.await()
        isLoaded = true
        initComplete.complete(true)
    }

    private fun initMicrophone(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }
        val numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        Log.i(TAG, "buffer size in milliseconds: ${numBytes * 1000.0f / sampleRateInHz}")
        audioRecord = AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, numBytes * 2)
        return true
    }

    private fun processSamples() {
        Log.i(TAG, "processing samples")
        // 提前检查必要条件，快速返回
        val currentRecognizer = recognizer ?: return
        val currentAudioRecord = audioRecord ?: return
        if (!isRecording.get()) return

        val stream = currentRecognizer.createStream("")
        val interval = 0.05 // 50ms的间隔，比原来更频繁处理小批量数据
        val bufferSize = (interval * sampleRateInHz).toInt()
        val buffer = ShortArray(bufferSize)
        var utteranceStartTimeNs = 0L

        // 预计算尾部填充数组，避免重复创建
        val tailPaddingSize = (0.3 * sampleRateInHz).toInt()
        val tailPaddings = FloatArray(tailPaddingSize)

        // 复用FloatArray，减少对象创建
        val samples = FloatArray(bufferSize)

        while (isRecording.get()) {
            val ret = currentAudioRecord.read(buffer, 0, buffer.size)
            if (ret <= 0) {
                // 处理读取错误或结束
                if (ret == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "Invalid audio record operation")
                    break
                }
                continue
            }

            // 记录语音片段开始时间
            if (utteranceProcTimeNs == 0L) {
                utteranceStartTimeNs = System.nanoTime()
            }

            chunkCount.incrementAndGet()

            // 优化：直接转换而不使用lambda，减少函数调用开销
            for (i in 0 until ret) {
                samples[i] = buffer[i] / 32768.0f
            }

            utteranceAudioTimeSec += ret.toDouble() / sampleRateInHz
            val tStartProc = System.nanoTime()

            // 处理音频波形
            val tAcceptStart = tStartProc
            stream.acceptWaveform(samples, sampleRateInHz)
            acceptTimeNs.addAndGet(System.nanoTime() - tAcceptStart)

            // 动态调整最大解码次数，根据处理速度自适应
            var maxDecodesPerCycle = if (utteranceProcTimeNs > utteranceAudioTimeSec * 1e9) 1 else 2

            // 解码处理
            while (currentRecognizer.isReady(stream) && maxDecodesPerCycle-- > 0) {
                val tDecodeStart = System.nanoTime()
                currentRecognizer.decode(stream)
                decodeTimeNs.addAndGet(System.nanoTime() - tDecodeStart)
                decodeCount.incrementAndGet()
            }

            utteranceProcTimeNs += System.nanoTime() - tStartProc

            // 检查是否到达端点
            val isEndpoint = currentRecognizer.isEndpoint(stream)
            var text = currentRecognizer.getResult(stream).text

            if (isEndpoint && currentRecognizer.config.modelConfig.paraformer.encoder.isNotEmpty()) {
                // 使用预创建的尾部填充数组
                stream.acceptWaveform(tailPaddings, sampleRateInHz)

                // 最后一次解码，不限制次数以确保结果完整
                var finalDecodeCount = 0
                while (currentRecognizer.isReady(stream) && finalDecodeCount < 3) { // 限制最大3次避免无限循环
                    currentRecognizer.decode(stream)
                    finalDecodeCount++
                }
                text = currentRecognizer.getResult(stream).text
            }

            if (isEndpoint) {
                // 计算处理时间和RTF
                val T_proc = utteranceProcTimeNs / 1_000_000_000.0
                val T_audio = utteranceAudioTimeSec
                val rtf = if (T_audio > 0) T_proc / T_audio else 0.0
                totalRtf += rtf
                utteranceCount++

                // 计算总耗时并回调结果
                val totalProcessingTimeMs = (System.nanoTime() - utteranceStartTimeNs) / 1_000_000.0
                Log.i(TAG, "processSamples - 文本: '$text', 总耗时: ${"%.2f".format(totalProcessingTimeMs)}ms, RTF: ${"%.3f".format(rtf)}")

                // 重置状态准备下一段语音
                currentRecognizer.reset(stream)
                onRecognizeText?.invoke(text)
                utteranceProcTimeNs = 0
                utteranceAudioTimeSec = 0.0
            }
        }

        // 释放资源并输出统计信息
        stream.release()

        val totalChunks = chunkCount.get().coerceAtLeast(1)
        val totalDecodes = decodeCount.get().coerceAtLeast(1)
        val avgAcceptMs = acceptTimeNs.get() / totalChunks / 1_000_000.0
        val avgDecodeMs = decodeTimeNs.get() / totalDecodes / 1_000_000.0

        Log.i(TAG, "平均acceptWaveform: ${"%.2f".format(avgAcceptMs)} ms ($totalChunks 次)")
        Log.i(TAG, "平均decode: ${"%.2f".format(avgDecodeMs)} ms ($totalDecodes 次)")

        if (utteranceCount > 0) {
            val avgRtf = totalRtf / utteranceCount
            Log.i(TAG, "平均RTF ($utteranceCount 段语音): ${"%.3f".format(avgRtf)}")
        }

        // 重置统计变量
        acceptTimeNs.set(0)
        decodeTimeNs.set(0)
        chunkCount.set(0)
        decodeCount.set(0)
        totalRtf = 0.0
        utteranceCount = 0
    }

    fun stopRecord() {
        Log.i(TAG, "stopRecord isRecording: ${isRecording.get()}")
        if (!isRecording.get()) {
            return
        }
        isRecording.set(false)
        if (audioRecord != null) {
            audioRecord!!.stop()
            audioRecord!!.release()
            audioRecord = null
            Log.i(TAG, "Stopped recording")
        }
    }

    fun startRecord() {
        if (isRecording.get()) {
            return
        }
        val ret = initMicrophone()
        if (!ret) {
            Log.e(TAG, "Failed to initialize microphone")
            return
        }
        audioRecord?.let {
            Log.i(TAG, "state: ${it.state}")
            it.startRecording()
            isRecording.set(true)
            recordingThread = Thread { this.processSamples() }
            recordingThread!!.start()
            Log.i(TAG, "Started recording")
        }
    }
}