package com.taobao.meta.avatar.widget

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.taobao.meta.avatar.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentTextInputBinding

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
        adapter.observeHeightChanges(object : MessageAdapter.OnHeightChangedListener {
            override fun onHeightChanged(oldHeight: Int, newHeight: Int) {
                if (newHeight > oldHeight) {
                    // 当高度增加时，滚动到底部
                    scrollToBottom()
                } else {
                    // 当高度减少时，保持在当前消息位置
                    if (responsePosition != -1 && responsePosition < adapter.getItemCount()) {
                        binding.recyclerView.scrollToPosition(responsePosition)
                    }
                }
            }
        })
        loadMessages()
    }

    override fun observeViewModel() {
        viewModel.sendData.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
        viewModel.receivedData.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                receivedMessage(message)
            }
        }
        viewModel.receivedStatus.observe(viewLifecycleOwner) {
            if (it) {
                Log.d(TAG, "Received status: $it")
                messageData = null
                responsePosition = -1
                messages = null
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
//                    binding.recyclerView.postDelayed({
//                        // 确保滚动到最新消息
//                        scrollToBottom()
//                    }, 400)
                }
            }
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.postOnAnimation {
            if (adapter.getItemCount() > 0) {
                // 使用LayoutManager的scrollToPositionWithOffset方法
                // 确保最后一个Item完全可见
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

    private fun loadMessages() {
        // 从服务器或本地数据库加载历史消息
        // 示例数据
        val messages: MutableList<MessageData> = ArrayList()

        messages.add(
            MessageData(
                "1",
                "你好！",
                System.currentTimeMillis() - 3600000,
                "user2",
                "张三",
                true
            )
        )
        messages.add(
            MessageData(
                "2",
                "你好，有什么可以帮你的吗？！",
                System.currentTimeMillis() - 3600000,
                currentUserId,
                "张三",
                false
            )
        )

        adapter.setMessages(messages)
        scrollToBottom()
    }

    companion object {
        private const val currentUserId = "user1"
        private const val TAG = "TextInputFragment"
    }
}