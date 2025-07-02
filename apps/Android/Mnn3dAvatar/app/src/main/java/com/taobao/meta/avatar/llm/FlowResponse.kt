package com.taobao.meta.avatar.llm

import kotlinx.serialization.Serializable

// 根响应结构
@Serializable
data class FlowResponse(
    val event: String,
    val data: ResponseData
)

// 响应数据
@Serializable
data class ResponseData(
    // add_message事件字段
    val text: String? = null,
)