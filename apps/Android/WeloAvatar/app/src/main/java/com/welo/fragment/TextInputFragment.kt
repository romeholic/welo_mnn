package com.welo.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.taobao.meta.avatar.databinding.FragmentTextInputBinding
import com.welo.launcher.adapter.MessageAdapter
import com.welo.base.BaseFragment
import com.welo.base.observeInLifecycleWithDelay
import com.welo.entity.MessageData
import com.welo.viewmodel.MessageViewModel
import kotlinx.coroutines.launch

/**
 * 文本输入
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class TextInputFragment : BaseFragment<FragmentTextInputBinding, MessageViewModel>() {
    private val adapter: MessageAdapter by lazy { MessageAdapter() }

    private var messageData: MessageData? = null
    private var responsePosition: Int = -1
    private var messages: MutableList<MessageData>? = null
    private var currentChatId: Long = 0L

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTextInputBinding {
        return FragmentTextInputBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.recyclerView.apply {
            adapter = this@TextInputFragment.adapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // 确保新消息在底部
            }
            setHasFixedSize(true)
        }
        restoreMessages()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            //输入的内容
            viewModel.sendData.observeInLifecycleWithDelay(viewLifecycleOwner){ message ->
                if (message.isNotEmpty()) {
                    sendMessage(message)
                }
            }
            // 收集AI响应数据
            viewModel.collectedText.observeInLifecycleWithDelay(viewLifecycleOwner,100L){ message ->
                if (message.isNotEmpty()) {
                    receivedMessage(message)
                }
            }
            viewModel.requestId.observeInLifecycleWithDelay(viewLifecycleOwner) { value ->
                if (currentChatId != value) {
                    currentChatId = value
                    // 清空之前的消息数据
                    messageData = null
                    responsePosition = -1
                    messages = null
                }
            }
            viewModel.receivedStatus.observe(viewLifecycleOwner){
                if (messageData != null) {
                    viewModel.addMessage(messageData!!)
                }
            }
        }
    }

    private fun receivedMessage(message: String) {
        if (message.isEmpty()) return
        if (messageData == null) {
            messageData = MessageData(
                id = System.currentTimeMillis().toString(),
                text = message,
                timestamp = System.currentTimeMillis(),
                senderId = currentUserId,
                senderName = "张三",
                isSent = false
            )
            adapter.addMessage(messageData!!)
            responsePosition = adapter.getItemCount() - 1
        } else {
            if (responsePosition == -1 || responsePosition >= adapter.getItemCount()) {
                responsePosition = adapter.getItemCount() - 1
            }
            if (messages==null){
                messages = adapter.getList().toMutableList()
            }
            messages?.let {
                if (responsePosition < it.size) {
                    messageData!!.text = message
                    it[responsePosition] = messageData!!
                    // 通知适配器数据已更改，带payload优化
                    adapter.notifyItemChanged(responsePosition, "textChanged")
                }
                scrollToBottom()
            }
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.postOnAnimation {
            if (adapter.getItemCount() > 0) {
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(adapter.getItemCount() - 1, 0)
            }
        }
    }

    private fun sendMessage(message: String, isSend: Boolean = true) {
        if (message.isNotEmpty()) {
            val messageData = MessageData(
                id = System.currentTimeMillis().toString(),
                text = message,
                timestamp = System.currentTimeMillis(),
                senderId = currentUserId,
                senderName = "张三",
                isSent = isSend
            )
            adapter.addMessage(messageData)
            viewModel.addMessage(messageData)
            scrollToBottom()
        }
    }
    private fun restoreMessages() {
        // 从ViewModel获取消息列表并设置到适配器
        val messages = viewModel.messageList.value
        if (messages.isNotEmpty()) {
            adapter.setMessages(messages.toMutableList())
            scrollToBottom()
        }
    }
    companion object {
        private const val currentUserId = "user1"
        private const val TAG = "TextInputFragment"
    }
}