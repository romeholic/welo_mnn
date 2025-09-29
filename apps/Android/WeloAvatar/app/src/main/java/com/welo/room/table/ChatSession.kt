package com.welo.room.table

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = System.currentTimeMillis(), // 默认使用时间戳作为ID,
    val userId: Long,
    val title: String,
    val modelType: String, // 例如: "gpt-3.5", "gpt-4", "claude"
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    var messageCount: Int = 0,
    var lastMessagePreview: String? = null
)
