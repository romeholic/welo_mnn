package com.taobao.meta.avatar.widget

data class MessageData(
    val id: String,
    var text: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val isSent: Boolean
)
