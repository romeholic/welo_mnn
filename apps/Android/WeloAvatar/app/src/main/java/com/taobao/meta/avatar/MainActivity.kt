package com.taobao.meta.avatar

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.MHConfig.A2BS_MODEL_DIR
import com.taobao.meta.avatar.a2bs.A2BSService
import com.taobao.meta.avatar.a2bs.AudioBlendShapePlayer
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.base.BaseActivity
import com.taobao.meta.avatar.databinding.ActivityMainWeLoBinding
import com.taobao.meta.avatar.debug.DebugModule
import com.taobao.meta.avatar.download.DownloadCallback
import com.taobao.meta.avatar.download.DownloadModule
import com.taobao.meta.avatar.llm.LlmPresenter
import com.taobao.meta.avatar.llm.LlmService
import com.taobao.meta.avatar.nnr.AvatarTextureView
import com.taobao.meta.avatar.nnr.NnrAvatarRender
import com.taobao.meta.avatar.record.RecordPermission
import com.taobao.meta.avatar.record.RecordPermission.REQUEST_RECORD_AUDIO_PERMISSION
import com.taobao.meta.avatar.tts.TtsService
import com.taobao.meta.avatar.utils.MemoryMonitor
import com.taobao.meta.avatar.widget.MessageViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess


enum class ChatStatus {
    STATUS_IDLE,
    STATUS_INITIALIZING,
    STATUS_CALLING,
}

