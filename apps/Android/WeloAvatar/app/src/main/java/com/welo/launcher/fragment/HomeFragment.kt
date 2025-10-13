package com.welo.launcher.fragment

import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.WeLoApplication
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.FragmentHomeBinding
import com.welo.base.BaseFragment
import com.welo.base.gone
import com.welo.base.observeInLifecycleWithDelay
import com.welo.base.visible
import com.welo.callback.IChat
import com.welo.constant.Constants
import com.welo.constant.Constants.FEATURE_HOME_WELCOME
import com.welo.constant.Constants.FEATURE_TOUR_KEY
import com.welo.constant.Constants.GUIDE_ANIMATOR_DURATION
import com.welo.launcher.HomeActivity
import com.welo.launcher.MemoryMainActivity
import com.welo.launcher.anim.AnimUtil
import com.welo.launcher.asr.ASRManager
import com.welo.launcher.chathelper.ChatRecyclerHelper
import com.welo.launcher.entity.AIOptionItem
import com.welo.launcher.listener.IInputBarListener
import com.welo.launcher.repository.AIResponseEvent
import com.welo.launcher.viewmodel.ChatViewModel
import com.welo.login.LoginViewModel
import com.welo.manager.ChatInteractionManager
import com.welo.manager.ImageUploadManager
import com.welo.manager.ImageUploadResult
import com.welo.util.AiToolsUtil
import com.welo.util.FileUtil
import com.welo.util.JumpUtil
import com.welo.util.LogUtil
import com.welo.util.PhotoPickerUtils
import com.welo.util.PreferenceUtil
import com.welo.widget.HomeInputBarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import com.welo.util.KeyboardUtils
import com.welo.viewmodel.AgentViewModel

