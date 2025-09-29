package com.welo.callback

interface IChat {

    fun startRecord()
    fun stopRecord()
    fun recognitionResult(inputText: String)

    fun stopAnswer()

    fun stopRequest()
}