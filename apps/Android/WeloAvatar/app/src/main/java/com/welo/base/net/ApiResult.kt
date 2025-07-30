package com.welo.base.net

/**
 * 泛型API响应密封类（替代原有ApiResponse）
 * 携带具体类型的响应数据
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(
        val code: String,
        val message: String,
        val exception: Exception? = null
    ) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
    data class TokenExpired(val message: String?) : ApiResult<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$exception]"
            is Loading -> "Loading"
            is TokenExpired -> "TokenExpired[message=$message]"
        }
    }
}
