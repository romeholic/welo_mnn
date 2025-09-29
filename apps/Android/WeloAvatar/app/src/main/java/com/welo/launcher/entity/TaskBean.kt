package com.welo.launcher.entity

import com.welo.room.table.ChatMessage

enum class TaskType {
    DEFAULT, STEP, PROGRESS
}

data class TaskBean(
    val taskType: TaskType = TaskType.DEFAULT,
    val taskId: Long = 0L,
    val messagesData: List<ChatMessage> = emptyList(),
    val resId: Int = 0
) {
    val userMessages: List<ChatMessage>
        get() = messagesData.filter { it.senderType == "USER" }

    val aiMessages: List<ChatMessage>
        get() = messagesData.filter { it.senderType == "AI" }
}