package com.welo.util

sealed class InputMode {
    object Voice : InputMode()
    object Text : InputMode()
    object Typing : InputMode()
    object Send : InputMode()
    object VoiceInput : InputMode()
}