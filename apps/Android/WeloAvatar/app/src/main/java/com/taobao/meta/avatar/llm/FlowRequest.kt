package com.taobao.meta.avatar.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 定义适配后端接口的请求数据类
@Serializable
data class FlowRequest(
    @SerialName("files") val files: List<String> = emptyList(), // 如果没有文件，传空列表
    @SerialName("inputs") val inputs: FlowInputs
)

@Serializable
data class FlowInputs(
    @SerialName("input_value") val input_value: String,
    @SerialName("session") val session: String
)
