package com.welo.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.WeLoApplication
import com.taobao.meta.avatar.databinding.ActivityChatBinding
import com.welo.base.BaseActivity
import com.welo.base.getParcelableArrayListExtraCompat
import com.welo.base.observeInLifecycleWithDelay
import com.welo.launcher.adapter.ChatAdapter
import com.welo.launcher.listener.ChatActionListener
import com.welo.launcher.repository.AIResponseEvent
import com.welo.launcher.viewmodel.ChatViewModel
import com.welo.manager.ChatInteractionManager
import com.welo.manager.ImageUploadManager
import com.welo.manager.ImageUploadResult
import com.welo.room.table.ChatMessage
import com.welo.util.FileUtil
import com.welo.util.JumpUtil
import com.welo.util.LogUtil
import com.welo.util.PhotoPickerUtils
import com.welo.util.PhotoPickerUtils.handleImageResult
import com.welo.util.PhotoPickerUtils.handlePermissionResult
import kotlinx.coroutines.launch
import java.io.File

class ChatActivity : BaseActivity<ActivityChatBinding, ChatViewModel>() {
    companion object {
        private const val TAG = "ChatActivity"
        const val SCROLL_DEBOUNCE_TIME = 16L // 约60fps
        const val KEYBOARD_THRESHOLD_RATIO = 0.15

        private const val PICTURE_MIN_COUNT = 1
        private const val PICTURE_MAX_COUNT = 6

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_INPUT_TEXT = "input_text"
        const val EXTRA_INPUT_URIS = "input_uris"
        const val EXTRA_AI_OPTION_POSITION = "ai_option_position"
        const val EXTRA_CLICK_TYPE = "click_type"
    }
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var currentPhotoUri: String? = null
    private var currentChatId: Long = 0L
    private lateinit var chatAdapter: ChatAdapter
    private var currentSessionId: Long = -1
    private var isScrollingProgrammatically = false
    private var lastScrollTime = 0L
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var keyboardVisible = false
    private var keyboardHeight = 0
    private val imageUploadManager = ImageUploadManager()
    private var successfulUploads: List<ImageUploadResult.Success> = emptyList()

    private var inputText: String = ""
    private var inputUris: ArrayList<Uri>? = arrayListOf()
    private var aiOptionPosition: Int = 0
    private var clickType: String = "EditText"

    private var isPlusMenuOpen = false


    override fun createBinding(): ActivityChatBinding = ActivityChatBinding.inflate(layoutInflater)

