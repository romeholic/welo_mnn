package com.welo.launcher.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.WeLoApplication
import com.welo.base.net.TextStreamResponse
import com.welo.base.okhttp.OkChatRepository
import com.welo.launcher.repository.AIResponseEvent
import com.welo.room.table.ChatMessage
import com.welo.room.table.ChatSession
import com.welo.util.LogUtil
import com.welo.util.StringUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val repository = WeLoApplication.Companion.chatRepository

    private val chatRepository = OkChatRepository()

    // 观察AI回复流
    fun observeAIResponses(): SharedFlow<AIResponseEvent> {
        return repository.aiResponseStream
    }

    // 发送文本消息
    fun sendMessage(sessionId: Long, content: String, imageUrls: List<String>? = null) {
        val hasContent = content.isNotEmpty()
        val hasImages = imageUrls.isNullOrEmpty().not()

        val messageType = when {
            hasContent && hasImages -> "TEXT_WITH_IMAGE"
            hasImages -> "IMAGE"
            else -> "TEXT"
        }
        repository.sendMessage(sessionId, content, messageType = messageType, imageUris = imageUrls)
    }

    fun observeStream() {
        viewModelScope.launch {
            chatRepository.sendMessage("画一个帅哥")
                .collect { response ->
                    when (response) {
                        TextStreamResponse.Connecting -> {
                            LogUtil.d(TAG, "observeStream: 连接中")
                        }

                        TextStreamResponse.Connected -> {
                            LogUtil.d(TAG, "observeStream: 已连接")
                        }

                        is TextStreamResponse.Data -> {
                            // 处理数据流
                            val text = response.text
                            val json = StringUtil.getDecodesData(text)
                            // 2. 解析JSON数据，提取有效回复内容
                            var processedContent = StringUtil.parseFlowResponse(json) ?: ""
                            LogUtil.d(TAG, "observeStream: 数据流: $processedContent")
                        }

                        TextStreamResponse.Completed -> {
                            // 流完成
                            LogUtil.d(TAG, "observeStream: 流完成")
                        }

                        is TextStreamResponse.Error -> {
                            // 处理错误
                            LogUtil.d(TAG, "observeStream: 错误: ${response.message}")
                        }

                        TextStreamResponse.Cancelled -> {
                            // 请求被取消
                            LogUtil.d(TAG, "observeStream: 请求被取消")
                        }

                        else -> {
                            LogUtil.d(TAG, "observeStream: 未知状态")
                        }
                    }
                }
        }
    }

    //发送图文消息
    fun sendTextWithImageMessage(sessionId: Long, content: String, imageUri: List<Uri>) {
        repository.sendMessageWithImage(sessionId, content, imageUri)
    }

    // 取消AI回复
    fun cancelResponse(sessionId: Long) {
        LogUtil.d(TAG, "cancelResponse: $sessionId")
        repository.cancelAIResponse(sessionId)
    }

    // 实时观察会话消息
    fun observeSessionMessages(sessionId: Long): Flow<List<ChatMessage>> {
        return repository.observeSessionMessages(sessionId)
    }

    // 获取单次会话消息（用于初始加载）
    suspend fun getSessionMessagesOnce(sessionId: Long): List<ChatMessage> {
        return repository.getSessionMessages(sessionId)
    }

    fun allMessage(): Flow<List<ChatSession>> = repository.allSessions

    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val allMessages: StateFlow<List<ChatMessage>> = _allMessages

    // 加载所有消息
    fun loadAllMessages() {
        viewModelScope.launch {
            try {
                val messages = repository.getAllMessagesOnce()
                _allMessages.value = messages
            } catch (e: Exception) {
                // 处理错误
                LogUtil.e("ChatViewModel", "加载消息失败", e)
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.clearDatabase()
        }
    }
}