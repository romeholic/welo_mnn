package com.welo.constant

import com.alibaba.mls.api.ApplicationProvider

object Constants {
    /**
     * LLM模型存储路径
     */
    val SD_CARD_PATH = ApplicationProvider.get().filesDir.absolutePath
//    private const val BASE_URL = "http://192.168.111.10:7866"
    /**
     * 测试服务器地址
     */
    private const val BASE_URL = "http://192.168.111.10:7860"

    /**
     * 获取LLM模型的URL
     * @param agentId 模型的UUID
     * @return 完整的LLM模型URL
     */
    fun getLlmPath(agentId: String): String {
        return "$BASE_URL/api/v1/build/$agentId/flow?event_delivery=direct"
    }
    /**
     * 注册
     */
    const val REGISTER = "$BASE_URL/api/v1/users/"

    /**
     * 登录
     */
    const val LOGIN = "$BASE_URL/api/v1/login"

    /**
     * 刷新TOKEN
     */
    const val REFRESH_TOKEN = "$BASE_URL/api/v1/refresh"

    /**
     * 获取agent列表
     */
    const val AGENT_LIST = "$BASE_URL/api/v1/projects/welo"
}