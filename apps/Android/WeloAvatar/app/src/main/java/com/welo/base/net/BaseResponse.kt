package com.welo.base.net

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null
)