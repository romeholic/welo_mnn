package com.welo.base.net


import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlin.coroutines.CoroutineContext

class SSEManager(
    private val okHttpClient: OkHttpClient,
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {
    companion object {
        private const val TAG = "SSEManager"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * 创建 SSE 流式连接
     */
    fun createSSEStream(
        url: String,
        requestBody: String,
        headers: Map<String, String> = emptyMap()
    ): Flow<TextStreamResponse> = callbackFlow {
        LogUtil.d(TAG, "Creating SSE stream to: $url")

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()

        val eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    LogUtil.d(TAG, "SSE connection opened")
                    trySend(TextStreamResponse.Connected)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    LogUtil.d(TAG, "SSE event received: $data")
                    if (data.isNotBlank()) {
                        trySend(TextStreamResponse.Data(data))
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    LogUtil.d(TAG, "SSE connection closed")
                    trySend(TextStreamResponse.Completed)
                    close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    val errorMessage = t?.message ?: "Unknown SSE error"
                    LogUtil.e(TAG, "SSE connection failed: $errorMessage")
                    trySend(TextStreamResponse.Error(errorMessage))
                    close(t ?: Exception(errorMessage))
                }
            })

        awaitClose {
            LogUtil.d(TAG, "Closing SSE stream")
            eventSource.cancel()
        }
    }.flowOn(coroutineContext)
}