package com.welo.room.table

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isCurrentUser: Boolean = false
)

