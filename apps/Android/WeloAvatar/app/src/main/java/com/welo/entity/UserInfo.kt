package com.welo.entity

import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class UserInfo(
    @SerialName("id")
    val id: String = "", // 使用 String 类型，假设 ID 是字符串格式

    @SerialName("username")
    val username: String = "", // 用户名不能为空，使用非 nullable 类型

    @SerialName("profile_image")
    val profileImage: String? = null, // 使用 nullable 类型，因为 JSON 中该字段为 null

    @SerialName("store_api_key")
    val storeApiKey: String? = null, // 同样为 nullable 类型

    @SerialName("is_active")
    val isActive: Boolean = true, // 默认值为 true，表示用户是活跃的

    @SerialName("is_superuser")
    val isSuperuser: Boolean = false, // 默认值为 false，表示用户不是超级用户

    @SerialName("create_at")
    val createdAt: String = "", // 也可以使用 LocalDateTime 类型，需要自定义解析器

    @SerialName("updated_at")
    val updatedAt: String = "", // 同样可以使用 LocalDateTime 类型

    @SerialName("last_login_at")
    val lastLoginAt: String? = null, // 可能为 null

    @SerialName("optins")
    val optins: Optins? = null
)
@Serializable
data class Optins(
    @SerialName("github_starred")
    val githubStarred: Boolean,

    @SerialName("dialog_dismissed")
    val dialogDismissed: Boolean,

    @SerialName("discord_clicked")
    val discordClicked: Boolean
)