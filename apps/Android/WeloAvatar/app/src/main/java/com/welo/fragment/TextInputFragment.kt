package com.welo.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.welo.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentTextInputBinding
import com.welo.adapter.MessageAdapter
import com.welo.entity.MessageData
import com.welo.viewmodel.MessageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var currentChatId: String = ""

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
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            //输入的内容
            viewModel.sendData.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ) .onEach { message ->
                withContext(Dispatchers.Main) {
                    if (message.isNotEmpty()) {
                        sendMessage(message)
                    }
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

            // 收集AI响应数据
            viewModel.collectedText.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ).onEach { message ->
                    delay(100) // 模拟延迟，确保消息收集完成
                    withContext(Dispatchers.Main) {
                        if (message.isNotEmpty()) {
                            receivedMessage(message)
                        }
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)
            viewModel.requestId.collect { string ->
                if (currentChatId != string) {
                    currentChatId = string
                    // 清空之前的消息数据
                    messageData = null
                    responsePosition = -1
                    messages = null
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
            scrollToBottom()
        }
    }

    companion object {
        private const val currentUserId = "user1"
        private const val TAG = "TextInputFragment"
    }
}