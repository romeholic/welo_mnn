package com.welo.callback

interface IChat {

    fun startRecord()
    fun stopRecord()
    fun textSend(inputText: String)

    fun stopAnswer()

    fun stopRequest()
}