package com.welo.widget

import ResourceProvider
import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.core.view.isEmpty
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ChatBarLayoutBinding
import com.welo.base.gone
import com.welo.base.visible
import com.welo.callback.IChat
import com.welo.constant.Constants
import com.welo.launcher.listener.ChatActionListener
import com.welo.launcher.ui.AiToolsDialog
import com.welo.launcher.ui.BottomOptionDialog
import com.welo.manager.ChatInteractionManager
import com.welo.util.JumpUtil
import com.welo.util.KeyboardUtils
import com.welo.util.LogUtil
import androidx.core.view.isNotEmpty
import com.WeLoApplication.Companion.applicationScope
import com.WeLoApplication.Companion.chatRepository
import com.welo.constant.Constants.AGENT_TYPE_WELO_CHAT
import com.welo.constant.Constants.AGENT_TYPE_WELO_GUIDANCE
import com.welo.constant.Constants.FEATURE_TOUR_KEY
import com.welo.launcher.entity.AIOptionItem
import com.welo.util.FeatureTourUtil
import com.welo.util.LanguageDetector
import com.welo.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs), IChat {
    companion object {
        const val WINDOW_TYPE_ACTIVITY = 0
        const val WINDOW_TYPE_FLOAT = 1
        private const val TAG = "ChatBarView"
    }

    @IntDef(WINDOW_TYPE_ACTIVITY, WINDOW_TYPE_FLOAT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class ChatBarViewType

    private var windowType = WINDOW_TYPE_ACTIVITY
    private var isSendModel: Boolean = false
    private val binding = ChatBarLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    private var actionListener: ChatActionListener? = null
    private var onInputCallBack: ((inputText: String) -> Unit)? = null

    val detector = LanguageDetector()
    var currentToolOption: AIOptionItem? = null

    init {
        initViews()
    }

    fun setParentWinType(@ChatBarViewType windowType: Int) {
        this.windowType = windowType
    }

    private fun initViews() {

        binding.ivPlus.setOnClickListener { plusClick() }
        binding.aiToolView.setOnClickListener { openAiTools() }

        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleInputChange(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        binding.ivSend.setOnClickListener { handleSendClick() }
        binding.ivAudio.setOnClickListener { startVoiceRecording() }

        binding.ivBack.setOnClickListener { stopRecording(true) }
        binding.ivOk.setOnClickListener { stopRecording(false) }
    }

    private fun openAiTools() {
        AiToolsDialog(context = context, Constants.aiToolList) { option, position ->
            currentToolOption = option
            binding.etInput.hint = option.textHint
            binding.aiToolView.setAiTools(option.title, option.iconResId)
        }.show()
    }

    private fun handleSendClick() {
        LogUtil.d(TAG, "handleSendClick: isSendModel=$isSendModel")
        if (isSendModel) {
            isSendModel = false
            binding.ivSend.setImageResource(R.drawable.icon_stop)
            viewClickable(false)
            recognitionResult(getInputText())
            clearInput()
        } else {
            binding.ivSend.setImageResource(R.drawable.icon_send_default)
            onInputCallBack?.invoke("")
            viewClickable(true)
        }
        setUpOptionItem(0)
    }

    private fun viewClickable(isClickable: Boolean) {
        val alpha = if (isClickable) {
            1.0f
        } else {
            0.3f
        }
        binding.etInput.isClickable = isClickable
        binding.etInput.isFocusable = isClickable
        binding.etInput.isEnabled = isClickable
        binding.etInput.isFocusableInTouchMode = isClickable
        binding.aiToolView.isClickable = isClickable
        binding.ivPlus.isClickable = isClickable
        binding.ivAudio.isClickable = isClickable
        if (isClickable) {
            binding.etInput.requestFocus()
        }

        binding.aiToolView.alpha = alpha
        binding.ivPlus.alpha = alpha
        binding.ivAudio.alpha = alpha
    }

    private fun startVoiceRecording() {

        KeyboardUtils.hideKeyboard(binding.etInput)
        binding.layoutChatInput.gone()
        binding.layoutImagePreview.gone()
        binding.layoutVoiceInput.visible()
        binding.viewWaveform.startAnimation()

        val success = ChatInteractionManager.getInstance().startRecord()
        if (!success) {
            binding.etInput.visible()
            Toast.makeText(context, "启动录音失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(isBack: Boolean = false) {
        binding.viewWaveform.stopAnimation()
        binding.layoutVoiceInput.gone()
        binding.layoutImagePreview.gone()
        binding.layoutChatInput.visible()
        ChatInteractionManager.getInstance().stopRecord()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
//        ChatInteractionManager.getInstance().setChatListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
//        ChatInteractionManager.getInstance().removeChatListener()
    }

    private fun handleInputChange(text: String) {
        val shouldShowSend = text.isNotEmpty()
        if (shouldShowSend != isSendModel) {
            setUpSendIcon(shouldShowSend)
        }
    }

    fun setUpSendIcon(isSend: Boolean) {
        isSendModel = isSend
        val iconRes = if (isSend) R.drawable.icon_send else R.drawable.icon_send_default
        binding.ivSend.setImageResource(iconRes)
    }

    private fun plusClick() {
        actionListener?.plusClick()
        BottomOptionDialog(context = context) { option ->
            actionListener?.onFileClick(option)
        }.show(binding.ivPlus)
    }

    fun setUpOptionItem(position: Int) {
        if (position in Constants.aiToolList.indices) {
            val option = Constants.aiToolList[position]
            currentToolOption = option
            binding.etInput.hint = option.textHint
            binding.aiToolView.setAiTools(option.title, option.iconResId)
        }
    }

    fun setUpClickType(clickType: String) {
        LogUtil.d(TAG, "setUpClickType: $clickType")
        when (clickType) {
            "Audio" -> {
                startVoiceRecording()
            }

            "EditText" -> {
                KeyboardUtils.focusAndShowKeyboard(binding.etInput)
            }
        }
    }

    fun chatComplete(sendText: String = "") {
        if (sendText.isNotEmpty()){
            handleInputChange(sendText)
        }
        handleSendClick()
    }

    fun previewImage(uris: List<Uri>, uploadInProgress: Boolean = false) {
        if (uris.isEmpty()) return

        // 收集已存在的Uri，使用HashSet提高查询效率
        val existingUris = mutableSetOf<Uri>()
        for (i in 0 until binding.imagePreview.childCount) {
            val existingView = binding.imagePreview.getChildAt(i) as? DeletableImageView
            existingView?.getImageUri()?.let { existingUris.add(it) }
        }

        val context = context ?: return
        val horizontalSpacing = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_6)
        uris.forEachIndexed { index, uri ->
            if (existingUris.contains(uri)) {
                return@forEachIndexed // 已存在则跳过
            }
            existingUris.add(uri) // 加入集合，避免后续重复处理
            val deletableImageView = DeletableImageView(context).apply {
                setImageUri(uri)
                setUploadInProgress(uploadInProgress)
                setOnDeleteClickListener {
                    actionListener?.deleteImage(uri)
                    binding.imagePreview.removeView(this)
                    if (binding.imagePreview.isEmpty()) {
                        binding.layoutImagePreview.gone()
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
                    marginEnd = horizontalSpacing
                }
            }

            deletableImageView.layoutParams = params
            binding.imagePreview.addView(deletableImageView)
        }
        // 最终根据实际内容决定显示/隐藏预览布局
        if (binding.imagePreview.isNotEmpty()) {
            binding.layoutImagePreview.visible()
        } else {
            binding.layoutImagePreview.gone()
        }
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

        if (binding.imagePreview.isEmpty()) {
            binding.layoutImagePreview.gone()
        }
    }

    fun setChatActionListener(listener: ChatActionListener) {
        this.actionListener = listener
    }

    fun setOnVoiceInputBack(listener: (inputText: String) -> Unit) {
        onInputCallBack = listener
    }

    fun getInputText(): String {
        return binding.etInput.text.toString()
    }

    fun clearInput() {
        binding.etInput.text.clear()
    }

    override fun startRecord() {}
    override fun stopRecord() {}

    override fun recognitionResult(inputText: String) {
        LogUtil.d(TAG, "recognitionResult: inputText=$inputText")
        stopRecording()
        var guide = PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)
        LogUtil.d(TAG, "recognitionResult: inputText=$inputText,is guide mode =$guide")
        if(guide){
            inputComplete(inputText)
            return
        }
        if (inputText.isEmpty() && binding.imagePreview.isEmpty()) {
            return
        }

        var prompt = inputText

        currentToolOption?.let {
            Constants.agentType = Constants.AGENT_TYPE_WELO_TOOLS
            when (it.title) {
                "AI翻译" -> {
                    detector.detectLanguage(inputText) { languageCode ->
                        LogUtil.d(TAG, "detectLanguage: $languageCode")
                        when (languageCode) {
                            "zh" -> {
                                prompt = "请帮我将以下文本翻译成英文:$inputText"
                            }

                            "en" -> {
                                prompt = "请帮我将以下文本翻译成中文:$inputText"
                            }
                        }
                        inputComplete(prompt)
                    }
                }
                "AI搜索" -> {
                    prompt = "搜索一下：$inputText"
                    inputComplete(prompt)
                }
                "AI作图" -> {
                    prompt = "AI作图：$inputText"
                    inputComplete(prompt)
                }
                "AI写作" -> {
                    prompt = "AI写作：$inputText"
                    inputComplete(prompt)
                }
                else -> {
                    Constants.agentType = AGENT_TYPE_WELO_CHAT
                    inputComplete(prompt)
                }
            }
        }

        if (windowType == WINDOW_TYPE_FLOAT) {
            actionListener?.onCloseWindow()
        }
    }

    private fun inputComplete(inputText: String) {
        onInputCallBack?.invoke(inputText)
        binding.imagePreview.removeAllViews()
        binding.layoutImagePreview.gone()
    }

    override fun stopAnswer() {}
    override fun stopRequest() {}
}