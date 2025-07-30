package com.welo.repository

import com.welo.base.net.ApiResponse
import com.welo.base.net.ApiResult
import com.welo.base.net.BaseRepository
import com.welo.constant.Constants
import com.welo.entity.TokenResponse
import com.welo.entity.UserInfo
import com.welo.storage.TokenManager
import com.welo.util.LogUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import java.util.UUID

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository() : BaseRepository() {
    // 请求ID前缀，避免与其他请求冲突
    private val requestPrefix = "user_"

    fun getRegister(requestId:String, username: String, password: String): Flow<ApiResult<UserInfo>> {
        val requestId = "${requestPrefix}$requestId"
        return networkManager.post(
            requestId = requestId,
            Constants.REGISTER,
            body = mapOf(
                "username" to username,
                "password" to password
            )
        ).map { apiResponse ->
            when (apiResponse) {
                is ApiResponse.Success -> {
                    // 解析基础响应
                    val baseResponse = parseDirectResponse<UserInfo>(apiResponse.data)
                    ApiResult.Success(baseResponse)
                }
                is ApiResponse.Error -> {
                    ApiResult.Error(
                        code = apiResponse.code,
                        message = apiResponse.message
                    )
                }
                is ApiResponse.Loading -> ApiResult.Loading
            }
        }
    }

    /**
     * 获取用户信息
     * @param username 用户ID
     * @return 包含UserInfo的Flow
     */
    fun login(requestId:String,username: String,password: String): Flow<ApiResult<TokenResponse>> {
        val requestId = "${requestPrefix}info_$requestId"
        return networkManager.postForm(
            requestId = requestId,
            url = Constants.LOGIN,
            formFields = mapOf(
                "username" to username,
                "password" to password
            )
        ).map { apiResponse ->
            when (apiResponse) {
                is ApiResponse.Success -> {
                    // 解析基础响应
                    val baseResponse = parseDirectResponse<TokenResponse>(apiResponse.data)
                    TokenManager.updateToken(
                        token = baseResponse.accessToken,
                        refreshToken = baseResponse.refreshToken,
                        expireTime = 0L
                    )
                    ApiResult.Success(baseResponse)
                }
                is ApiResponse.Error -> {
                    ApiResult.Error(
                        code = apiResponse.code,
                        message = apiResponse.message
                    )
                }
                is ApiResponse.Loading -> ApiResult.Loading
            }
        }
    }

    /**
     * 刷新Token
     */
    fun refreshToken(): Flow<ApiResult<TokenResponse>> {
        val refreshToken = TokenManager.getRefreshToken() ?: return flow {
            emit(ApiResult.Error("TOKEN_EXPIRED", "Please login again"))
        }

        return networkManager.post(
            requestId = "${requestPrefix}refresh_${UUID.randomUUID()}",
            url = Constants.REFRESH_TOKEN,
            body = mapOf("refreshToken" to refreshToken)
        ).transform { response ->
            emit(handleResponse(response) {
                jsonDecoder.decodeFromString<TokenResponse>(it)
            })
        }.onEach { result ->
            if (result is ApiResult.Success) {
                TokenManager.updateToken(
                    token = "",
                    refreshToken = result.data.refreshToken,
                    expireTime = 0L
                )
            }
        }
    }

    /**
     * 取消用户信息请求
     */
    fun cancelUserInfoRequest(userId: String) {
        networkManager.cancelRequest("${requestPrefix}info_$userId")
    }
}