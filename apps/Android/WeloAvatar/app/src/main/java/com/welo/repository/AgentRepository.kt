package com.welo.repository

import com.welo.base.net.ApiResponse
import com.welo.base.net.ApiResult
import com.welo.base.net.BaseRepository
import com.welo.constant.Constants
import com.welo.entity.AgentData
import com.welo.storage.AgentManager
import com.welo.storage.TokenManager
import com.welo.util.LogUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentRepository : BaseRepository() {
    private val requestPrefix = "agent_"
    fun getAgentList(requestId: String): Flow<ApiResult<AgentData>> {
        LogUtil.d("AgentRepository", "getAgentList: token = ${TokenManager.getToken()}")
        val requestId = "${requestPrefix}agent_list_$requestId"
        val params = mapOf(
            "page" to "1",
            "size" to "20",
            "is_component" to "false",
            "is_flow" to "true"
        )
        return networkManager.get(
            requestId = requestId,
            url = Constants.AGENT_LIST,
            params
        ) {
            // 这里可以添加请求头或其他参数
            append("Authorization", "Bearer ${TokenManager.getToken()}")
        }.map { apiResponse ->
            when (apiResponse) {
                is ApiResponse.Success -> {
                    val baseResponse = parseDirectResponse<AgentData>(apiResponse.data)
                    AgentManager.saveAgent(baseResponse)
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
}