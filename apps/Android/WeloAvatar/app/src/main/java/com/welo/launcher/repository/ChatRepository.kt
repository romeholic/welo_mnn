package com.welo.launcher.repository

import android.net.Uri
import android.webkit.MimeTypeMap
import com.WeLoApplication
import com.taobao.meta.avatar.llm.FlowInputs
import com.taobao.meta.avatar.llm.FlowRequest
import com.welo.base.net.ApiResponse
import com.welo.base.net.NetworkManager
import com.welo.base.net.TextStreamResponse
import com.welo.constant.Constants
import com.welo.constant.Constants.FEATURE_TOUR_KEY
import com.welo.room.dao.ChatDao
import com.welo.room.table.ChatMessage
import com.welo.room.table.ChatSession
import com.welo.room.table.User
import com.welo.storage.TokenManager
import com.welo.util.LogUtil
import com.welo.util.PreferenceUtil
import com.welo.util.StringUtil
import com.welo.util.SystemUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID

/**
 * 聊天数据仓库类
 * 核心职责：协调聊天相关数据（用户、会话、消息）的本地存储（Room）与AI流式回复网络请求，
 * 对外提供统一的数据操作接口，支持数据实时观察（Flow），处理异步任务调度与异常。
 *
 * 数据流向：
 * 1. 本地数据：通过 [ChatDao] 操作 User/ChatSession/ChatMessage 表
 * 2. 网络请求：通过 [NetworkManager] 发起AI流式回复请求
 * 3. 事件通知：通过 [aiResponseStream] 对外发送AI回复过程中的状态事件
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val networkManager: NetworkManager = NetworkManager.instance,
    externalScope: CoroutineScope? = null
) {
    /** 伴生对象：存储日志标签 */
    companion object {
        /** 日志打印标签，用于区分当前类的日志输出 */
        private const val TAG = "ChatRepository"
    }

    /**
     * 协程作用域
     * 优先使用外部传入的作用域（便于统一生命周期管理），若未传入则默认使用IO调度器作用域，
     * 所有异步操作（数据库/网络）均在此作用域执行，避免阻塞主线程。
     */
    private val coroutineScope = externalScope ?: CoroutineScope(Dispatchers.IO)

    /**
     * 内部AI回复事件流（可发送）
     * 用于内部发送AI回复过程中的各类事件（连接、数据块、完成、错误等），
     * 类型为 [MutableSharedFlow]，支持多订阅者，无缓冲（默认）。
     */
    private val _aiResponseStream = MutableSharedFlow<AIResponseEvent>()

    /**
     * 外部可观察的AI回复事件流
     * 对外暴露不可变的 [SharedFlow]，避免外部直接发送事件，仅支持观察AI回复状态，
     * 事件类型为 [AIResponseEvent]（密封类，包含所有可能状态）。
     */
    val aiResponseStream: SharedFlow<AIResponseEvent> = _aiResponseStream

    /**
     * 活跃AI请求存储映射
     * Key：会话ID（Long），Value：AI请求唯一标识（String，UUID生成），
     * 用于跟踪当前正在进行的AI请求，支持后续取消指定会话的AI回复。
     */
    private val activeAIRequests = mutableMapOf<Long, String>()

    /**
     * 所有会话的实时观察流
     * 从 [ChatDao] 获取所有会话数据，返回 [Flow<List<ChatSession>>]，
     * 当ChatSession表数据变化时（新增/删除/更新），会自动触发流的更新，适合UI层实时展示会话列表。
     */
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    suspend fun getAllMessagesOnce(): List<ChatMessage> {
        return chatDao.getAllMessages()
    }

    /**
     * 创建新用户并插入本地数据库
     * 异步执行（通过 [coroutineScope] 调度），自动填充用户创建时间（当前时间戳）。
     *
     * @param username 用户名（唯一标识用户的名称）
     * @param isCurrentUser 是否为当前使用的用户（一个设备通常只有一个当前用户，默认false）
     */
    fun createUser(username: String, isCurrentUser: Boolean = false) {
        coroutineScope.launch {
            val user = User(
                username = username,
                isCurrentUser = isCurrentUser,
                createdAt = Date().time
            )
            chatDao.insertUser(user)
        }
    }

    /**
     * 获取本地数据库中的当前用户
     * 挂起函数，需在协程作用域中调用，查询 [User] 表中 [isCurrentUser] 为 true 的用户。
     *
     * @return [User]? 若存在当前用户则返回用户对象，否则返回null（如未创建过用户）
     */
    suspend fun getCurrentUser(): User? {
        return chatDao.getCurrentUser()
    }

    /**
     * 根据会话ID查询会话详情
     * 挂起函数，自动切换至IO调度器执行数据库操作，避免阻塞主线程。
     *
     * @param sessionId 会话唯一标识（ChatSession表的主键）
     * @return [ChatSession]? 若存在该ID的会话则返回会话对象，否则返回null
     */
    suspend fun getSessionById(sessionId: Long): ChatSession? {
        return withContext(Dispatchers.IO) {
            chatDao.getSessionById(sessionId)
        }
    }

    /**
     * 创建新会话并插入本地数据库
     * 异步执行，初始化会话的默认属性（未收藏、消息数0、空预览、创建/更新时间为当前时间），
     * 会话需关联到指定用户（通过 [userId]），且绑定AI模型类型（[modelType]）。
     *
     * @param userId 会话所属用户的ID（关联User表的主键）
     * @param title 会话标题（如“日常聊天”“工作咨询”，用户可自定义）
     * @param modelType 会话使用的AI模型类型（如“gpt-3.5”“gpt-4”，决定AI回复的模型版本）
     */
    fun createNewSession(userId: Long, title: String, modelType: String) {
        coroutineScope.launch {
            val session = ChatSession(
                userId = userId,
                title = title,
                modelType = modelType,
                createdAt = Date().time,
                updatedAt = Date().time,
                isFavorite = false,
                messageCount = 0,
                lastMessagePreview = ""
            )
            chatDao.insertSession(session)
        }
    }

    /**
     * 发送消息（用户消息触发AI流式回复）
     * 核心方法，处理消息发送的完整流程：验证会话存在性→插入消息→更新会话→触发AI回复（仅用户消息），
     * 异常时通过 [aiResponseStream] 发送错误事件。
     *
     * @param sessionId 消息所属会话的ID（关联ChatSession表）
     * @param content 消息内容（文本内容，如用户输入的问题、AI的回复）
     * @param senderType 发送者类型（默认“USER”表示用户发送，“AI”表示AI发送）
     * @param messageType 消息类型（默认“TEXT”表示文本消息，可扩展为“IMAGE”“FILE”等）
     * @param imageUri 图片地址
     * @param imageCaption 图片描述
     */
    fun sendMessage(
        sessionId: Long,
        content: String,
        senderType: String = "USER",
        messageType: String = "TEXT",
        imageUris: List<String>? = null,
        imageCaption: String? = null
    ) {
        if (senderType == "USER" && activeAIRequests.containsKey(sessionId)) {
            LogUtil.w(TAG, "会话 $sessionId 已有活跃AI请求，先取消之前的请求")
            cancelAIResponse(sessionId)
        }
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 第一步：确保消息所属的会话存在（不存在则自动创建）
                    ensureSessionExists(sessionId)

                    // 第二步：创建消息对象，用户消息初始状态为“SENDING”，AI消息为“SENT”
                    val message = ChatMessage(
                        sessionId = sessionId,
                        content = content,
                        senderType = senderType,
                        messageType = messageType,
                        imageUris = imageUris?.joinToString(","),       // 新增字段
                        imageCaption = imageCaption, // 新增字段
                        status = if (senderType == "USER") "SENDING" else "SENT",
                        timestamp = System.currentTimeMillis()
                    )

                    // 第三步：插入消息到本地数据库，获取插入后的消息ID
                    val messageId = chatDao.insertMessage(message)
                    LogUtil.d(TAG, "insertMessage messageId:$messageId")

                    // 第四步：更新会话的最新状态（消息数、最后一条消息预览、更新时间）
                    updateSessionInfo(sessionId, content)

                    // 第五步：若为用户消息，更新消息状态为“SENDING”并触发AI流式回复
                    if (senderType == "USER") {
                        chatDao.updateMessageStatus(messageId, "SENDING")

                        // 启动AI回复子协程（独立于当前协程，避免阻塞消息插入流程）
                        launch {
                            val session = getSessionById(sessionId)
                            getRealAIResponse(
                                sessionId,
                                messageId,
                                content,
                                session?.modelType ?: "gpt-3.5", // 默认使用gpt-3.5模型
                                imageUris
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // 捕获所有异常，打印日志并发送错误事件到UI层
                LogUtil.e(TAG, "发送消息失败: ${e.message}", e)
                _aiResponseStream.emit(
                    AIResponseEvent.Error(
                        sessionId,
                        e.message ?: "发送消息失败"
                    )
                )
            }
        }
    }

    /**
     * 发送带图片的消息（新方法）
     */
    fun sendMessageWithImage(
        sessionId: Long,
        content: String,
        imageUris: List<Uri>,
        senderType: String = "USER",
        messageType: String = "TEXT_WITH_IMAGE"
    ) {
        coroutineScope.launch {
            try {
                // 1. 先上传图片到服务器
                val imageUrls : MutableList<String> = mutableListOf()
                imageUris.forEach { imageUrl ->
                    val serviceImageUrl = uploadImageToServer(imageUrl)
                    serviceImageUrl?.let {
                        imageUrls.add(serviceImageUrl)
                    }
                }
                if (imageUrls.isEmpty()) {
                    _aiResponseStream.emit(
                        AIResponseEvent.Error(
                            sessionId,
                            "图片上传失败"
                        )
                    )
                    return@launch
                }

                // 2. 使用返回的图片地址发送消息
                sendMessage(
                    sessionId = sessionId,
                    content = content,
                    senderType = senderType,
                    messageType = messageType,
                    imageUris = imageUrls, // 使用服务器返回的相对地址
                    imageCaption = content.take(50) // 可选：生成图片描述
                )
            } catch (e: Exception) {
                LogUtil.e(TAG, "发送带图片消息失败: ${e.message}", e)
                _aiResponseStream.emit(
                    AIResponseEvent.Error(
                        sessionId,
                        e.message ?: "发送带图片消息失败"
                    )
                )
            }
        }
    }

    /**
     * 确保指定ID的会话存在
     * 挂起函数，用于消息发送前的校验，若会话不存在则调用 [createSessionSync] 同步创建，
     * 避免消息发送到无效会话。
     *
     * @param sessionId 需验证的会话ID
     */
    suspend fun ensureSessionExists(sessionId: Long) {
        val sessionExists = getSessionById(sessionId) != null
        if (!sessionExists) {
            // 会话不存在时，同步创建（确保在同一事务中，避免并发问题）
            createSessionSync(sessionId)
        }
    }

    /**
     * 同步创建会话（确保事务一致性）
     * 私有挂起函数，仅内部调用，切换至IO调度器执行，依赖当前用户（[getCurrentUser]），
     * 若无当前用户则抛出异常（无法创建无所属用户的会话）。
     *
     * @param sessionId 要创建的会话ID（直接指定主键，避免自动生成）
     * @throws IllegalStateException 当 [getCurrentUser] 返回null时抛出（无当前用户）
     */
    private suspend fun createSessionSync(sessionId: Long) {
        withContext(Dispatchers.IO) {
            val currentUser = getCurrentUser()
            currentUser?.let { user ->
                val session = ChatSession(
                    id = sessionId,
                    userId = user.id,
                    title = "新会话", // 默认会话标题
                    modelType = "gpt-3.5", // 默认AI模型
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isFavorite = false,
                    messageCount = 0,
                    lastMessagePreview = ""
                )
                chatDao.insertSession(session)
                LogUtil.d(TAG, "创建新会话: $sessionId")
            } ?: run {
                throw IllegalStateException("当前用户不存在，无法创建会话")
            }
        }
    }

    /**
     * 更新会话的最新状态信息
     * 私有挂起函数，仅内部调用，用于消息发送后同步更新会话的关键属性：
     * 1. 更新时间（当前时间）
     * 2. 消息数（+1）
     * 3. 最后一条消息预览（取消息内容前30字符，避免过长）
     *
     * @param sessionId 需更新的会话ID
     * @param content 最新消息的内容（用于生成预览）
     */
    private suspend fun updateSessionInfo(sessionId: Long, content: String) {
        val session = getSessionById(sessionId)
        session?.let {
            it.updatedAt = System.currentTimeMillis()
            it.messageCount += 1
            it.lastMessagePreview = content.take(30) // 截取前30字符作为预览
            chatDao.updateSession(it)
        }
    }

    /**
     * 发起真实AI流式回复请求
     * 私有挂起函数，核心网络请求方法，处理AI回复的完整生命周期：
     * 1. 生成唯一请求ID并加入活跃请求映射
     * 2. 构建AI请求参数（[FlowRequest]）
     * 3. 发起流式POST请求（[NetworkManager.streamTextPost]）
     * 4. 处理各类流状态（连接中/已连接/数据块/完成/取消/错误）
     * 5. 异常处理与活跃请求清理
     *
     * @param sessionId 会话ID（关联AI回复的会话）
     * @param userMessageId 用户消息ID（用于更新用户消息状态）
     * @param userMessage 用户消息内容（AI回复的输入内容）
     * @param modelType AI模型类型（决定调用的模型版本）
     */
    private suspend fun getRealAIResponse(
        sessionId: Long,
        userMessageId: Long,
        userMessage: String,
        modelType: String,
        imageUri: List<String>? = null
    ) {
        // 生成唯一请求ID，用于标识当前AI请求
        val requestId = UUID.randomUUID().toString()
        activeAIRequests[sessionId] = requestId
        // AI消息占位符ID（初始为-1，连接成功后创建）
        var aiMessageId: Long = -1L

        try {
            val streamResponse = streamResponse(userMessage, requestId, imageUri)

            streamResponse.collect { streamResponse ->
                // 检查当前请求是否已被取消
                if (activeAIRequests[sessionId] != requestId) {
                    LogUtil.d(TAG, "请求已被取消，停止收集流数据")
                    return@collect
                }
                // 4. 根据流响应类型处理不同状态
                when (streamResponse) {
                    // 连接中：发送连接中事件到UI层
                    is TextStreamResponse.Connecting -> {
                        LogUtil.d(TAG, "AI回复连接中...")
                        _aiResponseStream.emit(AIResponseEvent.Connecting(sessionId, aiMessageId))
                    }

                    // 已连接：创建AI消息占位符，发送连接成功事件
                    is TextStreamResponse.Connected -> {
                        aiMessageId = createAIMessagePlaceholder(sessionId)
                        chatDao.updateMessageStatus(userMessageId, "SEND")
                        _aiResponseStream.emit(AIResponseEvent.Connected(sessionId, aiMessageId))
                    }

                    // 数据块：处理AI返回的增量内容，更新数据库并发送事件
                    is TextStreamResponse.Data -> {
                        processAIResponseChunk(
                            sessionId = sessionId,
                            aiMessageId = aiMessageId,
                            userMessageId = userMessageId,
                            chunk = streamResponse.text,
                            userMessage = userMessage
                        )
                    }

                    // 完成：处理AI回复收尾（更新状态、会话信息），发送完成事件
                    is TextStreamResponse.Completed -> {
                        if (aiMessageId != -1L) {
                            completeAIResponse(sessionId, userMessageId, aiMessageId)
                        }
                        // 从活跃请求中移除当前会话
                        activeAIRequests.remove(sessionId)
                    }

                    // 取消：处理AI请求取消（更新消息状态），发送取消事件
                    is TextStreamResponse.Cancelled -> {
                        LogUtil.d(TAG,"Cancelled aiMessageId:$aiMessageId")
                        if (aiMessageId != -1L) {
                            _aiResponseStream.emit(
                                AIResponseEvent.Cancelled(sessionId, aiMessageId)
                            )
                            handleAICancellation(sessionId, aiMessageId)
                        }
                        handleUserMessageCancellation(userMessageId)
                        activeAIRequests.remove(sessionId)
                    }

                    // 错误：分阶段处理错误（连接阶段/数据阶段），发送错误事件
                    is TextStreamResponse.Error -> {
                        LogUtil.d(TAG, "getRealAIResponse Error:${streamResponse.message} :${streamResponse.errorCode}")
                        if (aiMessageId != -1L) {
                            _aiResponseStream.emit(
                                AIResponseEvent.Error(
                                    sessionId = sessionId,
                                    message = "AI回复错误: ${streamResponse.message}",
                                    streamResponse.errorCode
                                )
                            )
                            handleAIError(sessionId, aiMessageId, streamResponse.message)
                        } else {
                            // 连接阶段错误：无需创建AI消息，仅更新用户消息状态
                            _aiResponseStream.emit(
                                AIResponseEvent.Error(
                                    sessionId = sessionId,
                                    message = "AI连接失败: ${streamResponse.message}"
                                )
                            )
                            handleConnectionError(userMessageId, streamResponse.message)
                        }
                        activeAIRequests.remove(sessionId)
                    }
                    // 其他未定义状态：忽略处理
                    else -> {}
                }
            }

        } catch (e: Exception) {
            // 捕获所有异常（如网络异常、解析异常），发送错误事件
            if (aiMessageId != -1L) {
                _aiResponseStream.emit(
                    AIResponseEvent.Error(
                        sessionId = sessionId,
                        message = "获取AI回复时出错: ${e.message}"
                    )
                )
                handleAIError(sessionId, aiMessageId, e.message ?: "未知错误")
            } else {
                _aiResponseStream.emit(
                    AIResponseEvent.Error(
                        sessionId = sessionId,
                        message = "连接AI服务失败: ${e.message}"
                    )
                )
                handleConnectionError(userMessageId, e.message ?: "未知错误")
            }
            activeAIRequests.remove(sessionId)
        }
    }
    private suspend fun handleUserMessageCancellation(userMessageId: Long) {
        withContext(Dispatchers.IO) {
            chatDao.updateMessageStatus(userMessageId, "CANCELLED")
        }
    }
    private fun streamResponse(
        userMessage: String,
        requestId: String,
        imagePath: List<String>? = null
    ): Flow<TextStreamResponse> {
        val url = Constants.getLlmPath()
        val files = imagePath ?: emptyList()
        // HTTP Chunked 流式响应
        return networkManager.streamTextPostChunked(
            requestId = requestId,
            url = url,
            body = createFlowRequest(userMessage, imagePath),
            headers = {
                append("Authorization", "Bearer ${TokenManager.getToken()}")
                append("Accept", "text/plain") // 明确要求文本响应
                append("Cache-Control", "no-cache")
            }
        )
        // 使用 SSE 流式请求
//        return networkManager.streamTextPostSSE(
//            requestId = requestId,
//            url = Constants.getLlmPath(),
//            body = createFlowRequest(userMessage, imagePath),
//            headers = {
//                append("Authorization", "Bearer ${TokenManager.getToken()}")
//                append("Accept", "text/event-stream") // SSE 需要的头
//                append("Cache-Control", "no-cache")
//                append("Connection", "keep-alive")
//            }
//        )

//        return networkManager.streamTextPost(
//            requestId = requestId,
//            url = url,
//            body = requestBody
//        ) {
//            append("Authorization", "Bearer ${TokenManager.getToken()}")
//        }
    }
    // 创建 FlowRequest 的辅助方法
    private fun createFlowRequest(userMessage: String, imagePath: List<String>?): FlowRequest {
        return FlowRequest(
            files = imagePath ?: emptyList(),
            inputs = FlowInputs(
                input_value = userMessage,
                session = if (PreferenceUtil.get()
                        .getBoolean(FEATURE_TOUR_KEY)
                ) "Session ${Constants.guidesessionId + SystemUtils.getDeviceSN()}" else "Session ${Constants.sessionId + SystemUtils.getDeviceSN()}"
            )
        )
    }
    /**
     * 上传图片到服务器
     * @param imageUri 图片的Uri
     * @return 服务器返回的图片相对地址，如果上传失败返回null
     */
    private suspend fun uploadImageToServer(imageUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val context = WeLoApplication.getInstance().applicationContext
                val inputStream = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    LogUtil.e(TAG, "无法打开图片输入流")
                    return@withContext null
                }
                // 获取原始文件信息
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                val fileExtension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "jpg"

                // 使用原始格式创建临时文件
                val tempFile = File.createTempFile("upload_image", ".$fileExtension")

                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                val uploadUrl = Constants.getImageUploadPath()
                val response = networkManager.uploadImage(
                    requestId = UUID.randomUUID().toString(),
                    url = uploadUrl,
                    file = tempFile,
                    fileName = "image_${System.currentTimeMillis()}.$fileExtension",
                    mimeType = mimeType
                ) {
                    append("Authorization", "Bearer ${TokenManager.getToken()}")
                }.first { it is ApiResponse.Success || it is ApiResponse.Error }
                // 清理临时文件
                tempFile.delete()
                if (response is ApiResponse.Success) {
                    // 解析服务器返回的图片地址
                    return@withContext parseImageUrlFromResponse(response.data)
                } else {
                    LogUtil.e(TAG, "图片上传失败: ${(response as ApiResponse.Error).message}")
                    return@withContext null
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "上传图片异常: ${e.message}")
                return@withContext null
            }
        }
    }

    /**
     * 从服务器响应中解析图片地址
     * 根据你的实际API响应格式调整这个解析逻辑
     */
    private fun parseImageUrlFromResponse(response: String): String? {
        return try {
            //{"flowId":"e3e07c37-49d7-44b7-be70-1ed17ea44851","file_path":"e3e07c37-49d7-44b7-be70-1ed17ea44851/2025-09-02_05-28-43_image_1756790926658.jpg"}
            val jsonObject = JSONObject(response)
            val flowId = jsonObject.getString("flowId")
            val filePath = jsonObject.getString("file_path")
            LogUtil.d(TAG, "parseImageUrlFromResponse flowId:$flowId filePath:$filePath")
            return filePath
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析图片地址失败: ${e.message}")
            null
        }
    }
    /**
     * 处理AI连接阶段的错误
     * 私有挂起函数，仅在AI请求连接阶段失败时调用（未创建AI消息），
     * 更新用户消息状态为“FAILED”，可选更新消息内容显示错误信息（当前注释）。
     *
     * @param userMessageId 需更新状态的用户消息ID
     * @param errorMessage 错误描述信息（用于日志或消息内容）
     */
    private suspend fun handleConnectionError(userMessageId: Long, errorMessage: String) {
        withContext(Dispatchers.IO) {
            chatDao.updateMessageStatus(userMessageId, "FAILED")
            // 可选：更新消息内容以显示错误信息（根据业务需求决定是否启用）
            // chatDao.updateMessageContent(userMessageId, "发送失败: $errorMessage")
        }
    }

    /**
     * 处理AI回复的增量数据块
     * 私有挂起函数，核心数据处理方法，负责：
     * 1. 解码原始数据块（[StringUtil.getDecodesData]）
     * 2. 解析有效回复内容（[StringUtil.parseFlowResponse]）
     * 3. 提取增量内容（避免重复发送完整内容）
     * 4. 更新数据库中的AI消息完整内容
     * 5. 发送增量数据事件到UI层
     *
     * @param sessionId 会话ID
     * @param aiMessageId AI消息ID（用于更新数据库）
     * @param userMessageId 用户消息ID（预留，当前未使用）
     * @param chunk AI返回的原始数据块（需解码解析）
     */
    private suspend fun processAIResponseChunk(
        sessionId: Long,
        aiMessageId: Long,
        userMessageId: Long,
        chunk: String,
        userMessage: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 解码原始数据块（处理可能的编码格式）
                val json = StringUtil.getDecodesData(chunk)
                // 2. 解析JSON数据，提取有效回复内容
                var processedContent = StringUtil.parseFlowResponse(json) ?: ""
                if (processedContent.isBlank()) return@withContext

                if (processedContent == "CHAT_START") {
                    chatDao.updateMessageStatus(aiMessageId, "GENERATING")
                    return@withContext
                }

                // 处理服务端可能回显用户输入的情况：去掉以 userMessage 为前缀的回显
                if (userMessage.isNotBlank() && processedContent.startsWith(userMessage)) {
                    // 先直接去掉精确前缀
                    processedContent = processedContent.removePrefix(userMessage)
                    // 再去掉常见的额外分隔符（如 ":"、"-"、换行）以及多余空白
                    processedContent = processedContent.trimStart().trimStart { it == ':' || it == '-' || it == '\n' || it == '\r' || it.isWhitespace() }
                }
                if (processedContent.isNotBlank()) {
                    // 3. 获取数据库中当前AI消息的内容（用于对比增量）
                    val currentContent = chatDao.getMessageContent(aiMessageId) ?: ""
                    // 4. 提取真正的增量内容（仅发送新增部分，减少数据传输）
                    val incrementalContent =
                        extractTrueIncremental(processedContent, currentContent)

                    if (incrementalContent.isNotBlank()) {
                        // 5. 更新数据库中的AI消息完整内容
                        chatDao.updateMessageContent(aiMessageId, processedContent)
                        // 6. 发送增量数据事件到UI层
                        _aiResponseStream.emit(
                            AIResponseEvent.Chunk(
                                sessionId = sessionId,
                                messageId = aiMessageId,
                                chunk = incrementalContent,    // 仅发送新增内容
                                fullText = processedContent,   // 当前完整内容（供UI层备用）
                                isIncremental = true           // 标记为增量更新
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // 解析数据块异常，发送错误事件
                _aiResponseStream.emit(
                    AIResponseEvent.Error(
                        sessionId = sessionId,
                        message = "解析AI回复时出错: ${e.message}"
                    )
                )
            }
        }
    }

    /**
     * 提取AI回复的增量内容
     * 私有工具方法，对比新完整内容与当前存储内容，返回真正新增的部分，
     * 处理三种场景：1. 首次接收内容；2. 内容追加；3. 内容修正（如AI回溯修改）。
     *
     * @param newFullContent AI返回的最新完整内容
     * @param currentContent 数据库中存储的当前内容
     * @return String 增量内容（为空表示无新增）
     */
    private fun extractTrueIncremental(newFullContent: String, currentContent: String): String {
        return when {
            // 场景1：当前无内容（首次接收），返回全部新内容
            currentContent.isEmpty() -> newFullContent

            // 场景2：新内容以当前内容开头（正常追加），返回后缀增量
            newFullContent.startsWith(currentContent) ->
                newFullContent.substring(currentContent.length)

            // 场景3：内容不匹配（AI修正），查找差异部分
            else -> findContentDifference(currentContent, newFullContent)
        }
    }

    /**
     * 查找新旧内容的差异部分（处理AI内容修正）
     * 私有工具方法，找到新旧内容第一个不同字符的位置，返回新内容从该位置开始的子串，
     * 适用于AI回溯修改已返回内容的场景（如修正错别字、补充上下文）。
     *
     * @param oldContent 旧内容（数据库中存储的内容）
     * @param newContent 新内容（AI最新返回的内容）
     * @return String 新内容与旧内容的差异部分（为空表示无差异）
     */
    private fun findContentDifference(oldContent: String, newContent: String): String {
        // 取两者中较短的长度，避免索引越界
        val minLength = minOf(oldContent.length, newContent.length)
        // 找到第一个不同字符的索引
        var diffIndex = 0
        while (diffIndex < minLength && oldContent[diffIndex] == newContent[diffIndex]) {
            diffIndex++
        }
        // 返回新内容从差异索引开始的部分
        return if (diffIndex < newContent.length) {
            newContent.substring(diffIndex)
        } else {
            ""
        }
    }

    /**
     * 创建AI消息占位符
     * 私有挂起函数，在AI连接成功后调用，插入一条空内容、状态为“GENERATING”的AI消息，
     * 用于后续接收增量内容并更新，确保消息ID在AI回复过程中唯一且固定。
     *
     * @param sessionId 会话ID（AI消息所属会话）
     * @return Long 插入后的AI消息ID（ChatMessage表的主键）
     */
    private suspend fun createAIMessagePlaceholder(sessionId: Long): Long {
        return withContext(Dispatchers.IO) {
            val aiMessage = ChatMessage(
                sessionId = sessionId,
                content = "", // 初始为空，后续通过增量更新
                senderType = "AI",
                messageType = "TEXT",
                status = "GENERATING", // 生成中状态
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(aiMessage)
        }
    }

    /**
     * 完成AI回复的收尾处理
     * 私有挂起函数，在AI流式回复完成后调用，处理：
     * 1. 更新AI消息状态为“SENT”（发送完成）
     * 2. 更新用户消息状态为“DELIVERED”（已送达AI服务）
     * 3. 更新会话的最新状态（消息数、预览、更新时间）
     * 4. 发送回复完成事件到UI层
     *
     * @param sessionId 会话ID
     * @param userMessageId 用户消息ID（用于更新状态）
     * @param aiMessageId AI消息ID（用于更新状态）
     */
    private suspend fun completeAIResponse(
        sessionId: Long,
        userMessageId: Long,
        aiMessageId: Long
    ) {
        withContext(Dispatchers.IO) {
            // 1. 获取AI消息的完整内容（用于更新会话预览）
            val fullResponse = chatDao.getMessageContent(aiMessageId) ?: ""

            // 2. 更新AI消息状态为“已发送”
            chatDao.updateMessageStatus(aiMessageId, "SENT")

            // 3. 更新用户消息状态为“已送达”
            chatDao.updateMessageStatus(userMessageId, "DELIVERED")

            // 4. 更新会话的最新信息
            val session = getSessionById(sessionId)
            session?.let {
                it.updatedAt = System.currentTimeMillis()
                it.messageCount += 1
                it.lastMessagePreview = fullResponse.take(30)
                chatDao.updateSession(it)
            }
        }

        // 5. 发送回复完成事件
        _aiResponseStream.emit(
            AIResponseEvent.Complete(
                sessionId = sessionId,
                messageId = aiMessageId
            )
        )
    }

    /**
     * 处理AI请求取消的收尾
     * 私有挂起函数，在AI请求被取消后调用，更新AI消息状态为“CANCELLED”（已取消），
     * 确保本地数据库状态与实际请求状态一致。
     *
     * @param sessionId 会话ID
     * @param aiMessageId AI消息ID（用于更新状态）
     */
    private suspend fun handleAICancellation(sessionId: Long, aiMessageId: Long) {
        withContext(Dispatchers.IO) {
            chatDao.updateMessageStatus(aiMessageId, "CANCELLED")
        }
    }

    /**
     * 处理AI回复过程中的错误
     * 私有挂起函数，在AI回复过程中发生错误时调用，更新AI消息状态为“FAILED”（失败），
     * 并更新消息内容为错误描述，便于用户查看失败原因。
     *
     * @param sessionId 会话ID
     * @param aiMessageId AI消息ID（用于更新状态和内容）
     * @param errorMessage 错误描述信息（显示在消息内容中）
     */
    private suspend fun handleAIError(sessionId: Long, aiMessageId: Long, errorMessage: String) {
        withContext(Dispatchers.IO) {
            if (aiMessageId != -1L) {
                chatDao.updateMessageStatus(aiMessageId, "FAILED")
                chatDao.updateMessageContent(aiMessageId, "AI回复失败: $errorMessage")
            }
        }
    }

    /**
     * 取消指定会话的AI回复请求
     * 异步执行，从 [activeAIRequests] 中获取该会话的请求ID，
     * 调用 [NetworkManager.cancelRequest] 取消请求，并移除活跃请求记录。
     *
     * @param sessionId 需取消AI回复的会话ID
     */
    fun cancelAIResponse(sessionId: Long) {
        coroutineScope.launch {
            val requestId = activeAIRequests[sessionId]
            if (requestId != null) {
                _aiResponseStream.emit(AIResponseEvent.Cancelled(sessionId, -1L))

                networkManager.cancelRequest(requestId)
                activeAIRequests.remove(sessionId)

                // 立即更新用户消息状态为取消
                withContext(Dispatchers.IO) {
                    // 查找该会话中状态为SENDING的用户消息
                    val sendingMessages = chatDao.getMessagesBySessionIdAndStatus(sessionId, "SENDING")
                    sendingMessages.forEach { message ->
                        chatDao.updateMessageStatus(message.id, "CANCELLED")
                    }

                    val generatingMessages = chatDao.getMessagesBySessionIdAndStatus(sessionId, "GENERATING")
                    generatingMessages.forEach { message ->
                        chatDao.updateMessageStatus(message.id, "CANCELLED")
                        if (message.content.isEmpty()){
                            chatDao.updateMessageContent(message.id, "对话已取消")
                        }
                    }
                }
            }
        }
    }

    /**
     * 观察指定会话的消息列表（实时更新）
     * 返回 [Flow<List<ChatMessage>>]，当该会话的消息发生变化（新增/删除/更新）时，
     * 自动触发流的更新，适合UI层实时展示聊天记录。
     *
     * @param sessionId 会话ID（需观察消息的会话）
     * @return Flow<List<ChatMessage>> 该会话的消息流（按时间戳排序，默认升序）
     */
    fun observeSessionMessages(sessionId: Long): Flow<List<ChatMessage>> {
        LogUtil.e(TAG, "observeSessionMessages")
        return chatDao.getMessagesBySessionId(sessionId)
    }

    /**
     * 获取指定会话的消息列表（非实时，一次性查询）
     * 挂起函数，切换至IO调度器执行，一次性查询该会话的所有消息，
     * 查询失败时打印日志并返回空列表，适用于不需要实时更新的场景（如消息导出）。
     *
     * @param sessionId 会话ID（需获取消息的会话）
     * @return List<ChatMessage> 该会话的消息列表（空列表表示无消息或查询失败）
     */
    suspend fun getSessionMessages(sessionId: Long): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                chatDao.getMessagesBySessionIdList(sessionId)
            } catch (e: Exception) {
                LogUtil.e(TAG, "获取消息列表失败: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * 切换会话的收藏状态
     * 异步执行，更新指定会话的 [isFavorite] 属性（收藏/取消收藏），
     * 用于UI层展示收藏标记，便于用户快速筛选常用会话。
     *
     * @param sessionId 需切换收藏状态的会话ID
     * @param isFavorite 目标收藏状态（true：收藏；false：取消收藏）
     */
    fun toggleSessionFavorite(sessionId: Long, isFavorite: Boolean) {
        coroutineScope.launch {
            chatDao.updateSessionFavoriteStatus(sessionId, isFavorite)
        }
    }

    /**
     * 删除指定会话及关联消息
     * 异步执行，确保数据一致性：
     * 1. 先取消该会话的活跃AI请求（避免删除后仍接收回复）
     * 2. 先删除会话关联的所有消息（避免外键约束错误）
     * 3. 再删除会话本身
     *
     * @param sessionId 需删除的会话ID
     */
    fun deleteSession(sessionId: Long) {
        coroutineScope.launch {
            // 第一步：取消该会话的活跃AI请求
            cancelAIResponse(sessionId)

            // 第二步：删除关联消息和会话（IO调度器）
            withContext(Dispatchers.IO) {
                chatDao.deleteMessagesBySessionId(sessionId)
                chatDao.deleteSessionById(sessionId)
            }
        }
    }

    /**
     * 清空本地数据库中所有聊天相关数据
     * 挂起函数，切换至IO调度器执行，调用 [ChatDao.clearDatabase] 清空User、ChatSession、ChatMessage表，
     * @note 操作不可恢复，需谨慎调用（如用户主动触发“清除所有数据”功能）
     */
    suspend fun clearDatabase() {
        withContext(Dispatchers.IO) {
            chatDao.clearDatabase()
        }
    }

    /**
     * 清空所有消息数据（仅ChatMessage表）
     * 挂起函数，切换至IO调度器执行，删除ChatMessage表中所有记录，
     * @note 操作不可恢复，不会影响User和ChatSession表数据
     */
    suspend fun clearAllMessages() {
        withContext(Dispatchers.IO) {
            chatDao.clearAllMessages()
        }
    }

    /**
     * 清空所有会话数据（仅ChatSession表）
     * 挂起函数，切换至IO调度器执行，删除ChatSession表中所有记录，
     * @note 操作不可恢复，建议先删除关联消息（避免外键约束），不会影响User表数据
     */
    suspend fun clearAllSessions() {
        withContext(Dispatchers.IO) {
            chatDao.clearAllSessions()
        }
    }

    /**
     * 清空所有用户数据（仅User表）
     * 挂起函数，切换至IO调度器执行，删除User表中所有记录，
     * @note 操作不可恢复，删除后无当前用户，会影响会话创建等依赖用户的操作
     */
    suspend fun clearAllUsers() {
        withContext(Dispatchers.IO) {
            chatDao.clearAllUsers()
        }
    }

    /**
     * 获取指定会话的未读消息数
     * 挂起函数，从 [ChatDao] 查询该会话中状态为“未读”的消息数量，
     * 用于UI层展示未读标记（如会话列表的小红点）。
     *
     * @param sessionId 会话ID
     * @return Int 该会话的未读消息数（0表示无未读）
     */
    suspend fun getUnreadCount(sessionId: Long): Int {
        return chatDao.getUnreadCount(sessionId)
    }
}

/**
 * AI回复过程中的事件密封类
 * 定义AI回复生命周期中所有可能的事件类型，用于 [ChatRepository.aiResponseStream] 传递状态，
 * 每个事件均关联会话ID，部分事件关联消息ID，确保UI层能精准定位到对应的会话和消息。
 */
sealed class AIResponseEvent {
    /**
     * AI回复开始事件
     * 当AI连接成功并创建消息占位符后发送，表示AI开始生成回复。
     * @param sessionId 会话ID
     * @param messageId AI消息ID（占位符ID）
     */
    data class Start(val sessionId: Long, val messageId: Long) : AIResponseEvent()

    /**
     * AI回复连接中事件
     * 当发起AI请求后、连接建立前发送，表示正在尝试连接AI服务。
     * @param sessionId 会话ID
     * @param messageId AI消息ID（此时可能为-1，尚未创建占位符）
     */
    data class Connecting(val sessionId: Long, val messageId: Long) : AIResponseEvent()

    /**
     * AI回复已连接事件
     * 当AI服务连接成功后发送，表示已准备接收AI回复数据。
     * @param sessionId 会话ID
     * @param messageId AI消息ID（已创建占位符）
     */
    data class Connected(val sessionId: Long, val messageId: Long) : AIResponseEvent()

    /**
     * AI回复增量数据块事件
     * 当接收到AI的增量回复内容时发送，包含新增内容和当前完整内容。
     * @param sessionId 会话ID
     * @param messageId AI消息ID
     * @param chunk 增量内容（仅新增部分）
     * @param fullText 当前完整内容（便于UI层展示完整消息）
     * @param isIncremental 是否为增量更新（默认true）
     */
    data class Chunk(
        val sessionId: Long,
        val messageId: Long,
        val chunk: String,
        val fullText: String,
        val isIncremental: Boolean = false
    ) : AIResponseEvent()

    /**
     * AI回复完成事件
     * 当AI流式回复全部接收完成后发送，表示回复已生成完毕。
     * @param sessionId 会话ID
     * @param messageId AI消息ID
     */
    data class Complete(val sessionId: Long, val messageId: Long) : AIResponseEvent()

    /**
     * AI回复错误事件
     * 当AI请求过程中发生错误（连接失败、解析错误等）时发送，包含错误描述。
     * @param sessionId 会话ID
     * @param message 错误描述信息（用于UI层展示）
     */
    data class Error(val sessionId: Long, val message: String,val errorCode: Int = -1) : AIResponseEvent()

    /**
     * AI回复取消事件
     * 当AI请求被主动取消（如用户触发取消）时发送，表示回复已终止。
     * @param sessionId 会话ID
     * @param messageId AI消息ID
     */
    data class Cancelled(val sessionId: Long, val messageId: Long) : AIResponseEvent()
}