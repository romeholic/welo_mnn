package com.welo.storage

import com.welo.util.LogUtil
import com.welo.util.PreferenceUtil
import java.util.Date

object TokenManager {
    private const val KEY_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRE_TIME = "expire_time"

    /**
     * 保存Token信息
     */
    fun saveToken(token: String, refreshToken: String, expireTime: Long) {
        PreferenceUtil.get().putString(KEY_TOKEN, token)
        PreferenceUtil.get().putString(KEY_REFRESH_TOKEN, refreshToken)
        PreferenceUtil.get().putLong(KEY_EXPIRE_TIME, expireTime)
    }

    /**
     * 更新Token信息
     */
    fun updateToken(token: String, refreshToken: String, expireTime: Long) {
        LogUtil.d("TokenManager", "Updating token: $token, refreshToken: $refreshToken, expireTime: $expireTime")
        saveToken(token, refreshToken, expireTime)
    }

    /**
     * 获取访问Token
     */
    fun getToken(): String = PreferenceUtil.get().getString(KEY_TOKEN, "") ?: ""

    /**
     * 获取刷新Token
     */
    fun getRefreshToken(): String? {
        return PreferenceUtil.get().getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * 检查Token是否过期
     */
    fun isTokenExpired(): Boolean {
        val expireTime = PreferenceUtil.get().getLong(KEY_EXPIRE_TIME, 0)
        return expireTime <= Date().time / 1000
    }

    /**
     * 清除Token信息
     */
    fun clear() {
        PreferenceUtil.get().apply {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_EXPIRE_TIME)
        }
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return !getToken().isEmpty()
    }
}
