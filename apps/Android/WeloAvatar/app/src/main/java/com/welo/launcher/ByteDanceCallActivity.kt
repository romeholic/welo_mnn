package com.welo.launcher

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.WeLoApplication
import com.airbnb.lottie.LottieAnimationView
import com.bytedance.speech.speechengine.SpeechEngine
import com.bytedance.speech.speechengine.SpeechEngine.SpeechListener
import com.bytedance.speech.speechengine.SpeechEngineDefines
import com.bytedance.speech.speechengine.SpeechEngineGenerator
import com.taobao.meta.avatar.R
import com.welo.util.LogUtil

import org.json.JSONObject
import java.util.LinkedList


class ByteDanceCallActivity : AppCompatActivity(), SpeechListener {

    private var isMicOn = true
    private var isSpeakerOn = true

    private lateinit var btn_end_call: LinearLayout
    private lateinit var btn_microphone: LinearLayout
    private lateinit var btn_speaker: LinearLayout
    private lateinit var tv_speaker_status: TextView
    private lateinit var tv_mic_status: TextView
    private lateinit var iv_mic: ImageView
    private lateinit var iv_scale_icon: ImageView
    private lateinit var iv_speaker: ImageView
    private lateinit var mainicon: LottieAnimationView
    private lateinit var mic_bg: View
    private lateinit var speaker_bg: View
    private lateinit var mSpeechEngine: SpeechEngine

    private var mStartEngineTimestamp: Long = -1

