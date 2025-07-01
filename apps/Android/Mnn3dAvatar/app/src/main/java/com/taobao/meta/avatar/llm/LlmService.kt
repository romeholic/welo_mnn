package com.taobao.meta.avatar.llm

import android.util.Log

import com.alibaba.mls.api.ApplicationProvider
import com.alibaba.mnnllm.android.ChatService
import com.alibaba.mnnllm.android.ChatSession

import com.taobao.meta.avatar.settings.MainSettings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse

import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

class LlmService {
    private var chatSession: ChatSession? = null
    private var stopRequested = false
    private var sessionId: String = "Session ${System.currentTimeMillis()}" // 生成唯一会话ID

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

/*    fun generate(text: String): Flow<Pair<String?, String>> = channelFlow {
        stopRequested = false
        val result = StringBuilder()
        withContext(Dispatchers.Default) {
            chatSession?.generate(text, object : ChatSession.GenerateProgressListener {
                override fun onProgress(progress: String?):Boolean {
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
    }.cancellable()*/

    // 从端侧已下载的大模型生成信息
    fun generate(text: String): Flow<Pair<String?, String>> {
        // 提升result变量到channelFlow外部，以便在onCompletion中访问
        val result = StringBuilder()

        return channelFlow {
            // 打印输入参数
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
                // 打印生成完成信息和最终结果
                Log.d(TAG, "===== 生成请求完成 =====")
                Log.d(TAG, "| 完成状态: ${if (cause == null) "正常完成" else "异常取消: ${cause.message}"}")
                Log.d(TAG, "| 最终结果长度: ${result.length}")
                Log.d(TAG, "| 最终结果内容: ${result.toString()}")
            }
    }

    // 解析流式响应内容
    private fun parseFlowResponse(jsonString: String): String {
        // 这里需要根据实际的流式响应格式进行调整
        // 以下是一个示例，假设响应是一个包含"output"字段的JSON对象
        try {
            val json = Json.parseToJsonElement(jsonString) as JsonObject
            return json["output"]?.jsonPrimitive?.content ?: ""
        } catch (e: Exception) {
            // 如果不是JSON格式，直接返回原始文本
            Log.w(TAG, "响应不是JSON格式，返回原始文本: $jsonString")
            return jsonString
        }
    }

    // 从云端大模型生成信息
    fun generateFromCloud(text: String): Flow<Pair<String?, String>> {
        val result = StringBuilder()
        return channelFlow<Pair<String?, String>> {
            Log.d(TAG, "===== 云端请求开始 =====")
            Log.d(TAG, "| 输入文本: $text")

            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                expectSuccess = true
            }

            try {
                // 构造适配后端接口的请求体
                val requestBody = FlowRequest(
                    files = emptyList(), // 如果没有文件，传空列表
                    inputs = Inputs(
                        input_value = text,
                        session = sessionId
                    )
                )

                val response: HttpResponse = client.post {
                    url("http://192.168.111.10:7860/api/v1/build/e3e07c37-49d7-44b7-be70-1ed17ea44851/flow?event_delivery=direct")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }

                // 使用 readRemaining 结合 produce 构建 Flow
                val responseFlow = flow<ByteArray> {
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead) {
                        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                        // 使用readText()并转换为ByteArray
                        val text = packet.readText()
                        emit(text.toByteArray(Charsets.UTF_8))
                        packet.release()
                    }
                }
                responseFlow
                    .map { bytes -> bytes.toString(Charsets.UTF_8) }
                    .filter { it.isNotBlank() && it.startsWith("data: ") }
                    .map { it.removePrefix("data: ").trim() }
                    .filterNot { it == "[DONE]" }
                    .mapNotNull { jsonString ->
                        try {
/*                            val json = Json.parseToJsonElement(jsonString) as JsonObject
                            val content = json["choices"]?.jsonArray?.firstOrNull()
                                ?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                            content*/
                            // 根据实际响应格式调整解析逻辑
                            val content = parseFlowResponse(jsonString)
                            Log.d(TAG, "解析出内容: $content")
                            content
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON解析错误: ${e.message}", e)
                            null
                        }
                    }
                    .collect { content ->
                        result.append(content)
                        // 明确参数类型
                        send(Pair(content, result.toString()))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "请求异常: ${e.message}", e)
                throw e
            } finally {
                client.close()
            }
        }.cancellable()
            .onCompletion { cause ->
                Log.d(TAG, "===== 云端请求完成 =====")
                Log.d(TAG, "| 完成状态: ${if (cause == null) "正常完成" else "异常取消: ${cause.message}"}")
                Log.d(TAG, "| 最终结果长度: ${result.length}")
                Log.d(TAG, "| 最终结果内容: ${result.toString()}")
            }
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