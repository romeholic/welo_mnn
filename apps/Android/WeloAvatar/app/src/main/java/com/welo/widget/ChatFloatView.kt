package com.welo.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.WeLoApplication
import com.alibaba.mnnllm.android.utils.FileUtils
import com.taobao.meta.avatar.MHConfig
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.download.DownloadCallback
import com.taobao.meta.avatar.download.DownloadModule
import com.taobao.meta.avatar.record.RecordPermission
import com.taobao.meta.avatar.record.RecordPermission.REQUEST_RECORD_AUDIO_PERMISSION
import com.taobao.meta.avatar.tts.TtsService
import com.welo.base.net.TextStreamResponse
import com.welo.base.observeInLifecycleWithDelay
import com.welo.callback.IChat
import com.welo.login.LoginActivity
import com.welo.service.RecordingForegroundService
import com.welo.service.ServiceInitializer
import com.welo.util.AudioPlayerUtil
import com.welo.util.LogUtil
import com.welo.util.ScreenState
import com.welo.viewmodel.MessageViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File

@RequiresApi(Build.VERSION_CODES.Q)
class ChatFloatView(
    private val context: Context,
    private val serviceInitializer: ServiceInitializer
) : FloatingView(context), LifecycleOwner, DownloadCallback, IChat {
    private var answerSession = System.currentTimeMillis()
    private val initComplete = CompletableDeferred<Boolean>()
    private var serviceInitializing = false

    // 声明ViewModel
    private lateinit var messageViewModel: MessageViewModel
    private lateinit var recognizeService: RecognizeService
    private lateinit var audioBlendShapePlayer: AudioPlayerUtil
    private lateinit var downloadManager: DownloadModule
    private lateinit var ttsService: TtsService

    private val lifecycleRegistry = LifecycleRegistry(this).also {
        it.currentState = Lifecycle.State.STARTED
    }


    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    // 协程作用域（与悬浮窗生命周期绑定）
    private val scope: LifecycleCoroutineScope
        get() = lifecycleScope

    init {
        // 标记悬浮窗生命周期为STARTED（开始收集数据）
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        chatCallback = this
        initServices()
        setupDownloadManager()
    }

    private fun initServices() {
        ttsService = TtsService()
        recognizeService = serviceInitializer.getRecognizeService()
        audioBlendShapePlayer = serviceInitializer.getAudioPlayer(ttsService)
        // 直接使用服务
        recognizeService.onRecognizeText = { text ->
            // 处理识别结果
            runOnUiThreadIfNeeded {
                if (text.isEmpty()) {
                    stopRecord()
                    return@runOnUiThreadIfNeeded
                }
                if (text.isNotEmpty()) {
                    stopRecord()
                    // 处理识别到的文本
                    processChatRequest(text)
                }
            }
        }
        audioBlendShapePlayer.addListener(object : AudioPlayerUtil.Listener {
            override fun onPlayStart() {
            }

            override fun onPlayEnd() {
            }
        })
    }

    private fun setupDownloadManager() {
        downloadManager = DownloadModule(serviceInitializer.getContext() as Activity)
        downloadManager.setDownloadCallback(this)
        MHConfig.BASE_DIR = downloadManager.getDownloadPath()
        if (downloadManager.isDownloadComplete()) {
            scope.launch {
                setupServices()
            }
        } else {
            onDownloadClicked()
        }
    }

    // 接收Service传递的ViewModel
    fun initWithViewModel(viewModel: MessageViewModel) {
        this.messageViewModel = viewModel
        // 初始化数据流观察
        observeViewModel()
    }

    private fun onDownloadClicked() {
        scope.launch {
            downloadManager.download()
        }
    }

    // 观察ViewModel的数据变化
    private fun observeViewModel() {
        // 观察AI响应状态（加载中、错误等）
        scope.launch {
            messageViewModel.aiResponseFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { state ->
                    runOnUiThreadIfNeeded {

                        when (state) {
                            is TextStreamResponse.Connecting -> {
                                LogUtil.d(TAG, "AI response connecting")
                                // 显示加载状态（例如修改悬浮窗按钮图标）
                                stateChange(ScreenState.Loading)
                            }

                            is TextStreamResponse.Error -> {
                                LogUtil.d(TAG, "AI response error: ${state.message}")
                                if (state.message.contains("405")) {
                                    val intent = Intent(context, LoginActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } else {
                                    // 其他错误处理
                                    addChatOutput("错误：${state.message}")
                                }
                                // 显示错误信息
                                stateChange(ScreenState.Idle)
                            }

                            is TextStreamResponse.Cancelled -> {
                                LogUtil.d(TAG, "AI response Cancelled")
                                stateChange(ScreenState.StopRequest)
                            }

                            is TextStreamResponse.Idle, TextStreamResponse.Completed -> {
                                LogUtil.d(TAG, "AI response  idle or Completed")
                                stateChange(ScreenState.Idle)
                            }

                            is TextStreamResponse.Connected -> {
                                LogUtil.d(TAG, "AI response connected")
                            }

                            is TextStreamResponse.Data -> {
                                audioBlendShapePlayer.playStreamText(state.text)
                            }
                        }
                    }
                }
            messageViewModel.requestId.observeInLifecycleWithDelay(this@ChatFloatView) {
                LogUtil.d(TAG, "Received requestId: $it")
            }
        }
        scope.launch {
            messageViewModel.collectedText.observeInLifecycleWithDelay(this@ChatFloatView, 10) {
                LogUtil.d(TAG, "Received requestId: $it")
                runOnUiThreadIfNeeded {
                    if (it.isNotEmpty()) {
                        // 显示AI回复内容
                        addChatOutput(it)
                    }
                }
            }
        }
    }

    // 语音转文本，发起请求
    private fun processChatRequest(text: String) {
        currentChatId = System.currentTimeMillis()
        answerSession++
        audioBlendShapePlayer.startNewSession(answerSession)
        scope.launch {
            messageViewModel.sendMessage(text)
            hideChatOutput()
            messageViewModel.receivedMessage(text, currentChatId)
        }
    }

    private suspend fun setupServices() {
        if (initComplete.isCompleted) return
        if (serviceInitializing) {
            initComplete.await()
        }
        serviceInitializing = true
        scope.async {
            // Other service initialization tasks
            val taskTTS = async {
                Log.i(TAG, "Task TTS init begin")
                val startTimeTTS = System.currentTimeMillis()
                loadTTSModel()
                Log.i(TAG, "Task TTS completed in ${System.currentTimeMillis() - startTimeTTS} ms")
            }
            val taskRecognize = async {
                val startTimeLLM = System.currentTimeMillis()
                setupRecognizeService()
                Log.i(
                    TAG,
                    "Task Recognize completed in ${System.currentTimeMillis() - startTimeLLM} ms"
                )
            }
            awaitAll(taskTTS, taskRecognize)
        }.await()
        initComplete.complete(true)
        checkRecordAudioPermission()
    }

    private suspend fun loadTTSModel() {
        ttsService.init(MHConfig.TTS_MODEL_DIR)
    }

    private suspend fun setupRecognizeService() {
        recognizeService.initRecognizer()
    }

    private fun checkRecordAudioPermission() {
        if (ActivityCompat.checkSelfPermission(
                serviceInitializer.getContext(),
                RecordPermission.permissions[0]
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showWelcomeMessage()
        } else {
            ActivityCompat.requestPermissions(
                serviceInitializer.getContext() as Activity,
                RecordPermission.permissions,
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun showWelcomeMessage() {
        scope.launch {
            delay(2000)
            val welcomeText = context.getString(R.string.llm_welcome_text)
            ensureActive()
            audioBlendShapePlayer.playSession(answerSession, welcomeText.split("[,，]"))
        }
    }

    override fun startRecord() {
        val isInForeground = WeLoApplication.getInstance().isAppInForeground()
        LogUtil.d(TAG, "App in foreground: $isInForeground")
        if (!isInForeground) {
            runCatching {
                val intent = Intent(context, RecordingForegroundService::class.java)
                context.startForegroundService(intent)
                LogUtil.d(TAG, "Started RecordingForegroundService")
            }.onFailure {
                LogUtil.d(TAG, "Failed to start RecordingForegroundService: ${it.message}")
            }

        }
        // 开始语音识别
        recognizeService.startRecord()
        stateChange(ScreenState.VoiceInput)
    }

    override fun stopRecord() {
        // 停止语音识别
        recognizeService.stopRecord()
        stateChange(ScreenState.Idle)

        // 停止前台服务（后台场景下）
        if (!WeLoApplication.getInstance().isAppInForeground()) {
            context.stopService(Intent(context, RecordingForegroundService::class.java))
        }
    }

    override fun recognitionResult(inputText: String) {
        processChatRequest(inputText)
    }

    override fun stopAnswer() {
        audioBlendShapePlayer.stop()
    }

    override fun stopRequest() {
        messageViewModel.closeRequest(currentChatId.toString())
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    // 扩展函数确保UI操作在主线程
    fun runOnUiThreadIfNeeded(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    override fun onDownloadStart() {
        LogUtil.d(TAG, "Download started")
    }

    override fun onDownloadProgress(
        progress: Double,
        currentBytes: Long,
        totalBytes: Long,
        speedInfo: String
    ) {
        val string = context.resources.getString(
            R.string.download_progress,
            FileUtils.formatFileSize(currentBytes), FileUtils.formatFileSize(totalBytes), speedInfo
        )
        LogUtil.d(
            TAG,
            "Download string: $string, currentBytes: $currentBytes, totalBytes: $totalBytes, speedInfo: $speedInfo"
        )
    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        LogUtil.d(TAG, "Download complete: success=$success, file=$file")
        if (success) {
            scope.launch {
                setupServices()
            }
        }
    }

    override fun onDownloadError(error: Exception?) {
        LogUtil.e(TAG, "Download error: ${error?.message}")
    }

    companion object {
        private const val TAG = "ChatFloatView"
    }
}