    private val PERMISSIONS_REQUEST_CODE = 1001
    private val MAX_DIALOG_MESSAGE_COUNT = 20
    private val REQUEST_CODE_OVERLAY_PERMISSION = 1001
    private val TAG = "ByteDanceCallActivity"

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
//        Manifest.permission.WRITE_EXTERNAL_STORAGE,
//        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE
    )

    private val mDialogMessages: LinkedList<Message> =
        LinkedList<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        setupViews()
        checkAndRequestPermissions()
        switchStatus(ActivityStatus.BEFORE_INIT)
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            Log.i(TAG, " permissions have beed granted,initEngine")
            initEngine()
            startEngine()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i(
                    TAG,
                    "checkAndRequestPermissions call back granted permission,initEngine"
                )
                initEngine()
            } else {
                Toast.makeText(this, "需要权限才能使用语音功能", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews() {
        mic_bg = findViewById(R.id.btn_microphone_bg)
        speaker_bg = findViewById(R.id.btn_speaker_bg)
        btn_end_call = findViewById(R.id.btn_end_call)
        btn_end_call.setOnClickListener(View.OnClickListener { v: View? -> endCall() })
        btn_speaker = findViewById(R.id.btn_speaker)
        btn_speaker.setOnClickListener(View.OnClickListener { v: View? -> toggleSpeaker() })
        btn_microphone = findViewById(R.id.btn_microphone)
        btn_microphone.setOnClickListener(View.OnClickListener { v: View? -> toggleMicrophone() })
        iv_scale_icon = findViewById(R.id.iv_scale_icon)
//        iv_scale_icon.setOnClickListener(View.OnClickListener { v: View? -> scaleIcon() })

        tv_speaker_status = findViewById(R.id.tv_speaker_status)
        tv_mic_status = findViewById(R.id.tv_mic_status)
        iv_mic = findViewById(R.id.iv_mic)
        iv_speaker = findViewById(R.id.iv_speaker)

        mainicon = findViewById(R.id.main_icon)
        mainicon.setAnimation("guide_0.json")
        mainicon.repeatCount = ValueAnimator.INFINITE
        mainicon.playAnimation()
        // 初始化按钮状态
        updateButtonStates()
    }

    private fun toggleMicrophone() {
        isMicOn = !isMicOn
        if (isMicOn) startEngine() else stopEngine()
        updateButtonStates()
        // 实际应用中这里应该调用音频API
        if (isMicOn) {
            mic_bg.setBackgroundResource(R.drawable.byte_mic_bg)
            Toast.makeText(this, "麦克风已打开", Toast.LENGTH_SHORT).show()
        } else {
            mic_bg.setBackgroundResource(R.drawable.byte_speakerbg)
            Toast.makeText(this, "麦克风已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerphoneEnabled(getApplicationContext())
        if (isSpeakerOn) enableSpeakerphone(getApplicationContext()) else disableSpeakerphone(
            getApplicationContext()
        )
        updateButtonStates()

        // 实际应用中这里应该调用音频API
        if (isSpeakerOn) {
            speaker_bg.setBackgroundResource(R.drawable.byte_mic_bg)
            Toast.makeText(this, "扬声器已打开", Toast.LENGTH_SHORT).show()
        } else {
            speaker_bg.setBackgroundResource(R.drawable.byte_speakerbg)
            Toast.makeText(this, "扬声器已关闭", Toast.LENGTH_SHORT).show()
        }
    }

//    private fun scaleIcon() {
//        if (WeLoApplication.floatingWindowManager.isActivityHidden(this)) {
//            WeLoApplication.floatingWindowManager.showActivity()
//        } else {
//            if (checkOverlayPermission()) {
//                WeLoApplication.floatingWindowManager.hideActivity(this)
//            } else {
//                requestOverlayPermission()
//            }
//            Settings.canDrawOverlays(this)
//        }
//    }

    private fun updateButtonStates() {
        // 更新麦克风状态
        val micIcon = if (isMicOn) R.drawable.byte_mic_on else R.drawable.byte_mic_off
        iv_mic.setImageResource(micIcon)
        tv_mic_status.text = if (isMicOn) "麦克风已开" else "麦克风已关"

        // 更新扬声器状态
        val speakerIcon =
            if (isSpeakerOn) R.drawable.byte_speaker_on else R.drawable.byte_speaker_off
        iv_speaker.setImageResource(speakerIcon)
        tv_speaker_status.text = if (isSpeakerOn) "扬声器已开" else "扬声器已关"
    }

    private fun endCall() {
        // 结束通话逻辑
        Toast.makeText(this, "通话结束", Toast.LENGTH_SHORT).show()
        finish()
    }


    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        uninitEngine()
    }

    private fun uninitEngine() {
        mSpeechEngine?.let { engine ->
            engine.destroyEngine()
            Log.i(TAG, "引擎析构完成!")
        }
    }

    private fun startEngine() {
        mStartEngineTimestamp = System.currentTimeMillis()
        mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_SYNC_STOP_ENGINE, "")
        val startJson: String = String.format(
            "{\"dialog\":{\"bot_name\":\"%s\"}}",
            "乐乐"
        )

        val ret = mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_START_ENGINE, startJson)
        Log.i(TAG, "DIRECTIVE_START_ENGINE,ret=$ret")
        if (ret == SpeechEngineDefines.ERR_NO_ERROR) {
            Log.i(TAG, "引擎启动成功")
            sayHello()
            switchStatus(ActivityStatus.RECORDING)
        } else if (ret == SpeechEngineDefines.ERR_REC_CHECK_ENVIRONMENT_FAILED) {
            Log.i(TAG, "引擎启动失败")
            checkAndRequestPermissions()
        }
    }

    private fun switchStatus(status: ActivityStatus) {
        runOnUiThread {
            when (status) {
                ActivityStatus.BEFORE_INIT -> {

                }

                ActivityStatus.INITING -> {

                }

                ActivityStatus.BEFORE_START -> {

                }

                ActivityStatus.RECORDING -> {

                }

                ActivityStatus.WAITING_RESULT -> {

                }
            }
        }
    }

    private fun initEngine() {
        Log.i(TAG, "initEngine")
        switchStatus(ActivityStatus.INITING)
        // create engine
        mSpeechEngine = SpeechEngineGenerator.getInstance()
        mSpeechEngine.createEngine()
        mSpeechEngine.setContext(getApplicationContext())

        // Common Options
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_LOG_LEVEL_STRING,
            SpeechEngineDefines.LOG_LEVEL_TRACE
        )

        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_ENGINE_NAME_STRING,
            SpeechEngineDefines.DIALOG_ENGINE
        )
        //【必需配置】鉴权相关：Appid
        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_APP_ID_STRING, "3714780548");
//【必需配置】鉴权相关：AppKey
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_APP_KEY_STRING,
            "PlgvMymc7f3tQnJ6"
        );
//【必需配置】鉴权相关：Token
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_APP_TOKEN_STRING,
            "TXrD1TqPwaR5XcGPuyGUYNE4dmdEG1EF"
        );
//【必需配置】对话服务资源信息ResourceId
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_RESOURCE_ID_STRING,
            "volc.speech.dialog"
        );
//【必需配置】User ID（用以辅助定位线上用户问题，如无法提供可提供固定字符串）
//        mSpeechEngine.setOptionString(SpeechEngineDefines.PARAMS_KEY_UID_STRING, "wll");
//【必需配置】对话服务域名
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_DIALOG_ADDRESS_STRING,
            "wss://openspeech.bytedance.com"
        )
