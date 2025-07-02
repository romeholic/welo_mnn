package com.taobao.meta.avatar.llm

import android.util.Log

import com.alibaba.mls.api.ApplicationProvider
import com.alibaba.mnnllm.android.ChatService
import com.alibaba.mnnllm.android.ChatSession

import com.taobao.meta.avatar.settings.MainSettings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.Json

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

class LlmService {
    private var chatSession: ChatSession? = null
    private var stopRequested = false
    private var sessionId: String = "Session ${System.currentTimeMillis()}" // 生成唯一会话ID

    private val sharedJson = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true //忽略未知字段
    }

    suspend fun init(modelDir: String?): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "createSession begin")
        chatSession = ChatService.provide().createSession(
            "test_modelId_sessionId",
            "$modelDir/config.json",
            false, "llm_session", null
        )
        Log.d(TAG, "createSession create success")
        chatSession!!.load()
        Log.d(TAG, "createSession  load success")
        true
    }

    fun startNewSession() {
        chatSession?.reset()
        chatSession?.updatePrompt(MainSettings.getLlmPrompt(ApplicationProvider.get()))
    }

    // 默认从端侧已下载的大模型生成信息
    fun generate(text: String): Flow<Pair<String?, String>> {
        val result = StringBuilder()
        return channelFlow {
            Log.d(TAG, "===== 生成请求开始 =====")
            Log.d(TAG, "| 输入文本: $text")

            stopRequested = false

            withContext(Dispatchers.Default) {
                chatSession?.generate(text, object : ChatSession.GenerateProgressListener {
                    override fun onProgress(progress: String?): Boolean {
                        CoroutineScope(Dispatchers.Default).launch {
                            if (progress != null) {
                                result.append(progress)
                            }
                            send(Pair(progress, result.toString()))
                        }
                        return stopRequested
                    }
                })
            }
        }.cancellable()
            .onCompletion { cause ->
                Log.d(TAG, "===== 生成请求完成 =====")
                Log.d(TAG, "| 完成状态: ${if (cause == null) "正常完成" else "异常取消: ${cause.message}"}")
                Log.d(TAG, "| 最终结果长度: ${result.length}")
                Log.d(TAG, "| 最终结果内容: $result")
            }
    }

    private fun parseFlowResponse(jsonString: String): String? {
        if (jsonString.isBlank()) {
            Log.w(TAG, "空JSON字符串，跳过解析")
            return null
        }
        return try {
            val response = sharedJson.decodeFromString<FlowResponse>(jsonString)
            if (response.event == "add_message") {
                response.data.text?.replace("\\\\u", "\\u")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析错误: ${e.message}\nJSON内容: ${getDecodesData(jsonString)}")
            null
        }
    }

    class JsonStreamParser {
        private val buffer = StringBuilder()

        fun parseChunk(chunk: String): List<String> {
            buffer.append(chunk)
            val results = mutableListOf<String>()

            while (true) {
                val start = buffer.indexOf("{")
                val end = findMatchingBracket(buffer.toString(), start)

                if (start == -1 || end == -1) {
                    break
                }

                val json = buffer.substring(start, end + 1)
                results.add(json)

                if (end + 1 < buffer.length) {
                    buffer.delete(0, end + 1)
                } else {
                    buffer.clear()
                }
            }

            return results
        }

        private fun findMatchingBracket(json: String, startIndex: Int): Int {
            var depth = 0
            for (i in startIndex until json.length) {
                when (json[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth == 0) return i
            }
            return -1
        }
    }

    // 将 Unicode 编码转换为中文
    fun getDecodesData(text: String): String {
        val decodedText = text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { matchResult ->
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        }
        return decodedText
    }

    fun generateFlow(text: String): Flow<Pair<String?, String>> {
        val result = StringBuilder()
        val parser = JsonStreamParser() // 流式JSON解析器
        return channelFlow<Pair<String?, String>> {
            Log.d(TAG, "===== 云端请求开始 =====")
            Log.d(TAG, "| 输入文本: $text")
            Log.d(TAG, "| Session ID: $sessionId")

            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(sharedJson)
                }
                expectSuccess = true
            }

            try {
                val requestBody = FlowRequest(
                    files = emptyList(),
                    inputs = FlowInputs(
                        input_value = text,
                        session = sessionId
                    )
                )

                Log.d(TAG, "请求体: ${Json.encodeToString(FlowRequest.serializer(), requestBody)}")

                Log.d(TAG, "准备发送请求到: http://192.168.111.10:7860/api/v1/build/e3e07c37-49d7-44b7-be70-1ed17ea44851/flow?event_delivery=direct")

                val response: HttpResponse = client.post {
                    url("http://192.168.111.10:7860/api/v1/build/e3e07c37-49d7-44b7-be70-1ed17ea44851/flow?event_delivery=direct")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                Log.d(TAG, "收到响应: ${response.status}")
                Log.d(TAG, "响应头: ${response.headers}")

                if (response.status.isSuccess()) {
                    Log.d(TAG, "响应状态码成功: ${response.status}")
                } else {
                    Log.w(TAG, "响应状态码非成功: ${response.status}")
                }

                // 响应流处理
                val responseFlow = flow<String> {
                    val channel = response.bodyAsChannel()
                    Log.d(TAG, "开始读取响应体流")

                    var chunkCount = 0
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        if (packet.remaining > 0) {
                            chunkCount++
                            val text = packet.readText() // 原始文本 可能包含Unicode 编码
                            Log.d(TAG, "收到响应块 #$chunkCount (${getDecodesData(text).length} 字符): ${getDecodesData(text)}")
                            emit(text) //这里仍然发送原始文本，只修改日志显示
                        } else {
                            Log.d(TAG, "收到空响应块 #$chunkCount")
                        }
                        packet.release()
                    }

                    Log.d(TAG, "响应体流读取完成，共 $chunkCount 个块")
                }

                responseFlow
                    .filter { it.isNotBlank() }
                    .flatMapConcat { chunk ->
                        flow {
                            val jsonList = parser.parseChunk(chunk)
                            for (json in jsonList) {
                                Log.d(TAG, "解析出完整JSON: ${getDecodesData(json)}")
                                emit(json)
                            }
                        }
                    }
                    .mapNotNull { jsonString ->
                        parseFlowResponse(jsonString)
                    }
                    .collect { content ->
                        if (content.isBlank()) {
                            Log.d(TAG, "忽略空内容")
                            return@collect
                        }
                        Log.d(TAG, "收集到文本: $content")
                        result.append(content)
                        send(Pair(content, result.toString()))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "请求异常: ${e.message}")
                throw e
            } finally {
                client.close()
                Log.d(TAG, "HTTP客户端已关闭")
            }
        }.cancellable()
            .onCompletion { cause ->
                Log.d(TAG, "===== 云端请求完成 =====")
                Log.d(TAG, "| 完成状态: ${if (cause == null) "正常完成" else "异常取消: ${cause.message}"}")
                Log.d(TAG, "| 最终结果长度: ${result.length}")
                Log.d(TAG, "| 最终结果内容: $result")
            }
    }

    fun generateFromCloud(text: String): Flow<Pair<String?, String>> = channelFlow {
        Log.d(TAG, "===== 非流式云端请求开始 =====")
        Log.d(TAG, "| 输入文本: $text")
        Log.d(TAG, "| Session ID: $sessionId")

        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(sharedJson)
            }
            expectSuccess = true
        }

        try {
            val requestBody = AIChatRequest(
                input_value = text,
                session_id = sessionId, // 复用原sessionId
                tweaks = null
            )

            Log.d(TAG, "请求体: ${sharedJson.encodeToString(AIChatRequest.serializer(), requestBody)}")

            // 3. 发送POST请求到非流式接口
            val response: HttpResponse = client.post {
                url("http://192.168.111.10:7860/api/v1/run/e3e07c37-49d7-44b7-be70-1ed17ea44851?stream=false")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            Log.d(TAG, "收到响应: ${response.status}")

            // 4. 解析完整响应为AIChatResponse
            val aiResponse = response.body<AIChatResponse>()
            Log.d(TAG, "响应解析完成: 包含 ${aiResponse.outputs.size} 个输出项")

            // 5. 提取核心文本内容
            val fullResult = StringBuilder()
            var firstChunk: String? = null

            // 遍历outputs，提取每个OutputDetail中的message文本
            aiResponse.outputs.forEachIndexed { outputIndex, output ->
                output.outputs.forEachIndexed { detailIndex, detail ->
                    // 核心文本通常在 results.message.text 或 outputs.message.message 中
                    val content = detail.results.message.text
                        ?: detail.outputs.message.message
                        ?: ""

                    if (content.isNotBlank()) {
                        Log.d(TAG, "提取到内容: $content")
                        fullResult.append(content)
                        // 记录第一个非空内容（用于Pair的第一个元素）
                        if (firstChunk == null) firstChunk = content
                    }
                }
            }

            // 6. 发送结果
            val finalResult = fullResult.toString()
            send(Pair(firstChunk, finalResult))

        } catch (e: Exception) {
            Log.e(TAG, "请求异常: ${e.message}", e)
            throw e
        } finally {
            client.close()
            Log.d(TAG, "HTTP客户端已关闭")
        }

    }.onCompletion { cause ->
        Log.d(TAG, "===== 非流式云端请求完成 =====")
        Log.d(TAG, "| 完成状态: ${if (cause == null) "正常完成" else "异常取消: ${cause.message}"}")
    }


    fun requestStop() {
        stopRequested = true
    }

    fun unload() {
        chatSession?.release()
    }

    companion object {
        private const val TAG = "WELOO#LLMService"
    }
}