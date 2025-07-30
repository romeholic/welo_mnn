package com.welo.base.net

import com.welo.util.LogUtil
import kotlinx.serialization.json.Json

open class BaseRepository {
    protected val networkManager: NetworkManager by lazy { NetworkManager.instance }
    protected val jsonDecoder: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * 解析基础响应体
     * @param response 原始响应字符串
     */
    protected inline fun <reified T> parseDirectResponse(response: String): T {
        return try {
            jsonDecoder.decodeFromString(response)
        } catch (e: Exception) {
            throw Exception("Parse error: ${e.message}")
        }
    }
    /**
     * 统一处理API响应
     * 包含数据解析、错误处理和Token自动刷新逻辑
     */
    protected fun <T> handleResponse(
        response: ApiResponse,
        parser: (String) -> T
    ): ApiResult<T> {
        return when (response) {
            is ApiResponse.Success -> try {
                // 尝试解析数据
                ApiResult.Success(parser(response.data))
            } catch (e: Exception) {
                // 数据解析失败
                ApiResult.Error(
                    code = "PARSE_ERROR",
                    message = "数据解析失败: ${e.localizedMessage}",
                    exception = e
                )
            }
            is ApiResponse.Error -> {
                // 处理Token过期的特殊情况
                if (response.code == "401" || response.code == "TOKEN_EXPIRED") {
                    ApiResult.TokenExpired(response.message)
                } else {
                    ApiResult.Error(
                        code = response.code,
                        message = response.message ?: "未知错误"
                    )
                }
            }
            ApiResponse.Loading -> ApiResult.Loading
        }
    }
}