package com.welo.base.okhttp


import android.util.Log
import com.localebro.okhttpprofiler.OkHttpProfilerInterceptor
import com.welo.base.net.TextStreamResponse
import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OkHttpStreamManager {
    companion object {
        val instance: OkHttpStreamManager by lazy { OkHttpStreamManager() }
        private const val TAG = "OkHttpStreamManager"
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(OkHttpProfilerInterceptor()) // 可以安全使用 Profiler
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val requestMap = ConcurrentHashMap<String, Call>()

    /**
     * 发送流式 POST 请求
     */
    fun streamTextPost(
        requestId: String,
        url: String,
        body: String,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap()
    ): Flow<TextStreamResponse> = channelFlow {
        LogUtil.d(TAG, "Starting stream request: $requestId")

        try {
            send(TextStreamResponse.Connecting)

            val requestBody = RequestBody.create(
                contentType.toMediaType(),
                body
            )

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()

            val call = okHttpClient.newCall(request)
            requestMap[requestId] = call

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!isClosedForSend) {
                        launch {
                            if (call.isCanceled()) {
                                send(TextStreamResponse.Cancelled)
                                LogUtil.d(TAG, "Request cancelled: $requestId")
                            } else {
                                val errorMsg = "Network error: ${e.message ?: "Unknown error"}"
                                send(TextStreamResponse.Error(errorMsg))
                                LogUtil.e(TAG, "Request failed: $requestId", e)
                            }
                            close()
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!isActive) {
                        response.close()
                        return
                    }

                    launch {
                        try {
                            if (!response.isSuccessful) {
                                val errorBody = response.body?.string() ?: "No error body"
                                throw IOException("HTTP ${response.code}: $errorBody")
                            }

                            send(TextStreamResponse.Connected)

                            response.body?.let { body ->
                                processResponseStream(requestId, body, this@channelFlow)
                            } ?: run {
                                throw IOException("Response body is null")
                            }

                            send(TextStreamResponse.Completed)
                        } catch (e: Exception) {
                            if (isActive) {
                                val errorMsg = when (e) {
                                    is IOException -> "Stream error: ${e.message}"
                                    else -> "Unexpected error: ${e.message}"
                                }
                                send(TextStreamResponse.Error(errorMsg))
                                LogUtil.e(TAG, "Stream processing error: $requestId", e)
                            }
                        } finally {
                            response.close()
                            close()
                        }
                    }
                }
            })

            // 等待流结束或取消
            awaitClose {
                requestMap.remove(requestId)?.cancel()
            }
        } catch (e: Exception) {
            val errorMsg = "Request setup failed: ${e.message}"
            send(TextStreamResponse.Error(errorMsg))
            LogUtil.e(TAG, "Request setup error: $requestId", e)
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 发送流式 Multipart POST 请求
     */
    fun streamMultipartPost(
        requestId: String,
        url: String,
        formFields: Map<String, String> = emptyMap(),
        fileParts: List<FilePart> = emptyList(),
        headers: Map<String, String> = emptyMap()
    ): Flow<TextStreamResponse> = channelFlow {
        send(TextStreamResponse.Connecting)

        try {
            val multipartBuilder = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)

            // 添加表单字段
            formFields.forEach { (key, value) ->
                multipartBuilder.addFormDataPart(key, value)
            }

            // 添加文件
            fileParts.forEach { filePart ->
                val fileBody = RequestBody.create(
                    filePart.contentType.toMediaType(),
                    filePart.file
                )
                multipartBuilder.addFormDataPart(
                    filePart.partName,
                    filePart.file.name,
                    fileBody
                )
            }

            val requestBody = multipartBuilder.build()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()

            val call = okHttpClient.newCall(request)
            requestMap[requestId] = call

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!isClosedForSend) {
                        launch {
                            if (call.isCanceled()) {
                                send(TextStreamResponse.Cancelled)
                            } else {
                                send(TextStreamResponse.Error("Network error: ${e.message}"))
                            }
                            close()
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!isActive) {
                        response.close()
                        return
                    }

                    launch {
                        try {
                            if (!response.isSuccessful) {
                                throw IOException("HTTP ${response.code}")
                            }

                            send(TextStreamResponse.Connected)
                            response.body?.let { processResponseStream(requestId, it, this@channelFlow) }
                            send(TextStreamResponse.Completed)
                        } catch (e: Exception) {
                            if (isActive) {
                                send(TextStreamResponse.Error(e.message ?: "Stream error"))
                            }
                        } finally {
                            response.close()
                            close()
                        }
                    }
                }
            })

            awaitClose {
                requestMap.remove(requestId)?.cancel()
            }
        } catch (e: Exception) {
            send(TextStreamResponse.Error(e.message ?: "Request error"))
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 处理响应流
     */
    private suspend fun processResponseStream(
        requestId: String,
        body: ResponseBody,
        channel: kotlinx.coroutines.channels.SendChannel<TextStreamResponse>
    ) {
        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        var line: String?

        try {
            while (reader.readLine().also { line = it } != null) {
                if (!channel.isClosedForSend) {
                    line?.takeIf { it.isNotBlank() }?.let { text ->
                        channel.send(TextStreamResponse.Data(text))
                        LogUtil.d(TAG, "Received data: $text")
                    }
                } else {
                    break
                }
            }
        } finally {
            reader.close()
        }
    }

    /**
     * 取消请求
     */
    fun cancelRequest(requestId: String) {
        LogUtil.d(TAG, "Cancelling request: $requestId")
        requestMap[requestId]?.cancel()
        requestMap.remove(requestId)
    }

    /**
     * 取消所有请求
     */
    fun cancelAllRequests() {
        requestMap.values.forEach { it.cancel() }
        requestMap.clear()
    }
}

// FilePart 数据类（如果尚未定义）
data class FilePart(
    val partName: String,
    val file: java.io.File,
    val contentType: String = "application/octet-stream"
)