class MainActivity : BaseActivity<ActivityMainWeLoBinding, MessageViewModel>(),
    MainView.MainViewCallback, DownloadCallback {

    private var a2bsService: A2BSService? = null
    private lateinit var llmService: LlmService
    //    private lateinit var llmPresenter: LlmPresenter
    private var ttsService: TtsService? = null
    private var memoryMonitor: MemoryMonitor? = null
    private var audioBendShapePlayer: AudioBlendShapePlayer? = null
    private lateinit var recognizeService: RecognizeService
    private var callingSessionId = System.currentTimeMillis()
    private var serviceInitializing = false
    private var answerSession = System.currentTimeMillis()
    private val initComplete = CompletableDeferred<Boolean>()
    private var chatStatus = ChatStatus.STATUS_IDLE
    private var chatSessionJobs = mutableSetOf<Job>()
    private lateinit var downloadManager: DownloadModule

    private val navBarHeight: Int
        get() {
            val insets = ViewCompat.getRootWindowInsets(viewBinding.root)
            return insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        }
    private lateinit var navController: NavController
    private var isVoiceInput = true
    private var isRecording = false

    override fun createBinding(): ActivityMainWeLoBinding {
        return ActivityMainWeLoBinding.inflate(layoutInflater)
    }

    override fun initView() {
        ApplicationProvider.set(application)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.input_container) as NavHostFragment
        navController = navHostFragment.navController
        init()
        initListener()
    }

    override fun observeViewModel() {

    }
    private fun init(){
        downloadManager = DownloadModule(this)
        downloadManager.setDownloadCallback(this)
        MHConfig.BASE_DIR = downloadManager.getDownloadPath()
        memoryMonitor = MemoryMonitor(this)
        memoryMonitor!!.startMonitoring()
        a2bsService = A2BSService()
        ttsService = TtsService()
//        llmPresenter = LlmPresenter(mainView.textResponse)
        llmService = LlmService()
        recognizeService = RecognizeService(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (downloadManager.isDownloadComplete() && !DebugModule.DEBUG_DISABLE_SERVICE_AUTO_START) {
            lifecycleScope.launch {
                setupServices()
            }
        }
    }
    private fun initListener() {
        viewBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            viewBinding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = viewBinding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val marginBottom = viewBinding.buttonEndCall.marginBottom

            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出，编辑框上移
                viewBinding.root.translationY = -keypadHeight.toFloat() + marginBottom
            } else {
                // 键盘收起，恢复原位
                viewBinding.root.translationY = 0f
            }

        }
        viewBinding.buttonToggleText.setOnClickListener {
            if (isVoiceInput){
                viewBinding.waveFormView.startAnimation()
            }else{
                navController.navigate(R.id.action_text_to_voice)
                viewBinding.waveFormView.visibility = View.VISIBLE
                viewBinding.keyBordInput.visibility = View.GONE
                isVoiceInput = true
                startRecord()
            }
        }
        viewBinding.buttonEndCall.setOnClickListener {
            if (isVoiceInput){
                stopAnswer()
                navController.navigate(R.id.action_voice_to_text)
                viewBinding.waveFormView.stopAnimation()
                viewBinding.waveFormView.visibility = View.GONE
                viewBinding.keyBordInput.visibility = View.VISIBLE
                isVoiceInput = false
            }else{
                val inputMessage = viewBinding.keyBordInput.text.trim().toString()
                if (inputMessage.isEmpty()) return@setOnClickListener
                viewBinding.keyBordInput.apply {
                    text.clear()
                    processAsrText(inputMessage)
                }
            }
        }
        viewBinding.waveFormView.setOnClickListener {
            if (isRecording) {
                stopRecord()
                viewBinding.waveFormView.stopAnimation()
            } else {
                startRecord()
                viewBinding.waveFormView.startAnimation()
            }
        }
    }
    private fun stopAnswer() {
        Log.d(TAG, "stopAnswer")
        llmService.requestStop()
//        llmPresenter.stop()
        audioBendShapePlayer?.stop()
    }

    private fun cancelAllJobs() {
        chatSessionJobs.apply {
            forEach {
                it.cancel()
            }
            clear()
        }
    }

    override fun onEndCall() {
        Log.d(TAG, "onEndCall")
        chatStatus = ChatStatus.STATUS_IDLE
        showSystemBarsCompat()
        cancelAllJobs()
        stopAnswer()
        stopRecord()
//        llmPresenter.reset()
//        llmPresenter.onEndCall()
        audioBendShapePlayer?.stop()
    }

    override fun onStopAnswerClicked() {
        stopAnswer()
    }

    override fun onStartButtonClicked() {
        if (ActivityCompat.checkSelfPermission(
                this,
                RecordPermission.permissions[0]
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            handleStartChatInner()
        } else {
            ActivityCompat.requestPermissions(
                this,
                RecordPermission.permissions,
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun handleStartChatInner() {
        chatStatus = ChatStatus.STATUS_INITIALIZING
        lifecycleScope.launch {
            setupServices()
            if (chatStatus == ChatStatus.STATUS_INITIALIZING) {
                chatStatus = ChatStatus.STATUS_CALLING
                hideSystemBarsCompat()
                callingSessionId++
//                llmPresenter.setCurrentSessionId(callingSessionId)
                onChatServiceStarted()
            }
        }
    }

    override fun onDownloadClicked() {
        lifecycleScope.launch {
            downloadManager.download()
        }
    }

    private fun onChatServiceStarted() {
        llmService.startNewSession()
        lifecycleScope.launch {
            delay(2000)
            val welcomeText = getString(R.string.llm_welcome_text)
            ensureActive()
//            llmPresenter.onLlmTextUpdate(welcomeText, callingSessionId)
            audioBendShapePlayer?.playSession(answerSession, welcomeText.split("[,，]"))
        }.apply {
            chatSessionJobs.add(this)
        }
    }

    fun hideSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    private suspend fun setupServices() {
        if (initComplete.isCompleted) {
            return
        }
        if (serviceInitializing) {
            initComplete.await()
        }
        serviceInitializing = true
        lifecycleScope.async {
            val taskA2BS = async {
                val startTimeA2BS = System.currentTimeMillis()
                loadA2BSModel()
                Log.i(TAG, "Task A2BS completed in ${System.currentTimeMillis() - startTimeA2BS} ms")
            }
            val taskTTS = async {
                Log.i(TAG, "Task TTS init begin")
                val startTimeTTS = System.currentTimeMillis()
                loadTTSModel()
                Log.i(TAG, "Task TTS completed in ${System.currentTimeMillis() - startTimeTTS} ms")
            }
            val taskLLM = async {
                val startTimeLLM = System.currentTimeMillis()
                loadLLMModel()
                Log.i(TAG, "Task LLM completed in ${System.currentTimeMillis() - startTimeLLM} ms")
            }
            val taskRecognize = async {
                val startTimeLLM = System.currentTimeMillis()
                setupRecognizeService()
                Log.i(TAG, "Task Recognize completed in ${System.currentTimeMillis() - startTimeLLM} ms")
            }
            awaitAll(taskA2BS, taskTTS,  taskLLM, taskRecognize)
            Log.i(TAG, "All services have been initialized")
            onStartButtonClicked()
            recognizeService.onRecognizeText = { text ->
                Log.d(TAG, "onRecognizeText: $text chatStatus:$chatStatus")
                if (chatStatus == ChatStatus.STATUS_CALLING) {
                    stopRecord()
                    lifecycleScope.launch {
                        processAsrText(text)
                    }
                }
            }
        }.await()
        initComplete.complete(true)
        serviceInitializing = false
    }

    fun serviceInitialized():Boolean {
        return initComplete.isCompleted
    }

    private fun processAsrText(text:String) {
        answerSession++;
        Log.d(TAG, "onRecognizeText: $text sessionId: $answerSession")
        lifecycleScope.launch {
//            llmPresenter.onUserTextUpdate(text)
            viewModel.sendMessage(text)
        }
//        llmPresenter.start()
        audioBendShapePlayer?.startNewSession(answerSession)
        lifecycleScope.launch {
            val callingSessionId = this@MainActivity.callingSessionId
            var isEndReceived = false

            llmService.generateFlow(text).collect { pair ->
                if (isEndReceived) {
                    return@collect
                }

                val partialText = pair.first
                val fullText = pair.second

                // 处理中间文本（实时更新UI）
                if (partialText != null) {
                    Log.d(TAG, "收到中间文本: $partialText")
                    viewModel.receivedMessage(partialText)

                    // 更新UI显示中间文本
                    lifecycleScope.launch(Dispatchers.Main) {
//                        llmPresenter.onLlmTextUpdate(partialText, callingSessionId)
                    }
                }else{
                    Log.d(TAG, "当前会话结束")
                }

                // 处理最终文本
                if (partialText == null) {
                    Log.d(TAG, "收到最终文本: $fullText")
                    isEndReceived = true
//                    viewModel.receivedMessage(fullText)
                    viewModel.receivedStatus(true)
                    startRecord()
                    // 更新UI显示最终文本
                    lifecycleScope.launch(Dispatchers.Main) {
//                        llmPresenter.onLlmTextUpdate(fullText, callingSessionId)
                    }
                }
            }
        }.apply {
            chatSessionJobs.add(this)
        }
    }

    fun getAudioBlendShapePlayer():AudioBlendShapePlayer? {
        return audioBendShapePlayer
    }

    private fun createAudioBlendShapePlayer() {
        audioBendShapePlayer = AudioBlendShapePlayer(this@MainActivity)
        audioBendShapePlayer!!.addListener(object: AudioBlendShapePlayer.Listener{
            override fun onPlayStart() {
                Log.d(TAG, "onPlayStart: chatStatus: $chatStatus")
                stopRecord()
            }

            override fun onPlayEnd() {
                Log.d(TAG, "onPlayEnd: chatStatus: $chatStatus")
                if (chatStatus == ChatStatus.STATUS_CALLING) {
                    startRecord()
                }
            }
        })
    }

    private suspend fun setupRecognizeService() {
        recognizeService.initRecognizer()
    }

    fun getA2bsService(): A2BSService {
        return a2bsService!!
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
        if (chatStatus == ChatStatus.STATUS_CALLING) {
            onEndCall()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (memoryMonitor != null) {
            memoryMonitor!!.stopMonitoring()
        }
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: Configuration has changed. Language updated.")
        recreate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handleStartChatInner()
                } else {
                    Toast.makeText(this, R.string.record_permission_denied, Toast.LENGTH_SHORT).show()
                    chatStatus = ChatStatus.STATUS_IDLE
                }
            }
        }
    }


    private suspend fun loadLLMModel() {
        llmService.init(MHConfig.LLM_MODEL_DIR)
    }

    private suspend fun loadTTSModel() {
        ttsService!!.init(MHConfig.TTS_MODEL_DIR)
    }

    private suspend fun loadA2BSModel() {
        a2bsService!!.init(A2BS_MODEL_DIR, this)
        createAudioBlendShapePlayer()
    }

    fun getTtsService(): TtsService {
        return ttsService!!
    }

    fun stopRecord() {
        recognizeService.stopRecord()
        viewBinding.waveFormView.stopAnimation()
        isRecording = false
    }

    fun startRecord() {
        Log.d(TAG, "startRecord")
        recognizeService.startRecord()
        viewBinding.waveFormView.startAnimation()
        isRecording = true
    }

    override fun onDownloadStart() {
        Log.d(TAG, "Download started")
    }

    override fun onDownloadProgress(progress: Double, currentBytes: Long, totalBytes: Long, speedInfo:String) {
    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        Log.d(TAG, "Download completed: $success")
        lifecycleScope.launch {
            setupServices()
        }
    }

    override fun onDownloadError(error: Exception?) {
        Log.e(TAG, "Download error", error)
    }
    companion object {
        private const val TAG = "WELO#MainActivity"
        init {
            System.loadLibrary("taoavatar")
        }
    }
}
