package com.welo.launcher.asr

import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import com.welo.util.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

class ASRManager(private val recognizerText: (resultText: String) -> Unit) {

    companion object{
        private const val TAG = "ASRManager"
    }
    private lateinit var mAsr: ASR

    private var isDws = false
    private var isRun = false

    private var startMode = "AUDIO"

    private var language = "zh_cn"
    private var count: Int = 0

    private var audioRecorderManager: AudioRecorderManager? = null
    private val isWrite = AtomicBoolean(false)

    private val audioDataCallback = object : AudioRecorderManager.AudioDataCallback {
        override fun onAudioData(data: ByteArray?, size: Int) {
            if (isWrite.get()) {
                val ret = mAsr.write(data)
                if (ret != 0) {
                    isWrite.set(false)
                }
            }
        }

        override fun onAudioVolume(db: Double, volume: Int) {
        }
    }
    private val asrCallBack = object : AsrCallbacks {
        override fun onResult(asrResult: ASR.ASRResult?, p1: Any?) {
            asrResult?.let {
                val begin = it.begin
                val end = it.end
                val status = it.status
                val result = it.bestMatchText
                val sid = it.sid
                val vadList = it.vads
                val transcriptions = it.transcriptions

                var vadBegin = -1
                var vadRnd = -1
                var word: String? = null
                for (vad in vadList) {
                    vadBegin = vad.begin
                    vadRnd = vad.end //VAD结果
                }
                for (transcription in transcriptions) {
                    val segments = transcription.segments
                    for (segment in segments) {
                        word = segment.text //分词结果
                    }
                }
                val info = "result={begin:$begin,end:$end,status:$status,result:$result,sid:$sid}"
                LogUtil.d(TAG, "onResult: ASR result:$info")

                when (status) {
                    0 -> {
                        if (isDws) {
                            LogUtil.d(TAG, "onResult: DWS result:$result")
                        }
                    }

                    2 -> {
                        if (isDws) {
                            LogUtil.d(TAG, "onResult: DWS end")
                            recognizerText.invoke(result)
                        } else {
                            showInfo(result + "\n")
                        }
                        stopAsr()
                    }

                    else -> {
                        if (isDws){
                            LogUtil.d(TAG, "onResult: DWS end")
                        } else {
                            showInfo(result + "\n")
                        }
                    }
                }
                toEnd()
            }
        }

        override fun onError(asrError: ASR.ASRError?, p1: Any?) {
            val code = asrError!!.code
            val msg = asrError.errMsg
            val sid = asrError.sid
            val info = "error={code:$code,msg:$msg,sid:$sid}"
            LogUtil.d(TAG, info)
            showInfo("识别出错!错误码：$code,错误信息：$msg,sid:$sid\n")
            stopAsr()
            isRun = false
        }
    }

    private fun initASR() {
        mAsr = ASR()
        mAsr.registerCallbacks(asrCallBack)
        LogUtil.d(TAG, "initASR: ASR init success.")
    }

    fun runAsrAudio() {
        if (isRun) {
            showInfo("正在识别中，请勿重复开启。\n")
            return
        }
        if (!::mAsr.isInitialized) {
            initASR()
        }
        isDws = false
        mAsr.language(language)
        mAsr.domain("slm")
        mAsr.accent("mandarin")
        mAsr.vinfo(true)
        if ("zh_cn" == language) {
            mAsr.dwa("wpgs") //动态修正
            isDws = true
        }

        count++
        val ret = mAsr.start(count)
        if (ret != 0) {
            isRun = false
            showInfo("识别开启失败，错误码:$ret\n")
        } else {
            isRun = true
            isWrite.set(true)
            startMode = "AUDIO"
            if (audioRecorderManager == null) {
                audioRecorderManager = AudioRecorderManager.getInstance()
            }
            audioRecorderManager!!.startRecord()
            audioRecorderManager!!.registerCallBack(audioDataCallback)
        }
    }

    private fun toEnd() {
        //结束
    }

    fun stopAsr() {
        LogUtil.d(TAG, "stopAsr: isRun:$isRun,startMode:$startMode")
        if (isRun) {
            if ("AUDIO" == startMode) {
                if (audioRecorderManager != null) {
                    audioRecorderManager!!.stopRecord()
                    audioRecorderManager = null
                }
                mAsr.stop(false)
                showInfo("\n已停止录音。\n")
            } else {
                mAsr.stop(true) //取消。true:立即结束，false:等大模型返回最后一帧数据后结束
                showInfo("\n已停止识别。\n")
            }
            startMode = "NONE"
            isRun = false
        }
    }

    private fun showInfo(info: String) {
        LogUtil.d(TAG, info)
//        Toast.makeText(WeLoApplication.getInstance(), info, Toast.LENGTH_SHORT).show()
    }
}