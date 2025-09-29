package com.welo.util

import android.util.Log
import com.taobao.meta.avatar.llm.FlowResponse
import kotlinx.serialization.json.Json

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
                "vertices_sorted" -> {
                    Log.d(TAG, "收到顶点排序响应块: $jsonString")
                    return "CHAT_START"
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
    fun stringToList(string: String?): List<String> {
        // 处理空字符串情况
        if (string.isNullOrEmpty()) {
            return emptyList()
        }

        // 使用逗号分割字符串，并过滤可能的空项
        return string.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}