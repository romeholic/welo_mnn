package com.welo.base.okhttp

import com.google.gson.Gson
import com.taobao.meta.avatar.llm.FlowInputs
import com.taobao.meta.avatar.llm.FlowRequest
import com.welo.base.net.TextStreamResponse
import com.welo.constant.Constants
import com.welo.storage.TokenManager
import kotlinx.coroutines.flow.Flow
import java.io.File

// 在 ViewModel 或 Repository 中使用
class OkChatRepository {
    private val streamManager = OkHttpStreamManager.instance

    fun sendMessage(message: String,imagePath: List<String>? = null): Flow<TextStreamResponse> {
        val url = Constants.getLlmPath()
        val files = imagePath ?: emptyList()
        val requestBody = FlowRequest(
            files = files,
            inputs = FlowInputs(
                input_value = message,
                session = "Session ${System.currentTimeMillis()}"
            )
        )

        return streamManager.streamTextPost(
            requestId = "chat_${System.currentTimeMillis()}",
            url = url,
            body =  Gson().toJson(requestBody),
            headers = mapOf(
                "Authorization" to "Bearer ${TokenManager.getToken()}"
            )
        )
    }

    fun uploadFileWithStream(file: File, message: String): Flow<TextStreamResponse> {
        return streamManager.streamMultipartPost(
            requestId = "upload_${System.currentTimeMillis()}",
            url = "https://api.example.com/upload",
            formFields = mapOf("message" to message),
            fileParts = listOf(
                FilePart(
                    partName = "file",
                    file = file,
                    contentType = "image/jpeg"
                )
            ),
            headers = mapOf("Authorization" to "Bearer your_token")
        )
    }
}
