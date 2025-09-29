package com.welo.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taobao.meta.avatar.R
import com.welo.base.net.ApiResult
import com.welo.repository.LoginRepository
import com.welo.entity.UserInfo
import com.welo.util.LogUtil
import kotlinx.coroutines.launch

class LoginViewModel() : ViewModel() {
    val loginRepository: LoginRepository by lazy { LoginRepository() }

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    fun register(username: String, password: String) {
        viewModelScope.launch {
            loginRepository.getRegister("",username,password) .collect { response ->
                when(response){
                    is ApiResult.Success -> {
                        val value = response.data
                        LogUtil.d("LoginViewModel", "register Success: $value")
                        _loginResult.value = LoginResult(success = response.data)
                    }
                    is ApiResult.Error -> {
                        LogUtil.d("LoginViewModel", "register Error: ${response.code} message: ${response.message}")
                        _loginResult.value = LoginResult(error = R.string.login_failed)
                    }
                    is ApiResult.Loading -> {
                        // 可以在这里处理加载状态
                        LogUtil.d("LoginViewModel", "register: Loading")
                    }
                    else -> {}
                }
            }
        }
    }
    fun login(username: String, password: String) {
        viewModelScope.launch {
            loginRepository.login("",username,password) .collect { response ->
                when(response){
                    is ApiResult.Success -> {
                        val value = response.data
                        LogUtil.d("LoginViewModel", "login Success: $value")
                        _loginResult.value = LoginResult(success = UserInfo())
                    }
                    is ApiResult.Error -> {
                        LogUtil.d("LoginViewModel", "login Error: ${response.code} message: ${response.message}")
                        _loginResult.value = LoginResult(error = R.string.login_failed)
                    }
                    is ApiResult.Loading -> {
                        // 可以在这里处理加载状态
                        LogUtil.d("LoginViewModel", "login: Loading")
                    }
                    else -> {}
                }
            }
        }
    }
    fun loginDataChanged(username: String, password: String) {
        if (!isUserNameValid(username)) {
            _loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
        } else if (!isPasswordValid(password)) {
            _loginForm.value = LoginFormState(passwordError = R.string.invalid_password)
        } else {
            _loginForm.value = LoginFormState(isDataValid = true)
        }
    }

    // A placeholder username validation check
    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    // A placeholder password validation check
    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}