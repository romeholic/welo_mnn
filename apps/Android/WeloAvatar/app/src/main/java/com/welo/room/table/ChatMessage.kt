package com.welo.room.table

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"]), Index(value = ["timestamp"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val content: String,
    val senderType: String, // "USER" 或 "AI"
    val status: String = "SENT", // "SENDING", "SENT", "FAILED", "DELIVERED"
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "AUDIO"
    // 修改图片存储方式：使用逗号分隔的字符串存储多个图片URL
    val imageUris: String? = null, // 用逗号分隔的图片URL字符串
    val imageCaption: String? = null, // 新增：图片描述
    val aiResponseTime: Long? = null, // AI响应耗时(毫秒)
    val isRead: Boolean = false
)