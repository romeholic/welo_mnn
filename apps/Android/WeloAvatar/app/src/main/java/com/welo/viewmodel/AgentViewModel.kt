package com.welo.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.welo.base.net.ApiResult
import com.welo.repository.AgentRepository
import com.welo.util.LogUtil
import kotlinx.coroutines.launch

class AgentViewModel() : ViewModel() {
    val loginRepository: AgentRepository by lazy { AgentRepository() }
    private val _logined = MutableLiveData<Boolean>()
    val logined: LiveData<Boolean> = _logined

    fun setLoginState(state: Boolean){
        _logined.value = state
    }
    fun getAgent() {
        viewModelScope.launch {
            loginRepository.getAgentList("") .collect { response ->
                when(response){
                    is ApiResult.Success -> {
                        LogUtil.d("AgentViewModel", "register Success： ${response.data.flows.items.size}")
                        setLoginState(true)
                    }
                    is ApiResult.Error -> {
                        setLoginState(false)
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