package com.welo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.welo.base.net.ApiResult
import com.welo.repository.AgentRepository
import com.welo.util.LogUtil
import kotlinx.coroutines.launch

class AgentViewModel() : ViewModel() {
    val loginRepository: AgentRepository by lazy { AgentRepository() }
    fun getAgent() {
        viewModelScope.launch {
            loginRepository.getAgentList("") .collect { response ->
                when(response){
                    is ApiResult.Success -> {
                        LogUtil.d("AgentViewModel", "register Success： ${response.data.flows.items.size}")

                    }
                    is ApiResult.Error -> {
                        LogUtil.d("AgentViewModel", "register Error: ${response.code} message: ${response.message}")
                    }
                    is ApiResult.Loading -> {
                        // 可以在这里处理加载状态
                        LogUtil.d("AgentViewModel", "register: Loading")
                    }
                    else -> {}
                }
            }
        }
    }
}