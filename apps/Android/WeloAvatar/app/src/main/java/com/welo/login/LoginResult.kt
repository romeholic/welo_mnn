package com.welo.login

import com.welo.entity.UserInfo

/**
 * Authentication result : success (user details) or error message.
 */
data class LoginResult(
    val success: UserInfo? = null,
    val error: Int? = null
)