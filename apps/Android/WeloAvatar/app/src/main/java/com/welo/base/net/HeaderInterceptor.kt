package com.welo.base.net

import com.welo.storage.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 构建新的请求并添加自定义头
        val request = original.newBuilder()
            .header("Authorization", "Bearer ${TokenManager.getToken()}")
            .build()

        return chain.proceed(request)
    }
}