// TODO: Rename parameter arguments, choose names that match
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val GUIDE_SAY_HALO = "Hi、"
/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding, ChatViewModel>(), IChat {
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var recyclerHelper: ChatRecyclerHelper
    private var currentChatId: Long = 0L
    private var currentSessionId: Long = -1

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var keyboardVisible = false
    private var keyboardHeight = 0
    private lateinit var guideAnimate: com.airbnb.lottie.LottieAnimationView
    private lateinit var guideRecycleView: RecyclerView
    private lateinit var guideInput: HomeInputBarView
    private lateinit var guideEndNotice: TextView
    private var guideMode = PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)

    private var isCancelVoiceInput = false
    private var hasLoadAllMessage = false

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var inputUris: ArrayList<Uri>? = arrayListOf()
    private var currentPhotoUri: String? = null
    private val imageUploadManager = ImageUploadManager()
    private var successfulUploads: List<ImageUploadResult.Success> = emptyList()

    private lateinit var mediaLauncher: ActivityResultLauncher<Intent>

    private var currentAiToolOption: AIOptionItem? = null
    private val aiToolsUtil = AiToolsUtil()
    private val agentViewModel: AgentViewModel by activityViewModels()
    private var guideAnimateRound: Int = 0

    private val asrManager by lazy {
        ASRManager { resultText ->
            LogUtil.d(TAG, "ASR 识别结果: $resultText")
            if (isCancelVoiceInput) {
                isCancelVoiceInput = false
                return@ASRManager
            }
            requestChat(resultText)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    @RequiresApi(Build.VERSION_CODES.P)
    override fun initView() {
        currentChatId = Constants.chatSessionId
        currentSessionId = currentChatId
        initHelper()
        if (judgeGuideMode(guideMode)) {
            Constants.agentType = Constants.AGENT_TYPE_WELO_GUIDANCE
            initGuideView()
        } else {
            setupMemoryIcon()
            initInputBar()
        }

        setupKeyboardListener()
        registerCameraLauncher()
        registerMedia()

        LogUtil.d(TAG,"allMessages recyclerView:${binding.recyclerView.isVisible}  blurryMask:${binding.blurryMaskView.isVisible}")
    }

    /**
     * 启动相机拍照获取图片
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerCameraLauncher() {
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                createImageUri(currentPhotoUri)
                handleSelectedImage(inputUris)
            }
        }
    }

    /**
     * 进行相册选取图片
     */
    private fun registerMedia() {
        mediaLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (_, data) ->
            data?.let {
                PhotoPickerUtils.handleMultipleImagesResult(data, requireActivity())
            }
        }
    }

    private fun initHelper() {
        recyclerHelper = ChatRecyclerHelper(
            if (!guideMode) binding.recyclerView else binding.guideMain.findViewById(
                R.id.message_list
            )
        ).apply {
            init(
                onRetry = { message ->

                },
                onMessageLongClick = { message ->

                },
                swipeListener = object : ChatRecyclerHelper.OnSwipeListener {
                    override fun onSwipeLeft() {
                        LogUtil.d(TAG, "recycleView 左滑")
                        hideRecyclerView()
                    }

                    override fun onSwipeRight() {
                        LogUtil.d(TAG, "recycleView 右滑")
                        hideRecyclerView()
                    }
                },
                onEmptySpaceClick = {
                    hideRecyclerView()
                }
            )
        }
    }

    private fun initGuideInputBar(v: HomeInputBarView) {
        v.setViewListener(false, object : IInputBarListener {
            override fun textSend(text: String) {
                Constants.agentType = Constants.AGENT_TYPE_WELO_GUIDANCE
                requestChat(text)
            }

            override fun onKeyDown() {
                asrManager.runAsrAudio()
            }

            override fun onKeyUp() {
                asrManager.stopAsr()
            }
        })
    }

    private fun initInputBar() {
        binding.inputBarLayout.setViewListener(listener = object : IInputBarListener {
            override fun textSend(text: String) {
                requestChat(text)
            }

            override fun onKeyDown() {
                LogUtil.d(TAG, "onKeyDown")
                asrManager.runAsrAudio()
                isCancelVoiceInput = false
            }

            override fun onCancelVoiceInput() {
                isCancelVoiceInput = true
                asrManager.stopAsr()
                Toast.makeText(requireContext(), "已取消语音输入", Toast.LENGTH_SHORT).show()
            }

            override fun onClickListener() {
                LogUtil.d(TAG,"onClickListener")
                if (!hasLoadAllMessage) {
                    viewModel.loadAllMessages()
                    hasLoadAllMessage = true
                } else {
                    if (binding.recyclerView.isVisible.not() || binding.blurryMaskView.isVisible.not()) {
                        AnimUtil.toChatAnime(binding.blurryMaskView, binding.recyclerView) {
                            binding.recyclerView.postDelayed({
                                recyclerHelper.scrollToBottomImmediately()
                            }, 150)
                        }
                    }
                }
            }

            override fun deleteImage(uri: Uri) {
                if (successfulUploads.isNotEmpty()) {
                    successfulUploads = successfulUploads.filterNot { it.uri == uri }
                }
            }

            override fun onFileClick(clickType: String) {
                when (clickType) {
                    "upload_file" -> uploadFile()
                    "upload_image" -> uploadImage()
                    "upload_camera" -> uploadCamera()
                    "audio_call" -> audioCall()
                }
            }

            override fun toolItem(aiOptionItem: AIOptionItem?) {
                currentAiToolOption = aiOptionItem
            }

            override fun onKeyUp() {
                LogUtil.d(TAG, "onKeyUp")
                asrManager.stopAsr()
            }
        })
    }

    private fun uploadFile() {
        // 实现文件上传逻辑
    }

    private fun uploadImage() {
        val maxCount = PICTURE_MAX_COUNT - successfulUploads.size
        if (maxCount <= 0) {
            Toast.makeText(requireContext(), "已超过最大上传数量", Toast.LENGTH_SHORT).show()
            return
        }
        PhotoPickerUtils.openGallery(
            requireActivity(),
            object : PhotoPickerUtils.OnPhotosSelectedListener {
                @RequiresApi(Build.VERSION_CODES.P)
                override fun onPhotosSelected(bitmaps: List<Bitmap>, uris: List<Uri>) {
                    LogUtil.d(TAG, "onPhotosSelected")
                    handleSelectedImage(uris)
                }

                override fun onSelectionIncomplete(minRequired: Int, actualSelected: Int) {
                    // 选择数量不足，提示用户
                    LogUtil.d(TAG, "onSelectionIncomplete")
                }

                override fun onSelectionExceeded(maxAllowed: Int, actualSelected: Int) {
                    LogUtil.d(TAG, "onSelectionExceeded")
                    // 选择数量超出，提示用户
                }

                override fun onPhotosError(errorMsg: String?) {
                    // 处理错误
                    LogUtil.d(TAG, "onPhotosError errorMsg:$errorMsg")
                }

                override fun onSelectionCancelled() {
                    // 用户取消了选择
                    LogUtil.d(TAG, "onSelectionCancelled")
                }
            },
            mediaLauncher,
            minCount = PICTURE_MIN_COUNT,
            maxCount = maxCount
        )
    }

    private fun uploadCamera() {
        inputUris?.clear()
        currentPhotoUri = JumpUtil.startCamera(requireActivity(), cameraLauncher)
    }

    private fun audioCall() {
        JumpUtil.jumpAudioCall(requireContext())
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleSelectedImage(uri: List<Uri>?) {
        uri ?: return
        LogUtil.d(TAG, "handleSelectedImage uri:$uri")
        lifecycleScope.launch {
            try {
                // 显示图片预览（初始状态为上传中）
                binding.inputBarLayout.previewImage(uri, uploadInProgress = true)
                // 开始上传图片
                val uploadResults = imageUploadManager.uploadImages(uri) { uri, progress ->
                    binding.inputBarLayout.updateImageUploadProgress(uri, progress)
                }

                // 检查所有图片是否上传成功
                successfulUploads = uploadResults.filterIsInstance<ImageUploadResult.Success>()
                val failedUploads = uploadResults.filterIsInstance<ImageUploadResult.Failed>()

                if (failedUploads.isNotEmpty()) {
                    // 处理上传失败的图片
                    Toast.makeText(
                        requireContext(),
                        "${failedUploads.size}张图片上传失败",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 移除上传失败的图片
                    failedUploads.forEach { result ->
                        binding.inputBarLayout.removeImagePreview(result.uri)
                    }
                }
                // 所有图片上传成功，更新发送状态
                if (successfulUploads.isNotEmpty()) {
                    binding.inputBarLayout.setSendDrawableState(true)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "处理图片失败: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun observeViewModel() {
        guideMode = PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)
        LogUtil.d(TAG, "AIResponseEvent loginViewModel.logined:${agentViewModel.logined}")
        if(guideMode) {
            lifecycleScope.launch {
                delay(4000)
                requestChat(GUIDE_SAY_HALO)
            }
        }
        viewModel.mediaUris.observe(this) { absolutePath ->
            createImageUri(absolutePath)
            if (!hasLoadAllMessage) {
                viewModel.loadAllMessages()
                hasLoadAllMessage = true
                binding.inputBarLayout.postDelayed({
                    handleSelectedImage(inputUris)
                }, 350)
            } else {
                if (binding.recyclerView.isVisible.not() || binding.blurryMaskView.isVisible.not()) {
                    AnimUtil.toChatAnime(binding.blurryMaskView, binding.recyclerView) {
                        binding.recyclerView.postDelayed({
                            recyclerHelper.scrollToBottomImmediately()
                        }, 150)
                        handleSelectedImage(inputUris)
                    }
                }
            }
        }
        viewModel.observeAIResponses().observeInLifecycleWithDelay(this, 100) { event ->
            when (event) {
                is AIResponseEvent.Start -> {
                    LogUtil.d(TAG, "AIResponseEvent Start sessionId:${event.sessionId}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                }

                is AIResponseEvent.Chunk -> {
                    if (event.chunk.contains("s"))
                        LogUtil.d(TAG, "AIResponseEvent chunk:${event.chunk}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    val changes = Bundle().apply {
                        var s = event.chunk
                        if (guideMode && event.chunk.contains("summary")) {
                            s = event.chunk.replace(Regex("</?summary>"), "")
                            handleGuideEndProcess()
                        }
                        putString("content", s)
                        putBoolean("isIncremental", event.isIncremental)
                        putBoolean("shouldScroll", true)
                    }
                    recyclerHelper.notifyItemChanged(changes)
                }

                is AIResponseEvent.Complete -> {
                    LogUtil.d(TAG, "AIResponseEvent Complete sessionId:${event.sessionId}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    recyclerHelper.updateLoadingState(false)
                    recyclerHelper.scrollToBottomImmediately()
                    if (guideMode) {
                        changeGuideAnimatorByStep()
                    }
                    binding.inputBarLayout.setViewEnable(true)
                }

                is AIResponseEvent.Error -> {
                    LogUtil.d(TAG, "AIResponseEvent Error sessionId:${event.sessionId}")
                    if (event.errorCode == 401){
                        (requireActivity() as? HomeActivity)?.setupLogin()
                    }
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    aiResponseEnd()
                }

                is AIResponseEvent.Cancelled -> {
                    LogUtil.d(TAG, "AIResponseEvent Cancelled sessionId:${event.sessionId}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    aiResponseEnd()
                }

                is AIResponseEvent.Connected -> {
                    LogUtil.d(TAG, "AIResponseEvent Connected sessionId:${event.sessionId}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    recyclerHelper.updateLoadingState(true)
                    recyclerHelper.scrollToBottomImmediately()
                }

                is AIResponseEvent.Connecting -> {
                    LogUtil.d(
                        TAG,
                        "AIResponseEvent Connecting messageId:${event.messageId} sessionId:${event.sessionId}"
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewModel.allMessages.collect { chatMessages ->
                if (chatMessages.isEmpty()) {
                    return@collect
                }
                if (binding.recyclerView.isVisible.not() || binding.blurryMaskView.isVisible.not()) {
                    AnimUtil.toChatAnime(binding.blurryMaskView, binding.recyclerView) {
                        if (binding.recyclerView.height == 0) {
                            binding.recyclerView.requestLayout()
                        }
                        recyclerHelper.submitMessages(chatMessages, currentSessionId)
                    }
                } else {
                    recyclerHelper.submitMessages(chatMessages, currentSessionId)
                }
            }
        }
    }

    private fun aiResponseEnd() {
        recyclerHelper.updateLoadingState(false)
        loadSessionMessages(currentSessionId)
        binding.inputBarLayout.setViewEnable(true)
    }

    private fun setupKeyboardListener() {
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val marginBottom = binding.inputBarLayout.marginBottom
            val screenHeight = binding.root.height
            val keypadHeight = screenHeight - rect.bottom - marginBottom + 40

            val isKeyboardVisible = keypadHeight > screenHeight * KEYBOARD_THRESHOLD_RATIO

            if (isKeyboardVisible != keyboardVisible) {
                keyboardVisible = isKeyboardVisible
                keyboardHeight = keypadHeight

                if (keyboardVisible) {
                    // 键盘弹出时调整布局
                    adjustLayoutForKeyboard(true, keypadHeight)
                } else {
                    // 键盘隐藏时恢复布局
                    adjustLayoutForKeyboard(false, 0)
                }
            }
        }

        globalLayoutListener?.let {
            binding.root.viewTreeObserver.addOnGlobalLayoutListener(it)
        }
    }

    private fun adjustLayoutForKeyboard(visible: Boolean, height: Int) {
        if (visible) {
            // 将整个主布局向上移动键盘高度
            binding.homeMain.translationY = -height.toFloat()

            // 可选：如果需要，也可以调整引导模式的布局
            if (::guideRecycleView.isInitialized) {
                binding.guideMain.translationY = -height.toFloat()
            }

            // 滚动到最新消息
            binding.recyclerView.postDelayed({
                recyclerHelper.scrollToBottomImmediately()
            }, 100)

        } else {
            // 恢复原位
            binding.homeMain.translationY = 0f
            if (::guideRecycleView.isInitialized) {
                binding.guideMain.translationY = 0f
            }
        }
    }

    private fun setupMemoryIcon() {
        binding.userMemory.apply {
            setAnimation("guide_0.json")
            repeatCount = ValueAnimator.INFINITE
            playAnimation()
            setOnLongClickListener {
                val intent = Intent(context, MemoryMainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                requireContext().startActivity(intent)
                true
            }
        }
    }

    fun setSession(sessionId: Long) {
        currentSessionId = sessionId
        loadSessionMessages(sessionId)
    }

    private fun loadSessionMessages(sessionId: Long) {
        lifecycleScope.launch {
            try {
                viewModel.observeSessionMessages(sessionId).collect { messages ->
                    recyclerHelper.submitMessages(messages, sessionId)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "加载消息失败: ${e.message}")
            }
        }
    }

    private fun requestChat(inputData: String) {
        if (inputData.isEmpty() && successfulUploads.isEmpty()){
            LogUtil.d(TAG,"There is no picture or text information")
            return
        }
        lifecycleScope.launch {
            val prompt = if (currentAiToolOption != null) {
                aiToolsUtil.getProcessedPrompt(inputData, currentAiToolOption)
            } else {
                inputData
            }
            LogUtil.d(TAG, "requestChat prompt:$prompt")
            try {
                WeLoApplication.chatRepository.ensureSessionExists(currentSessionId)
                setSession(currentChatId)
                viewModel.sendMessage(
                    currentChatId,
                    prompt,
                    successfulUploads.map { it.imageUrl })
                successfulUploads = emptyList()
                inputUris?.let {
                    if (it.isNotEmpty()){
                        binding.inputBarLayout.setSendDrawableState(false)
                    }
                }
                inputUris?.clear()
                binding.inputBarLayout.toolItemSelect()
                binding.inputBarLayout.setupTools()
                binding.inputBarLayout.setViewEnable(false)
            } catch (e: Exception) {
                LogUtil.e(TAG, "发送消息失败: ${e.message}")
            }
        }
    }

    fun hideRecyclerView() {
        if (binding.recyclerView.isVisible) {
            binding.recyclerView.gone()
            binding.blurryMaskView.gone()
            binding.inputBarLayout.hideToolLayout()
            LogUtil.d(TAG, "hideRecyclerView")
        }
        binding.inputBarLayout.setViewEnable(true)
        viewModel.cancelResponse(currentSessionId)
    }

    override fun onResume() {
        super.onResume()
        ChatInteractionManager.getInstance().setChatListener(this)
        binding.userMemory.playAnimation()
        if (guideMode)
            guideAnimate.playAnimation()
    }

    override fun onPause() {
        super.onPause()
        ChatInteractionManager.getInstance().removeChatListener()
        binding.userMemory.pauseAnimation()
        if (guideMode)
            guideAnimate.pauseAnimation()
        viewModel.cancelResponse(currentSessionId)
    }

    override fun onDestroy() {
        try {
            globalLayoutListener?.let {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        } catch (e: Exception) {
            LogUtil.d(TAG, "onDestroy Exception:$e")
        }
        globalLayoutListener = null
        super.onDestroy()
    }

    override fun startRecord() {}

    override fun stopRecord() {}

    override fun recognitionResult(inputText: String) {
        requestChat(inputText)
    }

    override fun stopAnswer() {}

    override fun stopRequest() {}
    private fun changeGuideAnimatorByStep(){
        binding.guideMain.animateViewExpansion(guideAnimateRound)
        guideAnimateRound++
        if (guideAnimateRound > binding.guideMain.max_round - 1) {
            guideAnimateRound = guideAnimateRound - binding.guideMain.max_round
        }
    }
    private fun restoreHomeView() {
        PreferenceUtil.get().putBoolean(FEATURE_TOUR_KEY, false)
        guideMode = false
        binding.guideMain.gone()
        initHelper()
        binding.homeMain.visible()
        setupMemoryIcon()
        showWelcome()
        binding.inputBarLayout.showTextInputMode()
        initInputBar()
        setupKeyboardListener()
        hideRecyclerView()
        Constants.agentType = Constants.AGENT_TYPE_WELO_CHAT
    }
    private fun showWelcome(){
        if(PreferenceUtil.get().getBoolean(FEATURE_HOME_WELCOME)){
            KeyboardUtils.popOutViem(binding.welcometext,true)
            binding.welcometext.setOnClickListener { 
                KeyboardUtils.popOutViem(binding.welcometext,false)
                PreferenceUtil.get().putBoolean(FEATURE_HOME_WELCOME,false)
            }
            lifecycleScope.launch {
                delay(GUIDE_ANIMATOR_DURATION*5)
                KeyboardUtils.popOutViem(binding.welcometext,false)
                PreferenceUtil.get().putBoolean(FEATURE_HOME_WELCOME,false)
            }
        }
    }

    private fun judgeGuideMode(guide: Boolean): Boolean {
        if (guide) {
            binding.guideMain.visible()
            binding.homeMain.gone()
        } else {
            binding.guideMain.gone()
            binding.homeMain.visible()
        }
        LogUtil.d(TAG, "guide,visible=${binding.homeMain.visibility}")
        return guide
    }

    private fun initGuideView() {
        guideEndNotice = binding.guideMain.findViewById(R.id.guide_end_notice)
        guideInput = binding.guideMain.findViewById(R.id.guide_edit)
        guideAnimate = binding.guideMain.findViewById(R.id.guide_animate)
        guideRecycleView = binding.guideMain.findViewById(R.id.message_list)
        initGuideInputBar(guideInput)
    }

    /**
     * 获取拍照后得到的图片URI，并授予读取权限
     */
    private fun createImageUri(filePath: String?) {
        val imageFile = File(filePath ?: "")
        if (imageFile.exists()) {
            val imageUri = FileUtil.getPhotoUri(requireContext(), imageFile)
            // 授予读取权限
            requireActivity().grantUriPermission(
                requireContext().packageName,
                imageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            if (inputUris == null) {
                inputUris = arrayListOf()
            }
            inputUris?.add(imageUri)
        }
    }
    private fun handleGuideEndProcess() {
        PreferenceUtil.get().putBoolean(FEATURE_TOUR_KEY, false)
        guideMode = false
        guideInput.gone()
        guideEndNotice.visible()
        guideEndNotice.setOnClickListener {
            binding.guideMain.stepExpandWithLottie(guideAnimate)
            //引导页动画切回初始状态
            binding.guideMain.animateViewExpansion(0)
            lifecycleScope.launch {
                //引导页动画结束后恢复主页layout
                delay(GUIDE_ANIMATOR_DURATION)
                restoreHomeView()
            }
        }
    }
    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }

        private const val TAG = "HomeFragment"
        private const val KEYBOARD_THRESHOLD_RATIO = 0.15

        private const val PICTURE_MIN_COUNT = 1
        private const val PICTURE_MAX_COUNT = 6
    }
}