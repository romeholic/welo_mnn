package com.taobao.meta.avatar.widget

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.taobao.meta.avatar.llm.FlowInputs
import com.taobao.meta.avatar.llm.FlowRequest
import com.taobao.meta.avatar.utils.StringUtil
import io.ktor.http.headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MessageViewModel : ViewModel() {

    private val TAG = "MessageViewModel"
    private val _sendData = MutableLiveData<String>()
    val sendData: LiveData<String> = _sendData

    private val _receivedData = MutableLiveData<String>()
    val receivedData: LiveData<String> = _receivedData

    private val _receivedStatus = MutableLiveData<Boolean>()
    val receivedStatus: LiveData<Boolean> = _receivedStatus

    // 请求状态流
    private val _aiResponseFlow = MutableStateFlow<TextStreamResponse>(TextStreamResponse.Idle)
    val aiResponseFlow: StateFlow<TextStreamResponse> = _aiResponseFlow.asStateFlow()

    // 收集到的完整文本
    private val _collectedText = MutableLiveData<String>("")
    val collectedText: LiveData<String> = _collectedText

    private var chatSessionJobs = mutableSetOf<Job>()

    fun sendMessage(message: String) {
        if (message.isNotEmpty()) {
            _sendData.value = message
        }
    }

    fun receivedMessage(message: String) {
        if (message.isNotEmpty()) {
            _receivedData.value = message
        }
    }
    fun receivedStatus(status: Boolean) {
        _receivedStatus.value = status
    }

    fun receivedMessage(text: String,requestId: String){
        viewModelScope.launch {
            // 重置状态
            _aiResponseFlow.value = TextStreamResponse.Connecting
            _collectedText.value = ""
            val requestBody = FlowRequest(
                files = emptyList(),
                inputs = FlowInputs(
                    input_value = text,
                    session = "Session ${System.currentTimeMillis()}"
                )
            )
            val url =
                "http://192.168.111.10:7860/api/v1/build/e3e07c37-49d7-44b7-be70-1ed17ea44851/flow?event_delivery=direct"
            KtorFlowNetworkManager.instance.streamTextPost(
                requestId,
                url,
                requestBody,
            ).catch {
                // 捕获异常并更新状态
                _aiResponseFlow.value = TextStreamResponse.Error(it.message ?: "未知错误")
            }.collect { response ->
                when (response) {
                    is TextStreamResponse.Connecting -> {
                        _aiResponseFlow.value = response
                    }

                    is TextStreamResponse.Connected -> {
                        _aiResponseFlow.value = response
                    }
                    is TextStreamResponse.Data -> {
                        // 数据处理移至IO调度器
                        withContext(Dispatchers.IO) {
                            val json = StringUtil.getDecodesData(response.text)
                            val message = StringUtil.parseFlowResponse(json)

                            message?.let {
                                _aiResponseFlow.value = TextStreamResponse.Data(it)
                                withContext(Dispatchers.Main){
                                    _receivedData.value = it
                                    // 累加收集的文本
                                    _collectedText.value += it
                                }
                            }
                        }
                    }

                    is TextStreamResponse.Completed -> {
                        _aiResponseFlow.value = response
                    }

                    is TextStreamResponse.Cancelled -> {
                        _aiResponseFlow.value = response
                    }

                    is TextStreamResponse.Error -> {
                        _aiResponseFlow.value = response
                    }
                    is TextStreamResponse.Idle -> Unit
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
        KtorFlowNetworkManager.instance.cancelRequest(requestId)
        _collectedText.value = ""
        _receivedStatus.value = true
    }

}