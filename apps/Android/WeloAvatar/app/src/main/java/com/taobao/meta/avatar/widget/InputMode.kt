package com.taobao.meta.avatar.widget

sealed class InputMode {
    object Voice : InputMode()
    object Text : InputMode()
    object Typing : InputMode()
    object Send : InputMode()
}