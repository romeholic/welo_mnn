package com.welo.base.okhttp

import com.localebro.okhttpprofiler.OkHttpProfilerInterceptor
import com.welo.base.net.TextStreamResponse
import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.time.delay
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class OkHttpChunkedManager(
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {
    companion object {
        private const val TAG = "OkHttpChunkedManager"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
//        .addInterceptor(OkHttpProfilerInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 创建 HTTP Chunked 流式响应
     */
    fun createChunkedStream(
        url: String,
        requestBody: String,
        headers: Map<String, String> = emptyMap()
    ): Flow<TextStreamResponse> = callbackFlow {
        LogUtil.d(TAG, "Creating chunked stream to: $url")

        val request = Request.Builder()
            .url(url)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
                addHeader("Accept", "text/plain")
                addHeader("Cache-Control", "no-cache")
                addHeader("Connection", "keep-alive")
            }
            .build()

        val call = okHttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP error: ${response.code} - ${response.message}"
                    LogUtil.e(TAG, errorMsg)
                    try {
                        trySend(TextStreamResponse.Error(errorMsg,response.code))
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to send error: ${e.message}")
                    }
                    close()
                    return
                }

                response.body?.let { body ->
                    try {
                        body.byteStream().bufferedReader().use { reader ->
                            LogUtil.d(TAG, "Chunked connection established")
                            try {
                                trySend(TextStreamResponse.Connected)
                            } catch (e: Exception) {
                                LogUtil.e(TAG, "Failed to send connected: ${e.message}")
                            }

                            val buffer = CharArray(8192)
                            var charsRead: Int
                            val stringBuilder = StringBuilder()

                            while (reader.read(buffer).also { charsRead = it } != -1) {
                                if (call.isCanceled()) {
                                    LogUtil.d(TAG, "Call cancelled during streaming")
                                    trySend(TextStreamResponse.Cancelled)
                                    break
                                }
                                if (charsRead > 0) {
                                    stringBuilder.append(buffer, 0, charsRead)
                                    var lineEnd: Int
                                    while (stringBuilder.indexOf('\n').also { lineEnd = it } != -1) {
                                        val line = stringBuilder.substring(0, lineEnd).trim()
                                        if (line.isNotBlank()) {
                                            try {
                                                trySend(TextStreamResponse.Data(line))
                                            } catch (e: Exception) {
                                                LogUtil.e(TAG, "Failed to send data: ${e.message}")
                                                // 继续处理后续数据，不中断
                                            }
                                        }
                                        stringBuilder.delete(0, lineEnd + 1)
                                    }
                                }

                                if (call.isCanceled()) {
                                    break
                                }
                            }

                            // 处理剩余的缓冲区内容
                            if (stringBuilder.isNotEmpty()) {
                                val remainingContent = stringBuilder.toString().trim()
                                if (remainingContent.isNotBlank()) {
                                    try {
                                        trySend(TextStreamResponse.Data(remainingContent))
                                    } catch (e: Exception) {
                                        LogUtil.e(TAG, "Failed to send remaining data: ${e.message}")
                                    }
                                }
                            }

                            LogUtil.d(TAG, "Chunked stream completed")
                            try {
                                trySend(TextStreamResponse.Completed)
                            } catch (e: Exception) {
                                LogUtil.e(TAG, "Failed to send completed: ${e.message}")
                            }
                            close()
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Stream reading error: ${e.message}", e)
                        try {
                            trySend(TextStreamResponse.Error("Stream error: ${e.message}"))
                        } catch (sendError: Exception) {
                            LogUtil.e(TAG, "Failed to send stream error: ${sendError.message}")
                        }
                        close(e)
                    }
                } ?: run {
                    val errorMsg = "Empty response body"
                    LogUtil.e(TAG, errorMsg)
                    try {
                        trySend(TextStreamResponse.Error(errorMsg))
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to send empty body error: ${e.message}")
                    }
                    close()
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                LogUtil.e(TAG, "Connection failed: ${e.message}", e)
                try {
                    trySend(TextStreamResponse.Error("Connection failed: ${e.message}"))
                } catch (sendError: Exception) {
                    LogUtil.e(TAG, "Failed to send connection error: ${sendError.message}")
                }
                close(e)
            }
        })

        awaitClose {
            LogUtil.d(TAG, "Cancelling chunked call:${call.isCanceled()}")
            if (!call.isCanceled()) {
                try {
                    trySend(TextStreamResponse.Cancelled)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to send cancelled event: ${e.message}")
                }
                call.cancel()
            }
        }
    }.flowOn(coroutineContext)
}