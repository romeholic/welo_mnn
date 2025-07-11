package com.taobao.meta.avatar.widget

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.cancel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class KtorFlowNetworkManager {
    companion object {
        val instance: KtorFlowNetworkManager by lazy { KtorFlowNetworkManager() }
        private const val TAG = "KtorFlowNetworkManager"
    }

    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }


        install(HttpTimeout) {
            connectTimeoutMillis = 10000
            requestTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }

        engine {
            config {
                followRedirects(true)
            }
        }
    }
    private val requestMap = ConcurrentHashMap<String, Job>()
    // 创建一个用于发送取消事件的 MutableSharedFlow

    /**
     * 发送GET请求并返回Flow
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param params 请求参数
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun get(
        requestId: String,
        url: String,
        params: Map<String, String>? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.get(url) {
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            headers(headers)
        }
    }

    /**
     * 发送POST请求并返回Flow
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param params 请求参数
     * @param body 请求体
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun post(
        requestId: String,
        url: String,
        params: Map<String, String>? = null,
        body: Any? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.post(url) {
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            headers(headers)
            body?.let { setBody(it) }
        }
    }

    /**
     * 发送PUT请求并返回Flow
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param params 请求参数
     * @param body 请求体
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun put(
        requestId: String,
        url: String,
        params: Map<String, String>? = null,
        body: Any? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.put(url) {
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            headers(headers)
            body?.let { setBody(it) }
        }
    }

    /**
     * 发送DELETE请求并返回Flow
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param params 请求参数
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun delete(
        requestId: String,
        url: String,
        params: Map<String, String>? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.delete(url) {
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            headers(headers)
        }
    }

    /**
     * 以流的方式通过POST请求读取文本数据
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param body 请求体，可以是字符串、表单数据或JSON对象等
     * @param contentType 请求内容类型，默认application/json
     * @param charset 字符集，默认UTF-8
     * @return 返回包含文本块的Flow
     */
    fun streamTextPost(
        requestId: String,
        url: String,
        body: Any,
        contentType: ContentType = ContentType.Application.Json,
        charset: String = "UTF-8",
    ): Flow<TextStreamResponse> = flow {
        emit(TextStreamResponse.Connecting)
        // 发送请求
        val response: HttpResponse = client.post(url) {
            this.contentType(contentType)
            setBody(body)
            headers {
                append("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZjU2NTdkYi04OGExLTRkZWUtOTJmNC1kY2UwOGU4OGU1YTIiLCJ0eXBlIjoiYWNjZXNzIiwiZXhwIjoxNzgzMDcwMDU1fQ.9BNiYmvpvMifCMdVQyCWEhFyML-IpN9IY_iDFJWFAKg")
            }
        }
        Log.d(TAG, "Response status: ${response.status.value}")
        if (!response.status.isSuccess()) {
            throw IOException("HTTP error ${response.status.value}")
        }
        emit(TextStreamResponse.Connected)
        // 处理响应流
        val channel = response.bodyAsChannel()
        val reader = BufferedReader(InputStreamReader(channel.toInputStream(), charset))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.takeIf { it.isNotBlank() }?.let {
                    emit(TextStreamResponse.Data(it))
                }
            }
            emit(TextStreamResponse.Completed)
        } catch (e: Exception) {
            Log.d(TAG, "Error in streamTextPost catch: ${e.message}")
            if (currentCoroutineContext().isActive) {
                emit(TextStreamResponse.Error(e.message ?: "Stream error"))
            }
            throw e
        } finally {
            reader.close()
            channel.cancel() // 确保流被关闭
        }
    }
        .catch { e ->
            Log.d(TAG, "Error in streamTextPost: ${e.message}")
            if (e is CancellationException) {
                emit(TextStreamResponse.Cancelled)
            } else {
                emit(TextStreamResponse.Error(e.message ?: "Network error"))
            }
        }
        .flowOn(Dispatchers.IO)
        .onStart {
            val job = currentCoroutineContext().job
            requestMap[requestId] = job

            job.invokeOnCompletion {throwable ->
                requestMap.remove(requestId)
            }
        }
    /**
     * 取消指定ID的请求
     * @param requestId 请求唯一标识
     */
    fun cancelRequest(requestId: String) {
        Log.d(TAG, "Cancelling request with ID: $requestId")
        requestMap[requestId]?.cancel("Request cancelled by user")
        requestMap.remove(requestId)
    }

    /**
     * 取消所有请求
     */
    fun cancelAllRequests() {
        requestMap.values.forEach { it.cancel("All requests cancelled by user") }
        requestMap.clear()
    }

    private fun createFlowRequest(
        requestId: String,
        requestBlock: suspend () -> HttpResponse
    ): Flow<ApiResponse> = flow {
        emit(ApiResponse.Loading)

        val response = requestBlock()

        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            emit(ApiResponse.Success(body))
        } else {
            emit(ApiResponse.Error(response.status.value.toString(), response.bodyAsText()))
        }
    }
        .catch { e ->
            emit(ApiResponse.Error("Exception", e.message ?: "Unknown error"))
        }
        .flowOn(Dispatchers.IO)
        .onStart {
            val job = currentCoroutineContext().job
            requestMap[requestId] = job

            job.invokeOnCompletion {
                requestMap.remove(requestId)
            }
        }
}

/**
 * API响应的密封类
 */
sealed class ApiResponse {
    object Loading : ApiResponse()
    data class Success(val data: String) : ApiResponse()
    data class Error(val code: String, val message: String) : ApiResponse()
}
/**
 * 下载进度的密封类
 */
sealed class DownloadProgress {
    object Loading : DownloadProgress()
    object Started : DownloadProgress()
    data class Progress(val percentage: Int) : DownloadProgress()
    data class Completed(val file: File) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
/**
 * 文本流响应的密封类
 */
sealed class TextStreamResponse {
    object Idle : TextStreamResponse()
    object Connecting : TextStreamResponse()
    object Connected : TextStreamResponse()
    data class Data(val text: String) : TextStreamResponse()
    object Completed : TextStreamResponse()
    object Cancelled : TextStreamResponse()
    data class Error(val message: String) : TextStreamResponse()
}
