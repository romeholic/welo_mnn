package com.welo.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taobao.meta.avatar.llm.FlowInputs
import com.taobao.meta.avatar.llm.FlowRequest
import com.welo.base.net.NetworkManager
import com.welo.base.net.TextStreamResponse
import com.welo.constant.Constants
import com.welo.entity.MessageData
import com.welo.launcher.entity.TaskBean
import com.welo.storage.TokenManager
import com.welo.util.LogUtil
import com.welo.util.StringUtil
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
    private val _sendData = MutableSharedFlow<String>(replay = 3)
    val sendData: SharedFlow<String> = _sendData.asSharedFlow()

    private val _receivedStatus = MutableLiveData<Boolean>()
    val receivedStatus: LiveData<Boolean> = _receivedStatus

    // 请求状态流
    private val _aiResponseFlow = MutableStateFlow<TextStreamResponse>(TextStreamResponse.Idle)
    val aiResponseFlow: StateFlow<TextStreamResponse> = _aiResponseFlow.asStateFlow()

    // 收集到的完整文本
    private val _collectedText = MutableStateFlow<String>("")
    val collectedText: StateFlow<String> = _collectedText.asStateFlow()

    private val _requestId = MutableStateFlow(0L)
    val requestId: StateFlow<Long> = _requestId.asStateFlow()

    private var chatSessionJobs = mutableSetOf<Job>()

    // 使用MutableStateFlow管理消息列表
    private val _messageList = MutableStateFlow<List<MessageData>>(emptyList())
    val messageList: StateFlow<List<MessageData>> = _messageList


    private val _taskList = MutableStateFlow<List<TaskBean>>(emptyList())
    val taskList: StateFlow<List<TaskBean>> = _taskList
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

        }
    }

    fun receivedStatus(status: Boolean) {
        _receivedStatus.value = status
    }

    fun receivedMessage(text: String, requestId: Long) {
        Log.d(TAG, "receivedMessage: $text, requestId: $requestId}")

        viewModelScope.launch {
            if (text.isNotEmpty()) {
                _sendData.emit(text)
            }
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
            // TODO: 后续根据用户选择不同的Agent选择不同的URL，调用｛AgentManager.getSelectedAgent()｝
            val url = Constants.getLlmPath()
            NetworkManager.Companion.instance.streamTextPost(
                requestId.toString(),
                url,
                requestBody,
            ) {
                append("Authorization", "Bearer ${TokenManager.getToken()}")
            }.catch {
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
                                _collectedText.emit(it)
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
        _aiResponseFlow.value = TextStreamResponse.Cancelled
        NetworkManager.Companion.instance.cancelRequest(requestId)
        _receivedStatus.value = true
    }

}