package com.taobao.meta.avatar.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AIChatRequest(
    @SerialName("input_value") val input_value: String,
    @SerialName("output_type") val output_type: String = "chat",
    @SerialName("input_type") val input_type: String = "chat",
    @SerialName("session_id") val session_id: String,
    @SerialName("tweaks") val tweaks: Tweaks?
)

@Serializable
data class Tweaks(
    val `ChatInput-wcKOQ`: ChatInput
)

@Serializable
data class ChatInput(
    val files: String
)