    @RequiresApi(Build.VERSION_CODES.P)
    override fun initView() {

        parseIntent(intent)

        setupRecycleView()
        setupChatBar()

        viewModel.loadAllMessages()

        setupKeyboardListener()
        registerCameraLauncher()

    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun parseIntent(intent: Intent) {
        clickType = intent.getStringExtra(EXTRA_CLICK_TYPE) ?: "EditText"
        currentChatId = ChatInteractionManager.getInstance().currentChatId
        currentSessionId = currentChatId
        inputText = intent.getStringExtra(EXTRA_INPUT_TEXT) ?: ""
        aiOptionPosition = intent.getIntExtra(EXTRA_AI_OPTION_POSITION, 0)
        inputUris = intent.getParcelableArrayListExtraCompat<Uri>(EXTRA_INPUT_URIS)
        if (inputText.isNotEmpty()) {
            viewBinding.chatBar.chatComplete(inputText)
            requestChat(inputText)
        }
        if (inputUris != null && inputUris!!.isNotEmpty()) {
            handleSelectedImage(inputUris)
        }
    }

    private fun setupChatBar() {
        viewBinding.chatBar.setUpClickType(clickType)
        viewBinding.chatBar.setUpOptionItem(aiOptionPosition)
        viewBinding.chatBar.setOnVoiceInputBack { inputText ->
            if (inputText.isEmpty() && successfulUploads.isEmpty()) {
                viewModel.cancelResponse(currentChatId)
            } else {
                requestChat(inputText)
            }
        }
        viewBinding.chatBar.setChatActionListener(object : ChatActionListener {
            override fun onFileClick(clickType: String) {
                LogUtil.d(TAG, "onFileClick clickType:$clickType")
                when (clickType) {
                    "upload_file" -> uploadFile()
                    "upload_image" -> uploadImage()
                    "upload_camera" -> uploadCamera()
                    "audio_call" -> audioCall()
                }
            }

            override fun plusClick() {
                isPlusMenuOpen = true
            }

            override fun deleteImage(uri: Uri) {
                if (successfulUploads.isNotEmpty()) {
                    successfulUploads = successfulUploads.filterNot { it.uri == uri }
                }
                if (successfulUploads.isEmpty()){
                    viewBinding.chatBar.setUpSendIcon(false)
                }
            }

            override fun onCloseWindow() {
                // 处理关闭窗口逻辑（如果需要）
            }
        })
    }

    private fun uploadFile() {
        // 实现文件上传逻辑
    }

    private fun uploadImage() {
        PhotoPickerUtils.openGallery(
            this,
            object : PhotoPickerUtils.OnPhotosSelectedListener {
                @RequiresApi(Build.VERSION_CODES.P)
                override fun onPhotosSelected(bitmaps: List<Bitmap>, uris: List<Uri>) {
                    handleSelectedImage(uris)
                }

                override fun onSelectionIncomplete(minRequired: Int, actualSelected: Int) {
                    // 选择数量不足，提示用户
                    Toast.makeText(
                        this@ChatActivity,
                        "请至少选择 $minRequired 张图片", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSelectionExceeded(maxAllowed: Int, actualSelected: Int) {
                    // 选择数量超出，提示用户
                    Toast.makeText(
                        this@ChatActivity,
                        "最多只能选择 $maxAllowed 张图片", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onPhotosError(errorMsg: String?) {
                    // 处理错误
                    Toast.makeText(
                        this@ChatActivity,
                        "选择图片失败: $errorMsg", Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onSelectionCancelled() {
                    // 用户取消了选择
                    Toast.makeText(
                        this@ChatActivity,
                        "已取消选择", Toast.LENGTH_SHORT
                    ).show()
                }
            },
            minCount = PICTURE_MIN_COUNT,
            maxCount = PICTURE_MAX_COUNT
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerCameraLauncher() {
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageFile = File(currentPhotoUri ?: "")
                if (imageFile.exists()) {
                    val imageUri = FileUtil.getPhotoUri(this,imageFile)
                    // 授予读取权限
                    grantUriPermission(packageName,imageUri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (inputUris == null) {
                        inputUris = arrayListOf()
                    }
                    inputUris?.add(imageUri)
                    handleSelectedImage(inputUris)
                } else {
                    Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this ,"拍照取消", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun uploadCamera() {
        currentPhotoUri = JumpUtil.startCamera(this, cameraLauncher)
    }

    private fun audioCall() {
        val intent = Intent(this, ByteDanceCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun requestChat(inputData: String) {
        if (inputData.isBlank() && successfulUploads.isEmpty()) return

        lifecycleScope.launch {
            WeLoApplication.chatRepository.ensureSessionExists(currentChatId)
            setSession(currentChatId)

            try {
                viewModel.sendMessage(
                    currentChatId,
                    inputData,
                    successfulUploads.map { it.imageUrl })
                successfulUploads = emptyList()
            } catch (e: Exception) {
                LogUtil.e(TAG, "发送消息失败: ${e.message}")
            }
        }
    }

    private fun setupRecycleView() {
        chatAdapter = ChatAdapter(
            onRetry = { retryMessage(it) },
            onMessageLongClick = { showMessageOptions(it) },
            onContentUpdated = { position ->
                if (position == chatAdapter.itemCount - 1) {
                    scheduleScrollToBottom()
                }
            }
        )

        viewBinding.chatRecycleView.apply {
            adapter = chatAdapter
            setItemViewCacheSize(20)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER

            layoutManager = object : LinearLayoutManager(this@ChatActivity) {
                override fun supportsPredictiveItemAnimations(): Boolean = false

                override fun onLayoutChildren(
                    recycler: RecyclerView.Recycler?,
                    state: RecyclerView.State?
                ) {
                    try {
                        super.onLayoutChildren(recycler, state)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "onLayoutChildren error: ${e.message}")
                    }
                }
            }.apply {
                stackFromEnd = true
                reverseLayout = false
            }

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        isScrollingProgrammatically = false
                    }
                }
            })
        }
    }

    private fun shouldAutoScroll(): Boolean {
        if (isScrollingProgrammatically) return true

        val layoutManager =
            viewBinding.chatRecycleView.layoutManager as? LinearLayoutManager ?: return false
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        val totalItemCount = layoutManager.itemCount

        return lastVisiblePosition >= totalItemCount - 3
    }

    private fun scheduleScrollToBottom() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrollTime < SCROLL_DEBOUNCE_TIME) {
            return
        }
        lastScrollTime = currentTime

        if (chatAdapter.itemCount > 0 && shouldAutoScroll()) {
            scrollToBottomImmediately()
        }
    }

    private fun scrollToBottomImmediately() {
        isScrollingProgrammatically = true
        viewBinding.chatRecycleView.post {
            if (chatAdapter.itemCount > 0) {
                (viewBinding.chatRecycleView.layoutManager as? LinearLayoutManager)?.scrollToPosition(
                    chatAdapter.itemCount - 1
                )
                isScrollingProgrammatically = false
            }
        }
    }

    override fun observeViewModel() {
        viewModel.observeAIResponses().observeInLifecycleWithDelay(this, 100) { event ->
            when (event) {
                is AIResponseEvent.Start -> {
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                }

                is AIResponseEvent.Chunk -> {
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
//                    ChatInteractionManager.getInstance().getAudioPlayerUtil()
//                        .playStreamText(event.chunk)
                    val changes = Bundle().apply {
                        putString("content", event.chunk)
                        putBoolean("isIncremental", event.isIncremental)
                        putBoolean("shouldScroll", true)
                    }
                    chatAdapter.notifyItemChanged(chatAdapter.itemCount - 1, changes)
                }

                is AIResponseEvent.Complete -> {
                    LogUtil.d(TAG, "AIResponseEvent Complete sessionId:${event.sessionId}")
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    chatAdapter.setLoadingAIResponse(false)
                    scrollToBottomImmediately()
                    viewBinding.chatBar.chatComplete()
                }

                is AIResponseEvent.Error -> {
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    chatAdapter.setLoadingAIResponse(false)
                    loadSessionMessages(currentSessionId)
                    viewBinding.chatBar.chatComplete()
                }

                is AIResponseEvent.Cancelled -> {
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    loadSessionMessages(currentSessionId)
                }

                is AIResponseEvent.Connected -> {
                    if (event.sessionId != currentSessionId) return@observeInLifecycleWithDelay
                    chatAdapter.setLoadingAIResponse(true)
                    scrollToBottomImmediately()
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
                chatAdapter.submitListWithSession(chatMessages, currentSessionId)
            }
        }
    }

    override fun adaptStatusBarTextColor() {
        // 强制使用浅色文字（适合深色背景）
        setStatusBarTextColor(false)
    }

    private fun retryMessage(message: ChatMessage) {
        val content = if (message.senderType == "AI") "重新生成回复" else message.content
        viewModel.sendMessage(currentSessionId, content)
    }

    private fun showMessageOptions(message: ChatMessage) {
        // 实现消息长按菜单（复制、删除、重发等）
    }

    private fun scrollToBottom() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScrollTime < SCROLL_DEBOUNCE_TIME) {
            return
        }
        lastScrollTime = currentTime
        viewBinding.chatRecycleView.post {
            if (chatAdapter.itemCount > 0) {
                viewBinding.chatRecycleView.smoothScrollToPosition(chatAdapter.itemCount - 1)
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
                    LogUtil.d(TAG, "loadSessionMessages messages:${messages.size}")
                    chatAdapter.submitListWithSession(messages, sessionId)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "加载消息失败: ${e.message}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleSelectedImage(uri: List<Uri>?) {
        uri ?: return

        lifecycleScope.launch {
            try {
                // 显示图片预览（初始状态为上传中）
                viewBinding.chatBar.previewImage(uri, uploadInProgress = true)
                // 开始上传图片
                val uploadResults = imageUploadManager.uploadImages(uri) { uri, progress ->
                    viewBinding.chatBar.updateImageUploadProgress(uri, progress)
                }

                // 检查所有图片是否上传成功
                successfulUploads = uploadResults.filterIsInstance<ImageUploadResult.Success>()
                val failedUploads = uploadResults.filterIsInstance<ImageUploadResult.Failed>()

                if (failedUploads.isNotEmpty()) {
                    // 处理上传失败的图片
                    Toast.makeText(
                        this@ChatActivity,
                        "${failedUploads.size}张图片上传失败",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 移除上传失败的图片
                    failedUploads.forEach { result ->
                        viewBinding.chatBar.removeImagePreview(result.uri)
                    }
                }
                // 所有图片上传成功，更新发送状态
                if (successfulUploads.isNotEmpty()){
                    viewBinding.chatBar.setUpSendIcon(true)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "处理图片失败: ${e.message}")
                Toast.makeText(this@ChatActivity, "处理图片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupKeyboardListener() {
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            LogUtil.d(TAG, "GlobalLayoutListener triggered :$isPlusMenuOpen")
            if (isPlusMenuOpen){
                isPlusMenuOpen = false
                return@OnGlobalLayoutListener
            }
            val rect = Rect()
            viewBinding.root.getWindowVisibleDisplayFrame(rect)
            val marginBottom = viewBinding.chatBar.marginBottom
            val screenHeight = viewBinding.root.height
            val keypadHeight = screenHeight - rect.bottom - marginBottom

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

            // 原有的滚动逻辑
            if (keyboardVisible) {
                viewBinding.chatRecycleView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }

        globalLayoutListener?.let {
            viewBinding.root.viewTreeObserver.addOnGlobalLayoutListener(it)
        }
    }

    private fun adjustLayoutForKeyboard(visible: Boolean, height: Int) {
        if (visible) {
            // 键盘弹出时，将 ChatBarView 上移
            viewBinding.chatBar.translationY = -height.toFloat()

            // 同时调整 RecyclerView 的底部边距，确保内容不被遮挡
            val params = viewBinding.chatRecycleView.layoutParams as? ViewGroup.MarginLayoutParams
            params?.bottomMargin = height
            viewBinding.chatRecycleView.layoutParams = params
        } else {
            // 键盘隐藏时，恢复原位
            viewBinding.chatBar.translationY = 0f

            val params = viewBinding.chatRecycleView.layoutParams as? ViewGroup.MarginLayoutParams
            params?.bottomMargin = 0
            viewBinding.chatRecycleView.layoutParams = params
        }
    }

    override fun onDestroy() {
        globalLayoutListener?.let {
            viewBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        viewModel.cancelResponse(currentChatId)
        super.onDestroy()
    }

    // 在 onActivityResult 中处理
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        handleImageResult(requestCode, resultCode, data, this)
    }

    // 在 onRequestPermissionsResult 中处理权限
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handlePermissionResult(requestCode, grantResults, this)
    }
}