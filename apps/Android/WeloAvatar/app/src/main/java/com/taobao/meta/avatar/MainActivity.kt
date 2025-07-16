package com.taobao.meta.avatar

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
import com.taobao.meta.avatar.download.DownloadCallback
import com.taobao.meta.avatar.download.DownloadModule
import com.taobao.meta.avatar.record.RecordPermission
import com.taobao.meta.avatar.record.RecordPermission.REQUEST_RECORD_AUDIO_PERMISSION
import com.taobao.meta.avatar.tts.TtsService
import com.taobao.meta.avatar.utils.MemoryMonitor
import com.taobao.meta.avatar.widget.FeatureTourUtil
import com.taobao.meta.avatar.widget.InputMode
import com.taobao.meta.avatar.widget.MessageViewModel
import com.taobao.meta.avatar.widget.PreferenceUtil
import com.taobao.meta.avatar.widget.TextStreamResponse
import kotlinx.coroutines.CompletableDeferred
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

    private lateinit var navController: NavController
    private var isVoiceInput = true
    private var isRecording = false

    /**
     * 当前请求是否结束
     */
    private var isEndReceived = true

    override fun createBinding(): ActivityMainWeLoBinding {
        return ActivityMainWeLoBinding.inflate(layoutInflater)
    }

    override fun initView() {
        ApplicationProvider.set(application)
        hideSystemBarsCompat()
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.input_container) as NavHostFragment
        navController = navHostFragment.navController
        PreferenceUtil.init(this)
        init()
        initListener()
        FeatureTourUtil.featureTour(this, viewBinding)
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            // 观察状态变化
            viewModel.aiResponseFlow.collect { response ->
                when (response) {
                    is TextStreamResponse.Connecting -> {
                        println("正在连接到流...")
                        isEndReceived = false
                        inputStatusImage(InputMode.Typing)
                    }
                    is TextStreamResponse.Connected -> {
                        println("已连接到流")
                    }
                    is TextStreamResponse.Data -> {
                        audioBendShapePlayer?.playStreamText(response.text)
                    }
                    is TextStreamResponse.Completed -> {
                        requestEnd()
                        println("流读取完成")
                    }
                    is TextStreamResponse.Cancelled -> {
                        requestEnd()
                        println("流已取消")
                    }
                    is TextStreamResponse.Error -> {
                        requestEnd()
                        val errorMessage = response.message
                        // 判断是否为网络相关错误（根据实际错误信息特征调整）
                        val isNetworkError = errorMessage.contains("网络", ignoreCase = true)
                                || errorMessage.contains("连接", ignoreCase = true)
                                || errorMessage.contains("timeout", ignoreCase = true)
                                || errorMessage.contains("connect", ignoreCase = true)
                                || errorMessage.contains("DNS", ignoreCase = true)

                        if (isNetworkError) {
                            Toast.makeText(this@MainActivity, "网络连接失败，请检查 Wi-Fi 或数据网络", Toast.LENGTH_LONG).show()
                        } else {
                            println("流错误: $errorMessage")
                        }
                    }
                    is TextStreamResponse.Idle -> Unit // 初始状态，无需操作
                }
            }
        }

    }
    private fun init(){
        downloadManager = DownloadModule(this)
        downloadManager.setDownloadCallback(this)
        MHConfig.BASE_DIR = downloadManager.getDownloadPath()
        memoryMonitor = MemoryMonitor(this)
        memoryMonitor!!.startMonitoring()
        a2bsService = A2BSService()
        ttsService = TtsService()
        recognizeService = RecognizeService(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (downloadManager.isDownloadComplete()) {
            lifecycleScope.launch {
                setupServices()
            }
        } else {
            onDownloadClicked()
        }
        Log.d(TAG, "init: download complete: ${downloadManager.isDownloadComplete()}")
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun initListener() {
        viewBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            viewBinding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = viewBinding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val marginBottom = viewBinding.inputStateChange.marginBottom

            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出，编辑框上移
                viewBinding.root.translationY = -keypadHeight.toFloat() + marginBottom
            } else {
                // 键盘收起，恢复原位
                viewBinding.root.translationY = 0f
            }

        }
        viewBinding.inputModelChange.setOnClickListener {
            inputModelChange()
        }
        viewBinding.multifunctionalIv.setOnClickListener {
            Toast.makeText(this, "多功能按钮,敬请期待！", Toast.LENGTH_SHORT).show()
        }

        viewBinding.inputStateChange.apply {
            setOnLongClickListener {
                Log.d(TAG, "inputStateChange long clicked :$isEndReceived")
                if (isVoiceInput && isEndReceived) {
                    startRecord()
                }
                true // ✅ 消费长按事件
            }
            setOnClickListener {
                Log.d(TAG, "inputStateChange clicked")
                if (!isEndReceived){
                    isEndReceived = true
                    viewModel.closeRequest(callingSessionId.toString())
                    if (isVoiceInput){
                        inputStatusImage(InputMode.VoiceInput)
                    }else{
                        inputStatusImage(InputMode.Send)
                    }

                }else{
                    val inputMessage = viewBinding.keyBordInput.text.trim().toString()
                    if (inputMessage.isEmpty()) return@setOnClickListener
                    viewBinding.keyBordInput.apply {
                        text.clear()
                        processChatRequest(inputMessage)
                    }
                }
            }
        }
    }
    /**
     * 文本、语音输入模式切换
     */
    private fun inputModelChange() {
        if (isVoiceInput) {
            isVoiceInput = false
            navController.navigate(R.id.action_voice_to_text)
            viewBinding.inputModelChange.setImageResource(R.drawable.audio)
            viewBinding.inputStateChange.setImageResource(R.drawable.send_icon)
            viewBinding.waveFormView.visibility = View.GONE
            viewBinding.keyBordInput.visibility = View.VISIBLE
            stopRecord()
        } else {
            isVoiceInput = true
            navController.navigate(R.id.action_text_to_voice)
            viewBinding.inputModelChange.setImageResource(R.drawable.key_bord)
            viewBinding.inputStateChange.setImageResource(R.drawable.microphone)
            viewBinding.waveFormView.visibility = View.VISIBLE
            viewBinding.keyBordInput.visibility = View.GONE
        }
        viewModel.closeRequest(callingSessionId.toString())
        isEndReceived = true
    }
    private fun stopAnswer() {
        Log.d(TAG, "stopAnswer")
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
//        showSystemBarsCompat()
        cancelAllJobs()
        stopAnswer()
        stopRecord()
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
                callingSessionId++
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
        lifecycleScope.launch {
            delay(2000)
            val welcomeText = getString(R.string.llm_welcome_text)
            ensureActive()
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
                if (text.isEmpty()){
                    stopRecord()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "识别结果为空，请重新尝试", Toast.LENGTH_SHORT).show()
                    }
                }
                if (chatStatus == ChatStatus.STATUS_CALLING && text.isNotEmpty()) {
                    stopRecord()
                    lifecycleScope.launch {
                        processChatRequest(text)
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

    private fun processChatRequest(text: String) {
        answerSession++
        callingSessionId++
        audioBendShapePlayer?.startNewSession(answerSession)
        lifecycleScope.launch {
            viewModel.sendMessage(text)
            viewModel.receivedMessage(text,callingSessionId.toString())
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

    override fun onResume() {
        super.onResume()
        Log.d(TAG,"onResume chatStatus:$chatStatus")
        chatStatus = ChatStatus.STATUS_CALLING
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
        Log.d(TAG, "stopRecord:$isVoiceInput")
        recognizeService.stopRecord()
        viewBinding.waveFormView.stopAnimation()
        isRecording = false
    }

    /**
     * 开始录音
     * 录音功能主动触发
     * @param init 是否是初始化调用
     */
    fun startRecord() {
        Log.d(TAG, "startRecord: isVoiceInput:$isVoiceInput")
        if (serviceInitializing) {
            Toast.makeText(this, "服务正在初始化，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }
        if (isVoiceInput) {
            recognizeService.startRecord()
            viewBinding.waveFormView.startAnimation()
            isRecording = true
            inputStatusImage(InputMode.VoiceInput)
        } else {
            viewModel.receivedStatus(true)
            inputStatusImage(InputMode.Send)
        }
    }

    /**
     * 请求结束
     */
    private fun requestEnd(){
        Log.d(TAG, "requestEnd: isVoiceInput:$isVoiceInput :$isRecording")
        isRecording = true
        isEndReceived = true
        if (isVoiceInput){
            inputStatusImage(InputMode.VoiceInput)
        }else{
            viewModel.receivedStatus(true)
            inputStatusImage(InputMode.Send)
        }
    }

    private fun inputStatusImage(mode: InputMode) {
        when (mode) {
            is InputMode.Voice -> viewBinding.inputModelChange.setImageResource(R.drawable.audio)
            is InputMode.Text ->  viewBinding.inputModelChange.setImageResource(R.drawable.key_bord)
            is InputMode.Typing ->  {
                viewBinding.inputStateChange.setImageResource(R.drawable.stop_line)
                if (!isVoiceInput){
                    viewBinding.keyBordInput.isEnabled = false
                }
            }
            is InputMode.Send -> {
                viewBinding.inputStateChange.setImageResource(R.drawable.send_icon)
                isEndReceived = true
                if (!isVoiceInput) {
                    viewBinding.keyBordInput.isEnabled = true
                }
            }
            is InputMode.VoiceInput ->{
                viewBinding.inputStateChange.setImageResource(R.drawable.microphone)
            }
        }
    }

    override fun onDownloadStart() {
        Log.d(TAG, "Download started")
        viewBinding.loadingText.visibility = View.VISIBLE
    }

    override fun onDownloadProgress(progress: Double, currentBytes: Long, totalBytes: Long, speedInfo:String) {

    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        Log.d(TAG, "Download completed: $success")
        lifecycleScope.launch {
            viewBinding.loadingText.visibility = View.GONE
            setupServices()
        }
    }

    override fun onDownloadError(error: Exception?) {
        Log.e(TAG, "Download error", error)
        viewBinding.loadingText.visibility = View.GONE
    }
    companion object {
        private const val TAG = "WELO#MainActivity"
        init {
            System.loadLibrary("taoavatar")
        }
    }
}
