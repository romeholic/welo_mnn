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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

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

    private val _requestId = MutableStateFlow("")
    val requestId: StateFlow<String> = _requestId.asStateFlow()

    private var chatSessionJobs = mutableSetOf<Job>()

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

    fun receivedMessage(text: String, requestId: String){
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
            KtorFlowNetworkManager.instance.streamTextPost(
                requestId,
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
                                withContext(Dispatchers.Main){
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
        KtorFlowNetworkManager.instance.cancelRequest(requestId)
        _receivedStatus.value = true
    }

}