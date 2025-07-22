package com.welo.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taobao.meta.avatar.llm.FlowInputs
import com.taobao.meta.avatar.llm.FlowRequest
import com.welo.util.StringUtil
import com.welo.base.KtorFlowNetworkManager
import com.welo.base.TextStreamResponse
import com.welo.entity.MessageData
import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageViewModel : ViewModel() {

    private val TAG = "MessageViewModel"
    private val _sendData = MutableSharedFlow<String>(replay = 0)
    val sendData: SharedFlow<String> = _sendData.asSharedFlow()

    private val _receivedStatus = MutableLiveData<Boolean>()
    val receivedStatus: LiveData<Boolean> = _receivedStatus

    // 请求状态流
    private val _aiResponseFlow = MutableStateFlow<TextStreamResponse>(TextStreamResponse.Idle)
    val aiResponseFlow: StateFlow<TextStreamResponse> = _aiResponseFlow.asStateFlow()

    // 收集到的完整文本
    private val _collectedText = MutableSharedFlow<String>(replay = 0)
    val collectedText: SharedFlow<String> = _collectedText.asSharedFlow()

    private val _requestId = MutableStateFlow(0L)
    val requestId: StateFlow<Long> = _requestId.asStateFlow()

    private var chatSessionJobs = mutableSetOf<Job>()

    // 使用MutableStateFlow管理消息列表
    private val _messageList = MutableStateFlow<List<MessageData>>(emptyList())
    val messageList: StateFlow<List<MessageData>> = _messageList

    /**
     * 添加消息到列表，并根据消息ID进行去重
     *
     * @param message 要添加的消息
     * @param replaceIfExists 如果已存在相同ID的消息，是否替换原消息（默认为false）
     */
    fun addMessage(message: MessageData, replaceIfExists: Boolean = true) {
        LogUtil.d(TAG, "addMessage: $message")

        viewModelScope.launch {
            // 更新消息列表
            val currentList = _messageList.value.toMutableList()

            // 查找是否已存在相同ID的消息
            val existingIndex = currentList.indexOfFirst { it.id == message.id }

            if (existingIndex >= 0) {
                // 消息已存在
                if (replaceIfExists) {
                    // 替换原有消息
                    currentList[existingIndex] = message
                    _messageList.value = currentList
                    LogUtil.d(TAG, "消息已存在，执行替换操作: ${message.id}")
                } else {
                    // 忽略重复消息
                    LogUtil.d(TAG, "忽略重复消息: ${message.id}")
                }
            } else {
                // 添加新消息
                currentList.add(message)
                _messageList.value = currentList
                LogUtil.d(TAG, "添加新消息: ${message.id}")
            }
        }
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            if (message.isNotEmpty()) {
                _sendData.emit(message)
            }
        }
    }

    fun receivedStatus(status: Boolean) {
        _receivedStatus.value = status
    }

    fun receivedMessage(text: String, requestId: Long) {
        Log.d(TAG, "receivedMessage: $text, requestId: $requestId")

        viewModelScope.launch {
            _requestId.value = requestId
            // 重置状态
            _aiResponseFlow.value = TextStreamResponse.Connecting
            val requestBody = FlowRequest(
                files = emptyList(),
                inputs = FlowInputs(
                    input_value = text,
                    session = "Session ${System.currentTimeMillis()}"
                )
            )
            val url =
                "http://192.168.111.10:7860/api/v1/build/e3e07c37-49d7-44b7-be70-1ed17ea44851/flow?event_delivery=direct"
            KtorFlowNetworkManager.Companion.instance.streamTextPost(
                requestId.toString(),
                url,
                requestBody,
            ).catch {
                // 捕获异常并更新状态
                _aiResponseFlow.value = TextStreamResponse.Error(it.message ?: "未知错误")
            }.collect { response ->
                when (response) {
                    is TextStreamResponse.Data -> {
                        // 数据处理移至IO调度器
                        withContext(Dispatchers.IO) {
                            val json = StringUtil.getDecodesData(response.text)
                            val message = StringUtil.parseFlowResponse(json)
                            message?.let {
                                _aiResponseFlow.value = TextStreamResponse.Data(it)
                                withContext(Dispatchers.Main) {
                                    _collectedText.emit(it)
                                }
                            }
                        }
                    }

                    else -> {
                        // 其他状态直接更新
                        _aiResponseFlow.value = response
                    }
                }

            }
        }.apply {
            // 将新任务添加到集合中
            chatSessionJobs.add(this)
        }
    }

    fun closeRequest(requestId: String) {
        // 重置状态
        _aiResponseFlow.value = TextStreamResponse.Idle
        KtorFlowNetworkManager.Companion.instance.cancelRequest(requestId)
        _receivedStatus.value = true
    }

}