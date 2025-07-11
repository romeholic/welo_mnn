package com.taobao.meta.avatar.utils

import android.util.Log
import com.taobao.meta.avatar.llm.FlowResponse
import kotlinx.serialization.json.Json
import retrofit2.http.Tag

object StringUtil {
    private const val TAG = "StringUtil"
    private val sharedJson = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true //忽略未知字段
    }
    fun parseFlowResponse(jsonString: String): String? {
        if (jsonString.isBlank()) {
            Log.w(TAG, "空JSON字符串，跳过解析")
            return null
        }
        try {
            val response = sharedJson.decodeFromString<FlowResponse>(jsonString)
            when (response.event) {
                "add_message" -> {
                    val text = response.data.text
                    return text?.takeIf { it.isNotBlank() }
                }
                "end" -> {
                    Log.d(TAG, "收到结束响应块: $jsonString")
                }
                else -> {
                    Log.d(TAG, "收到未知事件响应块: ${response.event}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析错误: ${e.message}}")
        }
        return null
    }
    fun getDecodesData(text: String): String {
        val decodedText = text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { matchResult ->
            val codePoint = matchResult.groupValues[1].toInt(16)
            String(Character.toChars(codePoint))
        }
        return decodedText
    }
}