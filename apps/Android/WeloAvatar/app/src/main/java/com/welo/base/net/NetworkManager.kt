package com.welo.base.net

import android.util.Log
import com.google.gson.Gson
import com.welo.base.okhttp.OkHttpChunkedManager
import com.welo.entity.FilePart
import com.welo.util.LogUtil
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import io.ktor.http.headersOf
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
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException


class NetworkManager {
    companion object {
        val instance: NetworkManager by lazy { NetworkManager() }
        private const val TAG = "NetworkManager"
    }
    // 添加 SSE 专用的 OkHttpClient
    private val sseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // 对于 SSE，读超时设为 0（无限）
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val sseManager = SSEManager(sseClient)

    // 添加 Chunked 流管理器
    private val chunkedManager = OkHttpChunkedManager()

    // 基础HttpClient配置
    private val baseClientConfig = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10000
            requestTimeoutMillis = 180*1000
            socketTimeoutMillis = 180*1000
        }

        engine {
            config {
                followRedirects(true)
            }
        }
    }
    // 带日志的客户端
    private val client: HttpClient = baseClientConfig.config {
        install(Logging) {
            level = LogLevel.ALL
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    Log.d(TAG, "Ktor Log: $message")
                }
            }
        }
    }

    // 不带日志的客户端，用于streamTextPost
    private val clientWithoutLogging: HttpClient = baseClientConfig.config {
        install(Logging) {
            level = LogLevel.NONE // 关闭日志
        }
    }
    private val requestMap = ConcurrentHashMap<String, Job>()

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
        cookies: Map<String, String>? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.post(url) {
            contentType(ContentType.Application.Json)
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            cookies?.let { cookieMap ->
                val cookieString = cookieMap.entries.joinToString("; ") {
                    "${it.key}=${it.value}"
                }
                header("Cookie", cookieString)
            }
            headers(headers)
            body?.let { setBody(it) }
        }
    }

    /**
     * 发送表单数据的POST请求并返回Flow
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param formFields 表单字段
     * @param params 请求参数
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun postForm(
        requestId: String,
        url: String,
        formFields: Map<String, String>,
        params: Map<String, String>? = null,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.post(url) {
            contentType(ContentType.MultiPart.FormData)
            params?.forEach { (key, value) ->
                parameter(key, value)
            }
            headers(headers)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        formFields.forEach { (key, value) ->
                            append(key, value)
                        }
                    }
                ))
        }
    }

    /**
     * 上传图片到服务器
     * @param requestId 请求唯一标识
     * @param url 上传URL
     * @param file 图片文件
     * @param fileName 文件名
     * @param mimeType MIME类型
     * @param formFields 额外的表单字段
     * @param headers 请求头
     * @return 返回包含ApiResponse的Flow
     */
    fun uploadImage(
        requestId: String,
        url: String,
        file: File,
        fileName: String,
        mimeType: String = "image/jpeg",
        formFields: Map<String, String> = emptyMap(),
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<ApiResponse> = createFlowRequest(requestId) {
        client.post(url) {
            headers(headers)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // 添加文件
                        append(
                            "file", // 字段名，根据服务器API调整
                            file.readBytes(),
                            headersOf(
                                "Content-Type" to listOf(mimeType),
                                "Content-Disposition" to listOf("filename=\"$fileName\"")
                            )
                        )
                        // 添加额外的表单字段
                        formFields.forEach { (key, value) ->
                            append(key, value)
                        }
                    }
                )
            )
        }
    }
    /**
     * 上传图片到服务器（带进度）
     * @param requestId 请求唯一标识
     * @param url 上传URL
     * @param file 图片文件
     * @param fileName 文件名
     * @param mimeType MIME类型
     * @param formFields 额外的表单字段
     * @param headers 请求头
     * @param onProgress 上传进度回调 (已上传字节数, 总字节数)
     * @return 返回包含ApiResponse的Flow
     */
    fun uploadImageForProgress(
        requestId: String,
        url: String,
        file: File,
        fileName: String,
        mimeType: String = "image/jpeg",
        formFields: Map<String, String> = emptyMap(),
        headers: HeadersBuilder.() -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Flow<ApiResponse> = flow {
        emit(ApiResponse.Loading)

        val response: HttpResponse = client.post(url) {
            headers(headers)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // 添加文件
                        append(
                            "file", // 字段名，根据服务器API调整
                            file.readBytes(),
                            headersOf(
                                "Content-Type" to listOf(mimeType),
                                "Content-Disposition" to listOf("filename=\"$fileName\"")
                            )
                        )
                        // 添加额外的表单字段
                        formFields.forEach { (key, value) ->
                            append(key, value)
                        }
                    }
                )
            )

            // 添加进度监听
            onUpload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }

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
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<TextStreamResponse> = flow {
        // 检查是否已被取消
        if (!currentCoroutineContext().isActive) {
            emit(TextStreamResponse.Cancelled)
            return@flow
        }
        emit(TextStreamResponse.Connecting)
        val response: HttpResponse = clientWithoutLogging.post(url) {
            this.contentType(contentType)
            setBody(body)
            headers(headers)
        }
        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.bodyAsText()
            } catch (e: Exception) {
                "无法读取错误响应体"
            }
            throw IOException("HTTP error ${response.status.value}: $errorBody")
        }
