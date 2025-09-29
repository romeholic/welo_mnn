package com.welo.manager

import ResourceProvider
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import com.welo.callback.IChat
import com.welo.constant.Constants
import com.welo.service.RecordingForegroundService
import com.welo.util.AudioPlayerUtil
import com.welo.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

class ChatInteractionManager private constructor() : DownloadCallback, DefaultLifecycleObserver {
    companion object {
        private const val TAG = "ChatInteractionManager"
        private const val WELCOME_DELAY_MS = 1000L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ChatInteractionManager? = null

        fun getInstance(): ChatInteractionManager {
            return instance ?: synchronized(this) {
                instance ?: ChatInteractionManager().also { instance = it }
            }
        }
    }

    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private var serviceInitializing = false
    private val initComplete = CompletableDeferred<Boolean>()

    // 使用SupervisorJob防止一个子协程失败影响其他协程
    private val parentJob = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Main + parentJob + exceptionHandler)

    private lateinit var recognizeService: RecognizeService
    private lateinit var audioBlendShapePlayer: AudioPlayerUtil
    private lateinit var downloadManager: DownloadModule
    private lateinit var ttsService: TtsService

    val currentChatId: Long = Constants.chatSessionId
    private var answerSessionId = sessionIdGenerator.incrementAndGet()

    private var isInitialized = false
    private var isDestroyed = false

    private var chatCallback: IChat? = null
    private var welcomeJob: Job? = null

    @VisibleForTesting
    var weakActivity: WeakReference<Activity>? = null

    /**
     * 全局初始化方法，在Application中调用
     */
    fun initialize(activity: Activity) {
        if (isInitialized || isDestroyed) return

        weakActivity = WeakReference(activity)

        // 尝试添加生命周期监听
        tryAddLifecycleObserver(activity)

//        initServices()
//        setupDownloadManager()

//        isInitialized = true
        Log.d(TAG, "ChatInteractionManager initialized")
    }

    private fun tryAddLifecycleObserver(activity: Activity) {
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
        }
    }

    fun setChatListener(chatCallback: IChat) {
        this.chatCallback = chatCallback
    }

    fun removeChatListener() {
        this.chatCallback = null
    }

    /**
     * 初始化相关模型 ASR、TTS
     */
    private fun initServices() {
        weakActivity?.get()?.let { activity ->
            ttsService = TtsService()
            audioBlendShapePlayer = AudioPlayerUtil(ttsService)
            recognizeService = RecognizeService(activity)

            recognizeService.onRecognizeText = { text ->
                runOnUiThreadIfNeeded {
                    chatCallback?.recognitionResult(text)
                    if (text.isEmpty()) {
                        stopRecord()
                        return@runOnUiThreadIfNeeded
                    }
                    if (text.isNotEmpty()) {
                        stopRecord()
                        processChatRequest()
                    }
                }
            }

            audioBlendShapePlayer.addListener(object : AudioPlayerUtil.Listener {
                override fun onPlayStart() {

                }

                override fun onPlayEnd() {
                }
            })
        } ?: run {
            Log.w(TAG, "Activity is null during service initialization")
        }
    }

    /**
     * 初始化模型相关服务
     */
    private suspend fun setupServices() {
        if (initComplete.isCompleted) return
        if (serviceInitializing) {
            initComplete.await()
            return
        }
        serviceInitializing = true

        try {
            // 并行初始化任务
            val tasks = listOf(
                scope.async(Dispatchers.IO) {
                    Log.i(TAG, "TTS initialization started")
                    val startTime = System.currentTimeMillis()
                    loadTTSModel()
                    Log.i(
                        TAG,
                        "TTS initialization completed in ${System.currentTimeMillis() - startTime} ms"
                    )
                },
                scope.async(Dispatchers.IO) {
                    Log.i(TAG, "Recognize service initialization started")
                    val startTime = System.currentTimeMillis()
                    setupRecognizeService()
                    Log.i(
                        TAG,
                        "Recognize service initialization completed in ${System.currentTimeMillis() - startTime} ms"
                    )
                }
            )

            tasks.awaitAll()
            initComplete.complete(true)
            checkRecordAudioPermission()
        } catch (e: Exception) {
            Log.e(TAG, "Service initialization failed: ${e.message}", e)
            initComplete.completeExceptionally(e)
            serviceInitializing = false
            throw e
        }
    }

    /**
     * 权限检查
     */
    private fun checkRecordAudioPermission() {
        val activity = weakActivity?.get() ?: run {
            Log.w(TAG, "Activity is null, cannot check permission")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                activity,
                RecordPermission.permissions[0]
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showWelcomeMessage()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                RecordPermission.permissions,
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun showWelcomeMessage() {
        // 取消之前的欢迎消息任务
        welcomeJob?.cancel()

        welcomeJob = scope.launch {
            try {
                delay(WELCOME_DELAY_MS)
                ensureActive()

                val welcomeText = try {
                    ResourceProvider.get().getString(R.string.llm_welcome_text)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get welcome text: ${e.message}")
                    return@launch
                }

                answerSessionId = sessionIdGenerator.incrementAndGet()
                audioBlendShapePlayer.playSession(
                    answerSessionId,
                    welcomeText.split("[,，]".toRegex())
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Welcome message cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show welcome message: ${e.message}", e)
            }
        }
    }

    private fun processChatRequest() {
//        audioBlendShapePlayer.stop()

        sessionIdGenerator.incrementAndGet()
        answerSessionId = sessionIdGenerator.incrementAndGet()
        audioBlendShapePlayer.startNewSession(answerSessionId)
    }

    fun startRecord(): Boolean {
        if (!isInitialized || isDestroyed) {
            Log.w(TAG, "Manager not initialized or destroyed")
            return false
        }

        val isInForeground = WeLoApplication.getInstance().isAppInForeground()
        LogUtil.d(TAG, "App in foreground: $isInForeground")

        if (!isInForeground) {
            startForegroundServiceSafely()
        }

        // 开始语音识别
        return try {
            recognizeService.startRecord()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start record: ${e.message}", e)
            false
        }
    }

    fun getAudioPlayerUtil(): AudioPlayerUtil = audioBlendShapePlayer

    private fun startForegroundServiceSafely() {
        try {
            weakActivity?.get()?.let { activity ->
                val intent = Intent(activity, RecordingForegroundService::class.java)
                activity.startForegroundService(intent)
                LogUtil.d(TAG, "Started RecordingForegroundService")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start RecordingForegroundService: ${e.message}", e)
        }
    }

    fun stopRecord() {
        if (!isInitialized || isDestroyed) return

        // 停止语音识别
        recognizeService.stopRecord()
        // 停止前台服务（后台场景下）
        if (!WeLoApplication.getInstance().isAppInForeground()) {
            stopForegroundServiceSafely()
        }
        chatCallback?.stopRecord()
    }

    private fun stopForegroundServiceSafely() {
        try {
            weakActivity?.get()?.let { activity ->
                activity.stopService(Intent(activity, RecordingForegroundService::class.java))
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to stop foreground service: ${e.message}", e)
        }
    }

    private fun setupDownloadManager() {
        weakActivity?.get()?.let { activity ->
            downloadManager = DownloadModule(activity)
            downloadManager.setDownloadCallback(this)
            MHConfig.BASE_DIR = downloadManager.getDownloadPath()

            if (downloadManager.isDownloadComplete()) {
                scope.launch {
                    setupServices()
                }
            } else {
                onDownloadClicked()
            }
        } ?: run {
            Log.w(TAG, "Activity is null, cannot setup download manager")
        }
    }

    private fun onDownloadClicked() {
        scope.launch {
            try {
                downloadManager.download()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
            }
        }
    }

    private suspend fun loadTTSModel() = withContext(Dispatchers.IO) {
        ttsService.init(MHConfig.TTS_MODEL_DIR)
    }

    private suspend fun setupRecognizeService() = withContext(Dispatchers.IO) {
        recognizeService.initRecognizer()
    }

    private fun runOnUiThreadIfNeeded(action: () -> Unit) {
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
        val string = try {
            ResourceProvider.get().getString(
                R.string.download_progress,
                FileUtils.formatFileSize(currentBytes),
                FileUtils.formatFileSize(totalBytes),
                speedInfo
            )
        } catch (e: Exception) {
            "Downloading: ${FileUtils.formatFileSize(currentBytes)}/${
                FileUtils.formatFileSize(
                    totalBytes
                )
            } $speedInfo"
        }

        LogUtil.d(TAG, "Download progress: $string")
    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        LogUtil.d(TAG, "Download complete: success=$success, file=$file")
        if (success) {
            scope.launch {
                try {
                    setupServices()
                } catch (e: Exception) {
                    Log.e(TAG, "Service setup after download failed: ${e.message}", e)
                }
            }
        }
    }

    override fun onDownloadError(error: Exception?) {
        LogUtil.e(TAG, "Download error: ${error?.message}")
    }

    // 生命周期方法
    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
        owner.lifecycle.removeObserver(this)
    }

    override fun onPause(owner: LifecycleOwner) {
        // 暂停时停止录音
        stopRecord()
    }

    /**
     * 清理资源，取消所有协程任务
     */
    fun destroy() {
        if (isDestroyed) return

        isDestroyed = true
        parentJob.cancel()
        welcomeJob?.cancel()

        // 清理资源
        try {
            recognizeService.stopRecord()
            audioBlendShapePlayer.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup: ${e.message}")
        }

        removeChatListener()
        weakActivity?.clear()
        weakActivity = null

        Log.d(TAG, "ChatInteractionManager destroyed")
    }

    /**
     * 检查管理器是否可用
     */
    fun isAvailable(): Boolean {
        return isInitialized && !isDestroyed && scope.isActive
    }
}