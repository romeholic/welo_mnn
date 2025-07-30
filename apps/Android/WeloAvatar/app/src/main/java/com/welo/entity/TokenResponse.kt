package com.welo.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // 关键：添加序列化注解
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("token_type")
    val tokenType: String
)