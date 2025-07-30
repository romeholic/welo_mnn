package com.welo.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentData(
    @SerialName("folder") val folder: Folder,
    @SerialName("flows") val flows: Flows
)

// 文件夹信息
@Serializable
data class Folder(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String?,
    @SerialName("id") val id: String,
    @SerialName("parent_id") val parentId: String?
)

// 流程集合（包含items列表）
@Serializable
data class Flows(
    @SerialName("items") val items: List<FlowItem>, // 仅解析到items层级
    @SerialName("total") val total: Int,
    @SerialName("page") val page: Int,
    @SerialName("size") val size: Int,
    @SerialName("pages") val pages: Int
)

// 单个流程项（不解析data内部结构）
@Serializable
data class FlowItem(
    @SerialName("gradient") val gradient: String?,
    @SerialName("mcp_enabled") val mcpEnabled: Boolean,
    @SerialName("action_name") val actionName: String?,
    @SerialName("name") val name: String,
    @SerialName("is_component") val isComponent: Boolean,
    @SerialName("action_description") val actionDescription: String?,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("user_id") val userId: String,
    @SerialName("locked") val locked: Boolean,
    @SerialName("fs_path") val fsPath: String?,
    @SerialName("icon_bg_color") val iconBgColor: String?,
    @SerialName("webhook") val webhook: Boolean,
    @SerialName("access_type") val accessType: String,
    @SerialName("icon") val icon: String?,
    @SerialName("endpoint_name") val endpointName: String?,
    @SerialName("description") val description: String,
    @SerialName("data") val data: JsonObject, // 用JsonObject接收，不解析内部结构
    @SerialName("id") val id: String,
    @SerialName("tags") val tags: List<String>,
    @SerialName("folder_id") val folderId: String
)