//        LogUtil.d(TAG,"streamTextPost response:$response")
        emit(TextStreamResponse.Connected)
        // 处理响应流
        val channel = response.bodyAsChannel()
        LogUtil.d(TAG,"streamTextPost channel:$channel")
        val reader = BufferedReader(InputStreamReader(channel.toInputStream(), charset))
        try {
            val buffer = CharArray(8192)
            var charsRead: Int
            val stringBuilder = StringBuilder()
            while (reader.read(buffer).also { charsRead = it } != -1) {
                if (charsRead > 0) {
                    stringBuilder.append(buffer, 0, charsRead)

                    // 按行处理，避免半行数据
                    var lineEnd: Int
                    while (stringBuilder.indexOf('\n').also { lineEnd = it } != -1) {
                        val line = stringBuilder.substring(0, lineEnd).trim()
                        if (line.isNotBlank()) {
                            LogUtil.d(TAG, "streamTextPost line:$line")
                            emit(TextStreamResponse.Data(line))
                        }
                        stringBuilder.delete(0, lineEnd + 1)
                    }
                }

                // 检查是否被取消
                if (!currentCoroutineContext().isActive) {
                    break
                }
            }
            emit(TextStreamResponse.Completed)
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                val errorMsg = "流读取错误: ${e.message ?: "未知错误"}"
                emit(TextStreamResponse.Error(errorMsg))
            }
            throw e
        } finally {
            reader.close()
            channel.cancel() // 确保流被关闭
        }
    }
        .catch { e ->
            when (e) {
                is CancellationException -> {
                    emit(TextStreamResponse.Cancelled)
                    LogUtil.d(TAG, "请求被取消: $requestId")
                }

                is SocketTimeoutException -> {
                    val errorMsg = "连接超时: ${e.message}"
                    emit(TextStreamResponse.Error(errorMsg))
                    LogUtil.w(TAG, "连接超时: $requestId")
                }

                is ConnectException -> {
                    val errorMsg = "连接失败: ${e.message}"
                    emit(TextStreamResponse.Error(errorMsg))
                    LogUtil.w(TAG, "连接失败: $requestId")
                }

                else -> {
                    val errorMsg = "网络错误: ${e.message ?: "未知错误"}"
                    emit(TextStreamResponse.Error(errorMsg))
                    LogUtil.e(TAG, "流式请求错误: $requestId", e)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .onStart {
            val job = currentCoroutineContext().job
            requestMap[requestId] = job

            job.invokeOnCompletion { throwable ->
                requestMap.remove(requestId)
            }
        }
    /**
     * 以流的方式通过POST请求（支持Multipart表单）读取文本数据
     * @param requestId 请求唯一标识
     * @param url 请求URL
     * @param formFields 文本字段
     * @param fileParts 文件部分（可选）
     * @param charset 字符集，默认UTF-8
     * @return 返回包含文本块的Flow
     */
    fun streamTextPostMultipart(
        requestId: String,
        url: String,
        formFields: Map<String, String> = emptyMap(),
        fileParts: List<FilePart> = emptyList(),
        charset: String = "UTF-8",
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<TextStreamResponse> = flow {
        emit(TextStreamResponse.Connecting)

        val response: HttpResponse = client.post(url) {
            headers(headers)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // 添加文本字段
                        formFields.forEach { (key, value) ->
                            append(key, value)
                        }
                        // 添加文件
                        fileParts.forEach { filePart ->
                            append(
                                filePart.partName,
                                filePart.file.readBytes(),
                                headersOf(
                                    "Content-Type" to listOf(filePart.contentType),
                                    "Content-Disposition" to listOf("filename=\"${filePart.file.name}\"")
                                )
                            )
                        }
                    }
                )
            )
        }
        LogUtil.d(TAG,"streamTextPostMultipart response:$response")
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
            if (currentCoroutineContext().isActive) {
                emit(TextStreamResponse.Error(e.message ?: "Stream error"))
            }
            throw e
        } finally {
            reader.close()
            channel.cancel()
        }
    }
        .catch { e ->
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
            job.invokeOnCompletion { throwable ->
                requestMap.remove(requestId)
            }
        }

    /**
     * 使用 OkHttp 实现 HTTP Chunked 流式 POST 请求
     */
    fun streamTextPostChunked(
        requestId: String,
        url: String,
        body: Any,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<TextStreamResponse> {
        val headersMap = mutableMapOf<String, String>()
        val headersBuilder = HeadersBuilder()
        headersBuilder.headers()
        headersBuilder.build().forEach { name, values ->
            headersMap[name] = values.joinToString(", ")
        }

        val requestBody = when (body) {
            is String -> body
            else -> Gson().toJson(body)
        }

        return chunkedManager.createChunkedStream(
            url = url,
            requestBody = requestBody,
            headers = headersMap
        ).onStart {
            val job = currentCoroutineContext().job
            requestMap[requestId] = job
            job.invokeOnCompletion {
                requestMap.remove(requestId)
            }
        }
    }
    /**
     * 使用 SSE 进行流式文本 POST 请求
     */
    fun streamTextPostSSE(
        requestId: String,
        url: String,
        body: Any,
        headers: HeadersBuilder.() -> Unit = {}
    ): Flow<TextStreamResponse> {
        val headersMap = mutableMapOf<String, String>()
        val headersBuilder = HeadersBuilder()
        headersBuilder.headers()
        headersBuilder.build().forEach { name, values ->
            headersMap[name] = values.joinToString(", ")
        }

        return sseManager.createSSEStream(
            url = url,
            requestBody = when (body) {
                is String -> body
                else -> Gson().toJson(body) // 使用 Gson 或其他序列化库
            },
            headers = headersMap
        ).onStart {
            val job = currentCoroutineContext().job
            requestMap[requestId] = job
            job.invokeOnCompletion {
                requestMap.remove(requestId)
            }
        }
    }

    /**
     * 取消指定ID的请求
     * @param requestId 请求唯一标识
     */
    fun cancelRequest(requestId: String) {
        Log.d(TAG, "Cancelling request with ID: $requestId requestMap:${requestMap.map { "key:${it.key}" + "--value:${it.value}" }}")
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
 * 流式文本响应密封类
 * 用于表示网络流式请求的不同状态和事件，支持实时文本流处理
 *
 * 使用场景：AI聊天回复、实时数据流、长连接消息推送等需要增量接收数据的场景
 */
sealed class TextStreamResponse {
    /**
     * SSE 特定事件类型
     */
//    data class SSEEvent(val id: String?, val type: String?, val data: String) : TextStreamResponse()
    /**
     * 空闲状态
     * 表示流式请求尚未开始或已完全结束，处于初始等待状态
     * 通常用于初始化或重置流处理器的状态
     */
    object Idle : TextStreamResponse()

    /**
     * 连接中状态
     * 表示正在建立与服务器的连接，但尚未完成握手
     * 此时可以显示加载指示器，提示用户连接正在进行中
     */
    object Connecting : TextStreamResponse()

    /**
     * 已连接状态
     * 表示与服务器的连接已成功建立，准备接收数据流
     * 此时可以开始处理即将到来的数据块
     */
    object Connected : TextStreamResponse()

    /**
     * 数据块事件
     * 包含从服务器接收到的增量文本数据
     *
     * @property text 接收到的文本内容片段，可能是完整响应的一部分
     * 注意：可能需要拼接多个Data事件来构建完整响应
     */
    data class Data(val text: String) : TextStreamResponse()

    /**
     * 完成状态
     * 表示流式请求已成功完成，所有数据已接收完毕
     * 此时可以进行最终的UI更新和资源清理工作
     */
    object Completed : TextStreamResponse()

    /**
     * 取消状态
     * 表示流式请求被主动取消，通常由用户触发
     * 此时应该停止数据处理并释放相关资源
     */
    object Cancelled : TextStreamResponse()

    /**
     * 错误事件
     * 表示在流式处理过程中发生了错误
     *
     * @property message 错误描述信息，用于调试和用户提示
     * 可能包含网络错误、服务器错误、解析错误等详细信息
     */
    data class Error(val message: String,val errorCode: Int = -1) : TextStreamResponse()
}