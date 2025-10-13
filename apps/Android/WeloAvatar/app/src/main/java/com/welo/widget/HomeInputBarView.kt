package com.welo.widget

import ResourceProvider
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ItemAiToolBinding
import com.taobao.meta.avatar.databinding.ViewChatBarBinding
import com.welo.base.gone
import com.welo.base.visible
import com.welo.constant.Constants
import com.welo.launcher.anim.AnimUtil
import com.welo.launcher.listener.IInputBarListener
import com.welo.launcher.ui.BottomOptionDialog
import com.welo.util.KeyboardUtils

class HomeInputBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    companion object {
        private const val TAG = "HomeInputBarView"
    }

    private val binding = ViewChatBarBinding.inflate(LayoutInflater.from(context), this, true)
    val iconTextButton: ImageView by lazy { binding.iconTextButton }
    val iconVoiceButton: ImageView by lazy { binding.iconVoiceButton }
    val chatBarEditText: TextInputEditText by lazy { binding.chatBarEditText }
    val textInputLayout: TextInputLayout by lazy { binding.textInputLayout }
    val audioInput: TextView by lazy { binding.chatBarAudioText }
    val waveFormView: WaveformView by lazy { binding.waveFormView }

    private var inputBarListener: IInputBarListener? = null

    private var isCancelled = false // 标记是否取消发送
    private var startY = 0f // 记录按下时的Y坐标

    private var toolViewList: MutableList<View> = ArrayList()

    private var currentToolPosition = -1

    private var isShowTools = true

    private val resourceProvider = ResourceProvider.get()
    private val pixelSize6 by lazy {
        resourceProvider.getDimenPixelSize(R.dimen.dp_6)
    }
    private val pixelSize15 by lazy {
        resourceProvider.getDimenPixelSize(R.dimen.dp_15)
    }

    private val pixelSize40 by lazy {
        resourceProvider.getDimenPixelSize(R.dimen.dp_40)
    }
    private val pixelSize42 by lazy {
        resourceProvider.getDimenPixelSize(R.dimen.dp_42)
    }
    private val pixelSize60 by lazy {
        resourceProvider.getDimenPixelSize(R.dimen.dp_60)
    }
    private var inputType = InputType.VOICE

    private val textGroupViews by lazy {
        listOf(textInputLayout, iconVoiceButton)
    }

    private val audioGroupViews by lazy {
        listOf<View>(binding.iconTextButton, binding.chatBarAudioText)
    }

    private val optionDialog by lazy {
        BottomOptionDialog(context = context) { option ->
            inputBarListener?.onFileClick(option)
        }
    }

    private val addImageView by lazy {
        ImageView(context).apply {
            setImageResource(R.drawable.icon_add_pic)
            setOnClickListener { inputBarListener?.onFileClick("upload_image") }
            layoutParams = LinearLayout.LayoutParams(pixelSize42, pixelSize42).apply {
                marginEnd = pixelSize6
                gravity = Gravity.CENTER_VERTICAL
            }

        }
    }

    init {
        setupChartInput()
        setupClickListeners()
        toolsView()
    }

    fun setViewListener(isShowTools: Boolean = true, listener: IInputBarListener) {
        this.isShowTools = isShowTools
        this.inputBarListener = listener
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        iconTextButton.setOnClickListener {
            showTextInputMode()
            inputClick()
        }
        iconVoiceButton.setOnClickListener {
            KeyboardUtils.hideKeyboard(chatBarEditText)
            showVoiceInputMode()
            inputClick()
        }
        audioInput.setOnClickListener {
            inputClick()
        }
        audioInput.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isCancelled = false
                    startY = event.rawY
                    inputBarListener?.onKeyDown()
                    inputClick()
                    waveFormView.visible()
                    waveFormView.startAnimation()
                    binding.chatBarAudioGroup.gone()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 判断是否上滑超过一定距离（例如100dp）
                    val deltaY = startY - event.rawY
                    if (deltaY > pixelSize40) {
                        // 上滑取消
                        if (!isCancelled) {
                            isCancelled = true
                            // 可以在这里更新UI提示用户已取消
                            waveFormView.gone()
                            waveFormView.stopAnimation()
                            binding.chatBarAudioGroup.visible()
                            inputBarListener?.onCancelVoiceInput()
                        }
                    } else {
                        // 恢复正常状态
                        if (isCancelled) {
                            isCancelled = false
                            inputBarListener?.onKeyDown() // 重新开始录音
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isCancelled) {
                        inputBarListener?.onKeyUp() // 正常结束录音
                    } else {
                        inputBarListener?.onCancelVoiceInput() // 取消录音
                    }
                    waveFormView.gone()
                    waveFormView.stopAnimation()
                    binding.chatBarAudioGroup.visible()
                    true
                }

                else -> {
                    waveFormView.gone()
                    waveFormView.stopAnimation()
                    binding.chatBarAudioGroup.visible()
                    false
                }
            }
        }
        chatBarEditText.setOnClickListener {
            inputClick()
        }
        binding.iconPlus.setOnClickListener {
            plusClick()
        }
    }

    fun showTextInputMode() {
        inputType = InputType.TEXT
        AnimUtil.showTextInputModeAnim(textGroupViews, audioGroupViews, width)
    }

    fun showVoiceInputMode() {
        inputType = InputType.VOICE
        AnimUtil.showVoiceInputMode(textGroupViews, audioGroupViews, width)
    }

    private fun plusClick() {
        optionDialog.show(binding.iconPlus)
    }

    /**
     * 创建AI工具View
     */
    private fun toolsView() {
        val toolsList = Constants.aiToolList
        binding.imagePreview.removeAllViews()
        toolViewList.clear()
        binding.imagePreview.layoutParams = binding.imagePreview.layoutParams?.apply {
            height = pixelSize40
        }
        toolsList.forEachIndexed { index, optionItem ->
            val toolItemBinding =
                ItemAiToolBinding.inflate(LayoutInflater.from(context), binding.imagePreview, false)
            toolItemBinding.apply {
                aiToolIcon.setImageResource(optionItem.iconResId)
                aiToolTitle.text = optionItem.title
                root.setOnClickListener {
                    toolItemSelect(index)
                }
            }
            // 设置布局参数，添加水平间距
            val params = LayoutParams(
                LayoutParams.WRAP_CONTENT, // 宽度设为0，配合weight平分空间
                LayoutParams.MATCH_PARENT, // 高度匹配父容器
            ).apply {
                // 除了最后一个元素，其他元素都添加右侧间距
                if (index != toolsList.lastIndex) {
                    marginEnd = pixelSize15
                }
            }

            toolItemBinding.root.layoutParams = params

            toolViewList.add(toolItemBinding.root)

            binding.imagePreview.addView(toolItemBinding.root)
        }
    }

    fun toolItemSelect(position: Int = currentToolPosition) {
        if (!isValidPosition(position)) return

        deselectPreviousItem(position)
        toggleCurrentItem(position)
        val aiOptionItem = if (currentToolPosition >= 0) {
            Constants.aiToolList[currentToolPosition]
        } else {
            null
        }
        val textHint = aiOptionItem?.textHint ?: resourceProvider.getString(R.string.text_hint)
        chatBarEditText.hint = textHint
        inputBarListener?.toolItem(aiOptionItem)
    }

    private fun isValidPosition(position: Int): Boolean {
        return position in 0 until toolViewList.size
    }

    private fun deselectPreviousItem(newPosition: Int) {
        if (currentToolPosition in 0 until toolViewList.size && currentToolPosition != newPosition) {
            toolViewList[currentToolPosition].isSelected = false
        }
    }

    private fun toggleCurrentItem(position: Int) {
        val currentView = toolViewList[position]
        currentView.isSelected = if (currentToolPosition == position) {
            !currentView.isSelected
        } else {
            true
        }

        currentToolPosition = if (currentView.isSelected) position else -1
    }

    fun setupTools() {
        binding.imagePreview.removeAllViews()
        binding.imagePreview.layoutParams = binding.imagePreview.layoutParams?.apply {
            height = pixelSize40
        }
        if (toolViewList.isNotEmpty()) {
            if (binding.iconPlus.isVisible.not()) {
                binding.iconPlus.visible()
            }
            toolViewList.forEach {
                binding.imagePreview.addView(it)
            }
        }
        //隐藏发送按钮
        textInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
    }

    /**
     * 添加图片
     */
    fun previewImage(uris: List<Uri>, uploadInProgress: Boolean = false) {
        if (uris.isEmpty()) return
        val childCount = binding.imagePreview.childCount
        if (childCount > 1 && binding.imagePreview.getChildAt(1) !is DeletableImageView) {
            binding.imagePreview.removeAllViews()
        }

        if (binding.iconPlus.isVisible) {
            binding.iconPlus.gone()
        }

        // 收集已存在的Uri，使用HashSet提高查询效率
        val existingUris = mutableSetOf<Uri>()
        for (i in 0 until binding.imagePreview.childCount) {
            val existingView = binding.imagePreview.getChildAt(i) as? DeletableImageView
            existingView?.getImageUri()?.let { existingUris.add(it) }
        }

        val context = context ?: return
        binding.imagePreview.layoutParams = binding.imagePreview.layoutParams?.apply {
            height = pixelSize60
        }
        uris.forEachIndexed { index, uri ->
            if (existingUris.contains(uri)) {
                return@forEachIndexed // 已存在则跳过
            }
            existingUris.add(uri) // 加入集合，避免后续重复处理
            val deletableImageView = DeletableImageView(context).apply {
                setImageUri(uri)
                setUploadInProgress(uploadInProgress)
                setOnDeleteClickListener {
                    inputBarListener?.deleteImage(uri)
                    binding.imagePreview.removeView(this)
                    updateAddButtonState()
                    if (binding.imagePreview.isEmpty()) {
                        setupTools()
                    }
                }
            }
            // 设置布局参数，添加水平间距
            val params = LayoutParams(
                LayoutParams.WRAP_CONTENT, // 宽度设为0，配合weight平分空间
                LayoutParams.MATCH_PARENT, // 高度匹配父容器
            ).apply {
                // 除了最后一个元素，其他元素都添加右侧间距
                if (index != uris.lastIndex) {
                    marginEnd = pixelSize6
                }
            }

            deletableImageView.layoutParams = params
            binding.imagePreview.addView(deletableImageView)
        }
        updateAddButtonState()
        if (binding.imagePreview.isNotEmpty()) {
            binding.toolsLayout.visible()
        } else {
            setupTools()
        }
    }

    private fun updateAddButtonState() {
        val hasImageView = binding.imagePreview.getChildAt(0) is ImageView
        val realContentCount = getChildImageCount()
        when {
            realContentCount == 0 -> {
                // 没有内容时，移除添加按钮
                binding.imagePreview.removeView(addImageView)
            }

            realContentCount < 6 -> {
                // 有内容且未满时，确保添加按钮存在
                if (!hasImageView) {
                    binding.imagePreview.addView(addImageView, 0)
                }
            }

            else -> {
                // 已满6个内容时，移除添加按钮

                binding.imagePreview.removeView(addImageView)
            }
        }
    }

    fun getChildImageCount(): Int {
        val hasImageView = binding.imagePreview.getChildAt(0) is ImageView
        val realContentCount = binding.imagePreview.childCount - if (hasImageView) 1 else 0
        return realContentCount
    }

    fun updateImageUploadProgress(uri: Uri, progress: Int) {
        // 查找对应的 DeletableImageView 并更新进度
        for (i in 0 until binding.imagePreview.childCount) {
            val view = binding.imagePreview.getChildAt(i) as? DeletableImageView
            if (view?.getImageUri() == uri) {
                view.setUploadProgress(progress)
                break
            }
        }
    }

    fun removeImagePreview(uri: Uri) {
        for (i in 0 until binding.imagePreview.childCount) {
            val view = binding.imagePreview.getChildAt(i) as? DeletableImageView
            if (view?.getImageUri() == uri) {
                binding.imagePreview.removeViewAt(i)
                break
            }
        }
        updateAddButtonState()
        if (binding.imagePreview.isEmpty()) {
            setupTools()
        }
    }

    private fun setupChartInput() {
        // 初始隐藏发送按钮
        updateSendButtonVisibility()
        // 监听文本变化
        chatBarEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonVisibility()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 设置键盘发送键监听
        chatBarEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    fun inputClick() {
        inputBarListener?.onClickListener()
        if (isShowTools && binding.toolsLayout.isVisible.not()) {
            binding.toolsLayout.visible()
        }
    }

    fun hideToolLayout() {
        binding.toolsLayout.gone()
    }

    private fun updateSendButtonVisibility() {
        val hasText = chatBarEditText.text?.isNotBlank() ?: false
        val isCurrentlyVisible = textInputLayout.endIconMode == TextInputLayout.END_ICON_CUSTOM
        if (hasText != isCurrentlyVisible) {
            setSendDrawableState(hasText)
        }
    }

    fun setSendDrawableState(hasShowSend: Boolean) {
        if (hasShowSend) {
            //每次切换endIconMode之后，点击事件会失效，故需要重新设置
            textInputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
            textInputLayout.endIconDrawable =
                ContextCompat.getDrawable(context, R.drawable.icon_send)
            textInputLayout.setEndIconOnClickListener {
                sendMessage()
            }
        } else {
            textInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
        }
    }

    private fun sendMessage() {
        val message = chatBarEditText.text.toString().trim()
        onMessageSent(message)
        chatBarEditText.setText("")
        KeyboardUtils.hideKeyboard(chatBarEditText)
    }

    private fun onMessageSent(message: String) {
        inputBarListener?.textSend(message)
    }

    fun setViewEnable(clickEnable: Boolean) {
        if (inputType == InputType.VOICE) {
            binding.iconTextButton.isClickable = clickEnable
            binding.waveFormView.isClickable = clickEnable
            binding.waveFormView.isEnabled = clickEnable
            binding.chatBarAudioText.isEnabled = clickEnable
        } else {
            binding.chatBarEditText.isClickable = clickEnable
            binding.chatBarEditText.isFocusable = clickEnable
            binding.chatBarEditText.isFocusableInTouchMode = clickEnable
            if (clickEnable) {
                binding.chatBarEditText.requestFocus()
            }
            binding.iconVoiceButton.isClickable = clickEnable
        }
        val alpha = if (clickEnable) 1f else 0.5f
        binding.toolsLayout.alpha = alpha
        binding.waveFormView.alpha = alpha
        binding.iconTextButton.alpha = alpha
        binding.chatBarEditText.alpha = alpha
        binding.iconVoiceButton.alpha = alpha
        binding.chatBarAudioText.alpha = alpha

        binding.toolsLayout.setClickableWithChildren(clickEnable)
    }

    fun ViewGroup.setClickableWithChildren(clickable: Boolean) {
        this.descendantFocusability = if (clickable) {
            FOCUS_AFTER_DESCENDANTS
        } else {
            FOCUS_BLOCK_DESCENDANTS
        }

        this.isClickable = clickable
        this.isEnabled = clickable

        for (i in 0 until this.childCount) {
            val child = this.getChildAt(i)
            child.isClickable = clickable
            child.isEnabled = clickable
            if (child is ViewGroup) {
                child.setClickableWithChildren(clickable)
            }
        }
    }
}

enum class InputType {
    TEXT, VOICE
}