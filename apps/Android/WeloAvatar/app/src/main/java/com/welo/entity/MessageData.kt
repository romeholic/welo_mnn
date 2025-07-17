package com.welo.entity

data class MessageData(
    val id: String,
    var text: String,
    val timestamp: Long,
    val senderId: String,
    val senderName: String,
    val isSent: Boolean
)