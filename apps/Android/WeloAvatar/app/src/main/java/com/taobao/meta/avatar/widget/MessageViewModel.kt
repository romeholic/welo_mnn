package com.taobao.meta.avatar.widget

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MessageViewModel: ViewModel() {

    private val _sendData = MutableLiveData<String>()
    val sendData: LiveData<String> = _sendData

    private val _receivedData = MutableLiveData<String>()
    val receivedData : LiveData<String> = _receivedData

    private val _receivedStatus = MutableLiveData<Boolean>()
    val receivedStatus: LiveData<Boolean> = _receivedStatus
    fun sendMessage(message: String) {
        if (message.isNotEmpty()) {
            _sendData.value = message
        }
    }

    fun receivedMessage(message: String) {
        if (message.isNotEmpty()) {
            _receivedData.value = message
        }
    }
    fun receivedStatus(status: Boolean) {
        _receivedStatus.value = status
    }
}