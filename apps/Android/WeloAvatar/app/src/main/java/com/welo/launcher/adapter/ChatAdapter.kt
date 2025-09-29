package com.welo.launcher.adapter

import ResourceProvider
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.WeLoApplication
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ItemChatMessageBinding
import com.welo.base.gone
import com.welo.base.visible
import com.welo.constant.Constants
import com.welo.constant.Constants.FEATURE_TOUR_KEY
import com.welo.room.table.ChatMessage
import com.welo.util.LogUtil
import com.welo.util.PreferenceUtil
import com.welo.util.StringUtil
import com.welo.util.TimeUtils
import com.welo.widget.DeletableImageView
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

class ChatAdapter(
    private val onRetry: (ChatMessage) -> Unit = {},
    private val onMessageLongClick: (ChatMessage) -> Unit = {},
    private val onContentUpdated: (Int) -> Unit = {},
    private val shouldAutoScroll: (() -> Boolean) = { true }
) : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val TAG = "ChatAdapter"
        private const val ANIMATION_DURATION_PER_CHAR = 30L
        private const val PAYLOAD_CONTENT = "content"
        private const val PAYLOAD_STATUS = "status"
        private const val PAYLOAD_TIMESTAMP = "timestamp"
        private const val PAYLOAD_IS_INCREMENTAL = "isIncremental"
    }

    private var isLoadingAIResponse = false
    private var currentSessionId: Long = -1
    private  var guideMode = PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        updateGuideLayout(binding)
        return MessageViewHolder(binding)
    }
    /**
     * 绑定ViewHolder到指定位置的数据
     */
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        val isLastItemLoading = isLoadingAIResponse && position == itemCount - 1
        holder.bind(message, isLastItemLoading)
    }

    /**
     * 处理带payload的绑定，用于局部更新
     */
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val message = getItem(position)
            val isLastItemLoading = isLoadingAIResponse && position == itemCount - 1
            val changes = payloads[0] as Bundle
            holder.bindPartial(changes, message, isLastItemLoading)
        }
    }

    /**
     * 提交消息列表并设置当前会话ID
     */
    fun submitListWithSession(messages: List<ChatMessage>, sessionId: Long) {
        currentSessionId = sessionId
        submitList(messages)
    }

    /**
     * 设置AI响应加载状态
     */
    fun setLoadingAIResponse(loading: Boolean) {
        LogUtil.d(TAG, "setLoadingAIResponse: $loading")
        isLoadingAIResponse = loading
        if (itemCount > 0) {
            notifyItemChanged(itemCount - 1)
        }
    }
    fun getLoadingAIResponse() = isLoadingAIResponse

    private fun updateGuideLayout(binding: ItemChatMessageBinding){
        if (guideMode) {
            binding.aiMessageContent.setBackgroundResource(0)
            binding.aiMessageContent.setTextSize(18f)
            binding.aiMessageContent.setPadding(0)
            binding.userMessageTime.gone()
            binding.aiMessageTime.gone()
        } else {
            binding.aiMessageContent.setTextSize(14f)
            binding.aiMessageContent.setPadding(12)
            binding.aiMessageContent.setBackgroundResource(R.drawable.out_put_background)
            binding.userMessageTime.visible()
            binding.aiMessageTime.visible()
        }
    }
    /**
     * ViewHolder类，负责管理单个消息项的视图
     */
    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // 添加 Markwon 实例
        private val markwon by lazy {
            Markwon.builder(itemView.context)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(CoilImagesPlugin.create(itemView.context))
                .build()
        }

        private var currentDisplayedContent: String = ""
        private var currentMessageId: Long = -1
        private var currentAnimator: ValueAnimator? = null

        /**
         * 绑定消息数据到视图
         */
        fun bind(message: ChatMessage, isLastItemLoading: Boolean) {
            currentMessageId = message.id
            currentDisplayedContent = message.content
            currentAnimator?.cancel()

            when (message.senderType) {
                "USER" -> bindUserMessage(message)
                "AI" -> bindAIMessage(message, isLastItemLoading)
            }

            setupLongClickListener(message)
        }

        /**
         * 处理局部更新
         */
        @SuppressLint("SetTextI18n")
        fun bindPartial(changes: Bundle, message: ChatMessage, isLastItemLoading: Boolean) {
            var shouldScrollToBottom = false

            if (changes.containsKey(PAYLOAD_CONTENT)) {
                val newContentChunk = changes.getString(PAYLOAD_CONTENT) ?: ""
                val isIncremental = changes.getBoolean(PAYLOAD_IS_INCREMENTAL, false)

                if (message.id == currentMessageId && isIncremental) {
                    handleIncrementalContentUpdate(newContentChunk, message)
                    shouldScrollToBottom = adapterPosition == itemCount - 1
                }
            }

            if (changes.containsKey(PAYLOAD_STATUS)) {
                updateMessageStatus(
                    changes.getString(PAYLOAD_STATUS) ?: message.status,
                    message,
                    isLastItemLoading
                )
            }

            if (changes.containsKey(PAYLOAD_TIMESTAMP)) {
                updateTimestamp(
                    changes.getLong(PAYLOAD_TIMESTAMP),
                    message.senderType
                )
            }

            if (shouldScrollToBottom && adapterPosition >= 0) {
                onContentUpdated(adapterPosition)
            }
        }

        /**
         * 处理增量内容更新
         */
        private fun handleIncrementalContentUpdate(newContentChunk: String, message: ChatMessage) {
            val targetTextView = when (message.senderType) {
                "USER" -> binding.userMessageContent
                else -> {
                    binding.aiMessageContent.visible()
                    binding.aiTypingIndicator.gone()
                    updateGuideLayout(binding)
                    binding.aiMessageContent
                }
            }

            val currentText = targetTextView.text.toString()
            if (!currentText.endsWith(newContentChunk) || newContentChunk.length > 1) {
                targetTextView.append(newContentChunk)
                currentDisplayedContent += newContentChunk
            } else {
                targetTextView.text = newContentChunk
                currentDisplayedContent = newContentChunk
            }
        }

        /**
         * 更新消息状态
         */
        private fun updateMessageStatus(
            status: String,
            message: ChatMessage,
            isLastItemLoading: Boolean
        ) {
            when (message.senderType) {
                "USER" -> updateUserMessageStatus(status)
                "AI" -> updateAIMessageStatus(status, isLastItemLoading, message)
            }
        }

        /**
         * 更新时间戳显示
         */
        private fun updateTimestamp(timestamp: Long, senderType: String) {
            val timeText = TimeUtils.formatTime(timestamp)
            when (senderType) {
                "USER" -> binding.userMessageTime.text = timeText
                "AI" -> binding.aiMessageTime.text = timeText
            }
        }

        /**
         * 设置长按监听器
         */
        private fun setupLongClickListener(message: ChatMessage) {
            binding.root.setOnLongClickListener {
                onMessageLongClick(message)
                true
            }
        }

        /**
         * 绑定用户消息
         */
        private fun bindUserMessage(message: ChatMessage) {
            LogUtil.d(TAG, "bindUserMessage content:${message}")

            with(binding) {
                showUserLayout()
                hideAILayout()
                setupMessageContent(message, userMessageContent, userMessageImageScroll,userMessageImageContainer)
                if (!guideMode) userMessageTime.text = TimeUtils.formatTime(message.timestamp)
                updateUserMessageStatus(message.status)
                scrollToBottomIfNeeded(adapterPosition)
            }
        }

        /**
         * 绑定AI消息
         */
        private fun bindAIMessage(message: ChatMessage, showTyping: Boolean) {
            with(binding) {
                hideUserLayout()
                showAILayout(message.content)
                setupMessageContent(message, aiMessageContent, null,aiMessageImageContainer,aiTypingIndicator)
                if (!guideMode) aiMessageTime.text = TimeUtils.formatTime(message.timestamp)

                // 处理打字机效果
                currentAnimator?.cancel()
                val shouldAnimate =
                    message.status == "GENERATING" && showTyping && message.content.isNotEmpty()

                if (shouldAnimate) {
                    animateTextReveal(aiMessageContent, message.content, adapterPosition)
                } else {
                    renderMarkdown(aiMessageContent, message.content)
                }
                updateAIMessageStatus(message.status, showTyping, message)
            }
        }
        // 新增方法：渲染 Markdown
        private fun renderMarkdown(textView: TextView, content: String) {
            markwon.setMarkdown(textView, content)
        }
        /**
         * 设置消息内容（文本、图片等）
         */
        @SuppressLint("SetTextI18n")
        private fun setupMessageContent(
            message: ChatMessage,
            contentTextView: TextView,
            imageScrollView: HorizontalScrollView? = null,
            imageLayout: LinearLayout,
            aiLoadingLayout: LinearLayout? = null
        ) {
            when (message.messageType) {
                "TEXT" -> {
                    if (message.content.isEmpty()){
                        return
                    }else{
                        aiLoadingLayout?.gone()
                    }
                    contentTextView.visible()
                    contentTextView.text = message.content
                    if (message.senderType == "AI"){
                        updateGuideLayout(binding)
                    }
                    imageScrollView?.gone()
                }

                "IMAGE" -> {
                    contentTextView.gone()
                    imageScrollView?.visible()
                    imageLayout.visible()
                    loadImage(message.imageUris, imageLayout)
                }

                "TEXT_WITH_IMAGE" -> {
                    if (message.content.isEmpty()){
                        return
                    }else{
                        aiLoadingLayout?.gone()
                    }
                    contentTextView.visible()
                    contentTextView.text = message.content
                    imageScrollView?.visible()
                    imageLayout.visible()
                    loadImage(message.imageUris, imageLayout)
                }
            }
        }

        /**
         * 显示用户消息布局
         */
        private fun showUserLayout() {
            binding.userMessageLayout.visibility = View.VISIBLE
        }

        /**
         * 隐藏用户消息布局
         */
        private fun hideUserLayout() {
            binding.userMessageLayout.visibility = View.GONE
        }

        /**
         * 显示AI消息布局
         */
        private fun showAILayout(message: String) {
            LogUtil.d(TAG, "showAILayout: $message")
            binding.aiMessageLayout.visible()
            if (message.isEmpty()){
                binding.aiMessageContent.gone()
                return
            }
            if (!binding.aiMessageContent.isVisible){
                binding.aiMessageContent.visible()
            }
        }

        /**
         * 隐藏AI消息布局
         */
        private fun hideAILayout() {
            binding.aiMessageLayout.visibility = View.GONE
        }

        /**
         * 打字机效果动画
         */
        private fun animateTextReveal(textView: TextView, fullText: String, position: Int) {
            textView.text = ""
            currentAnimator?.cancel()

            currentAnimator = ValueAnimator.ofInt(0, fullText.length).apply {
                duration = fullText.length * ANIMATION_DURATION_PER_CHAR
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Int
                    if (progress <= fullText.length) {
//                        textView.text = fullText.substring(0, progress)
                        val partialText = fullText.substring(0, progress)
                        renderMarkdown(textView, partialText)
                        scrollToBottomIfNeeded(position)
                    }
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) {
                        renderMarkdown(textView, fullText)
                    }

                    override fun onAnimationCancel(animation: Animator) {}
                    override fun onAnimationRepeat(animation: Animator) {}
                })
                start()
            }
        }

        /**
         * 如果需要则滚动到底部
         */
        private fun scrollToBottomIfNeeded(position: Int) {
            if (position == itemCount - 1) {
                itemView.post {
                    (itemView.parent as? RecyclerView)?.smoothScrollToPosition(position)
                }
            }
        }

        /**
         * 更新用户消息状态
         */
        private fun updateUserMessageStatus(status: String) {
            LogUtil.d(TAG, "updateUserMessageStatus: $status")
            with(binding) {
                when (status) {
                    "SENDING" -> {
                        userMessageProgress.visibility = View.VISIBLE
                        userMessageStatus.visibility = View.GONE
                    }

                    "FAILED" -> {
                        userMessageProgress.visibility = View.GONE
                        userMessageStatus.visibility = View.VISIBLE
                        userMessageStatus.text = "发送失败"
                    }

                    else -> {
                        userMessageProgress.visibility = View.GONE
                        userMessageStatus.visibility = View.GONE
                    }
                }
            }
        }

        /**
         * 更新AI消息状态
         */
        private fun updateAIMessageStatus(
            status: String,
            showTyping: Boolean,
            message: ChatMessage
        )  {
            with(binding) {
                when (status) {
                    "GENERATING" -> {
                        aiTypingIndicator.visibility = if (showTyping) View.VISIBLE else View.GONE
                        aiMessageError.visibility = View.GONE
                    }

                    "FAILED" -> {
                        aiTypingIndicator.visibility = View.GONE
                        aiMessageError.visibility = View.VISIBLE
                        aiMessageError.text = "回复失败，点击重试"
                        aiMessageError.setOnClickListener { onRetry(message) }
                    }

                    else -> {
                        aiTypingIndicator.visibility = View.GONE
                        aiMessageError.visibility = View.GONE
                    }
                }
            }
        }

        /**
         * 视图回收时的清理工作
         */
        fun onViewRecycled() {
            currentAnimator?.cancel()
            currentAnimator = null
        }
    }

    /**
     * 加载图片
     */
    private fun loadImage(imageUri: String?, imageLayout: LinearLayout) {
        // 先清空容器，避免重复添加
        imageLayout.removeAllViews()

        val imageUris = StringUtil.stringToList(imageUri)
        if (imageUris.isEmpty()) {
            imageLayout.gone()
            return
        }
        val horizontalSpacing = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_7)
        imageUris.forEachIndexed { index, uri ->
            val path = if (uri.startsWith("http:")) {
                uri
            } else {
                Constants.servicesImagePath(uri)
            }
            LogUtil.d(TAG, "loadImage: $path")
            val deletableImageView = DeletableImageView(imageLayout.context).apply {
                setImageUrl(path, false)
                visibility = View.VISIBLE
            }
            val params = LayoutParams(
                LayoutParams.WRAP_CONTENT, // 宽度设为0，配合weight平分空间
                LayoutParams.MATCH_PARENT, // 高度匹配父容器
            ).apply {
                // 除了最后一个元素，其他元素都添加右侧间距
                if (index != imageUris.lastIndex) {
                    marginEnd = horizontalSpacing
                }
            }

            deletableImageView.layoutParams = params
            imageLayout.addView(deletableImageView)
        }
        imageLayout.visible()
    }

    /**
     * DiffUtil回调类，用于计算列表差异
     */
    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.status == newItem.status &&
                    oldItem.timestamp == newItem.timestamp &&
                    oldItem.senderType == newItem.senderType &&
                    oldItem.messageType == newItem.messageType
        }

        override fun getChangePayload(oldItem: ChatMessage, newItem: ChatMessage): Any? {
            val diff = Bundle()

            if (oldItem.content != newItem.content) {
                diff.putString(PAYLOAD_CONTENT, newItem.content)
                diff.putBoolean(PAYLOAD_IS_INCREMENTAL, true)
            }
            if (oldItem.status != newItem.status) {
                diff.putString(PAYLOAD_STATUS, newItem.status)
            }
            if (oldItem.timestamp != newItem.timestamp) {
                diff.putLong(PAYLOAD_TIMESTAMP, newItem.timestamp)
            }

            return if (diff.size() > 0) diff else null
        }
    }

    override fun onViewRecycled(holder: MessageViewHolder) {
        super.onViewRecycled(holder)
        holder.onViewRecycled()
    }
}