//【必需配置】对话服务Uri
        mSpeechEngine.setOptionString(
            SpeechEngineDefines.PARAMS_KEY_DIALOG_URI_STRING,
            "/api/v3/realtime/dialogue"
        )
        //【可选配置】启用播放器音频回调，默认不启用
        mSpeechEngine.setOptionBoolean(
            SpeechEngineDefines.PARAMS_KEY_DIALOG_ENABLE_PLAYER_AUDIO_CALLBACK_BOOL,
            false
        )
        //【可选配置】启用录音机音频回调，默认不启用
        mSpeechEngine.setOptionBoolean(
            SpeechEngineDefines.PARAMS_KEY_DIALOG_ENABLE_RECORDER_AUDIO_CALLBACK_BOOL,
            false
        )
        // init engine
        val startInitTimestamp = System.currentTimeMillis()
        val ret = mSpeechEngine.initEngine()
        val cost = System.currentTimeMillis() - startInitTimestamp
        Log.i(TAG, "引擎初始化时间：$cost")
        Log.i(TAG, "引擎初始化结果：$ret")
        // init succ or not
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            switchStatus(ActivityStatus.BEFORE_INIT)
        } else {
            mSpeechEngine.setListener(this)
            switchStatus(ActivityStatus.BEFORE_START)
        }
    }


    override fun onSpeechMessage(type: Int, data: ByteArray, len: Int) {
        val strData = String(data)
        Log.e(
            TAG,
            "onSpeechMessage: type is " + type + " ,data: " + strData
        )
        when (type) {
            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_START -> {
                // Callback: 引擎启动成功回调
                Log.i(TAG, "Callback: 引擎启动成功: " + type)
                speechEngineStarted(strData)
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_STOP -> {
                // Callback: 引擎关闭回调
                Log.i(TAG, "Callback: 引擎关闭: " + type)
                speechEngineStopped(strData)
            }

            SpeechEngineDefines.MESSAGE_TYPE_ENGINE_ERROR -> {
                // Callback: 错误信息回调
                Log.e(
                    TAG,
                    "Callback: 错误信息: " + type + " data: " + strData
                )
                speechEngineError(strData)
            }

            SpeechEngineDefines.MESSAGE_TYPE_DIALOG_ASR_RESPONSE ->                 // Callback: ASR语音识别结果回调
                showUserMessage(strData)

            SpeechEngineDefines.MESSAGE_TYPE_DIALOG_ASR_ENDED ->                 // Callback: ASR语音识别结束
                confirmUserMessage()

            SpeechEngineDefines.MESSAGE_TYPE_DIALOG_CHAT_RESPONSE ->                 // Callback: Chat对话内容回调
                showAssistantMessage(strData)

            SpeechEngineDefines.MESSAGE_TYPE_DIALOG_ASR_INFO, SpeechEngineDefines.MESSAGE_TYPE_DIALOG_CHAT_ENDED ->                 // Callback: Chat对话内容结束
                confirmAssistantMessage()

            else -> {}
        }
    }

    private fun confirmAssistantMessage() {
        runOnUiThread({
            val message: Message? =
                lastUnconfirmedMessage(Role.ASSISTANT)
            if (message != null) {
                message.confirmed = true
            }
        })
    }

    private fun showAssistantMessage(data: String) {
        runOnUiThread({
            var message: Message? =
                lastUnconfirmedMessage(Role.ASSISTANT)
            if (message == null) {
                message = Message(
                    Role.ASSISTANT,
                    "",
                    false
                )
                mDialogMessages.addLast(message)
            }
            try {
                val reader = JSONObject(data)
                message.text += reader.getString("content")
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        })
    }

    private fun confirmUserMessage() {
        runOnUiThread({
            val message: Message? =
                lastUnconfirmedMessage(Role.USER)
            if (message != null) {
                message.confirmed = true
            }
        })
    }

    private fun showUserMessage(data: String) {
        runOnUiThread({
            var message: Message? =
                lastUnconfirmedMessage(Role.USER)
            if (message == null) {
                message = Message(
                    Role.USER,
                    "",
                    false
                )
                mDialogMessages.addLast(message)
            }
            try {
                val reader = JSONObject(data)
                message.text = reader.getJSONArray("results").getJSONObject(0).getString("text")
                Log.e(
                    TAG,
                    "onSpeechMessage message text:${message.text}"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            updateMessageUI()
        })
    }

    private fun updateMessageUI() {
        // 刷新消息内容
        if (mDialogMessages.size > MAX_DIALOG_MESSAGE_COUNT) {
            mDialogMessages.removeFirst()
        }

        // 构建消息内容
        val sb = StringBuilder()
        for (message in mDialogMessages) {
            var role = ""
            when (message.role) {
                Role.USER -> role = "[USER]:"
                Role.ASSISTANT -> role =
                    "[ASSISTANT]:"

                Role.LOG -> role = "[LOG]:"
            }
            sb.append(role).append(message.text).append("\n")
        }
        Log.e(
            TAG,
            "updateMessageUI,sb.toString():${sb.toString()}"
        )
//        mResultTv.setText(sb.toString())
//        // 滚动到底部
//        val scrollAmount: Int =
//            mResultTv.getLayout().getLineTop(mResultTv.getLineCount()) - mResultTv.getHeight()
//        if (scrollAmount > 0) {
//            mResultTv.scrollTo(0, scrollAmount)
//        } else {
//            mResultTv.scrollTo(0, 0)
//        }
    }

    private fun lastUnconfirmedMessage(role: Role?): Message? {
        var message: Message? = null
        val it: MutableIterator<Message> =
            mDialogMessages.descendingIterator()
        while (it.hasNext()) {
            val current: Message = it.next()
            if (current.role == role) {
                if (!current.confirmed) {
                    message = current
                }
                break
            }
        }
        return message
    }

    private fun speechEngineStarted(stdData: String?) {
        Log.i(TAG, "Engine onMsgStart")
    }

    private fun speechEngineStopped(stdData: String) {
        Log.i(TAG, "Engine onMsgStop")
//        val delay = System.currentTimeMillis() - mStartEngineTimestamp
//
//        val sessionId = stdData
//        val audioFilePath = "voiceconv_" + sessionId + ".wav"
//        setTextOnUiThread(
//            mResultText, ("Engine Stopped, cost: " + delay
//                    + "\nResult File: " + audioFilePath), true
//        )
//        if (mCurRecType == SpeechEngineDefines.RECORDER_TYPE_STREAM) {
//            mStreamRecorder!!.Stop()
//        }

        switchStatus(ActivityStatus.BEFORE_START)
    }

    private fun speechEngineError(stdData: String?) {
        Log.i(TAG, "Engine Error")
//        setTextOnUiThread(mResultText, stdData)
    }


    private fun setTextOnUiThread(textView: TextView, text: String?, append: Boolean) {
        runOnUiThread {
            // 在这里更新文本
            if (append) {
                textView.append("\n" + text)
            } else {
                textView.setText(text)
            }
        }
    }

    private fun sayHello() {
        // Directive：发送say_hello指令以播放开场白。
        val helloText = String.format("{\"content\": \"%s\"}", "我是你的AI助手，请问有什么可以帮你")
        val ret =
            mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_DIALOG_SAY_HELLO, helloText)
        Log.e(TAG, "ret =$ret")
        if (ret != SpeechEngineDefines.ERR_NO_ERROR) {
            stopEngine()
        } else {
        }
    }

    private fun stopEngine() {
        // Directive：关闭引擎，停止对话功能。
        Log.i(TAG, "Directive: DIRECTIVE_STOP_ENGINE")
        mSpeechEngine.sendDirective(SpeechEngineDefines.DIRECTIVE_STOP_ENGINE, "")
    }

    // 打开扬声器
    fun enableSpeakerphone(context: Context) {
        try {
            val audioManager: AudioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = true
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: SecurityException) {
            Log.e("AudioControl", "需要 MODIFY_AUDIO_SETTINGS 权限", e)
        }
    }

    // 关闭扬声器
    fun disableSpeakerphone(context: Context) {
        try {
            val audioManager: AudioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: SecurityException) {
            Log.e("AudioControl", "需要 MODIFY_AUDIO_SETTINGS 权限", e)
        }
    }

    // 检查当前扬声器状态
    fun isSpeakerphoneEnabled(context: Context): Boolean {
        val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        LogUtil.d("AudioControl","isSpeakerphoneOn =${audioManager.isSpeakerphoneOn}")
        return audioManager.isSpeakerphoneOn
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

//    private fun requestOverlayPermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val intent = Intent(
//                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                Uri.parse("package:$packageName")
//            )
//            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
//        }
//    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION && checkOverlayPermission()) {
//            WeLoApplication.floatingWindowManager.hideActivity(this)
//        }
//    }

    private enum class ActivityStatus {
        // activity status sequence from first(BEFORE_INIT) to last(AFTER_SUBMIT)
        BEFORE_INIT,
        INITING,
        BEFORE_START,
        RECORDING,
        WAITING_RESULT,
    }

    enum class Role {
        // 用户提问内容
        USER,

        // 助手机器人回复内容
        ASSISTANT,

        // 日志信息
        LOG,
    }

    class Message(
        role: Role,
        text: String?,
        confirmed: Boolean
    ) {
        lateinit var role: Role
        lateinit var text: String
        var confirmed: Boolean = confirmed

        init {
            this.role = role
            if (text != null) {
                this.text = text
            }
            this.confirmed = confirmed
        }
    }

}
