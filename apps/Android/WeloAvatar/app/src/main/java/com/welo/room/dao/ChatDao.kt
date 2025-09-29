package com.welo.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.welo.room.table.ChatMessage
import com.welo.room.table.ChatSession
import com.welo.room.table.User
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // region 消息内容操作

    /**
     * 根据消息ID获取消息内容
     * @param messageId 消息ID
     * @return 消息内容字符串，如果消息不存在则返回null
     */
    @Query("SELECT content FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageContent(messageId: Long): String?

    // 在 ChatDao 接口中添加
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND status = :status")
    suspend fun getMessagesBySessionIdAndStatus(sessionId: Long, status: String): List<ChatMessage>

    /**
     * 更新指定消息的内容
     * @param messageId 要更新的消息ID
     * @param content 新的消息内容
     */
    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    /**
     * 更新指定消息的状态
     * @param messageId 要更新的消息ID
     * @param status 新的状态值（如：SENDING、SENT、FAILED、GENERATING等）
     */
    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: String)

    // endregion

    // region 用户相关操作

    /**
     * 插入新用户到数据库
     * @param user 用户实体对象
     * @return 插入的行ID（Room自动处理）
     */
    @Insert
    suspend fun insertUser(user: User)

    /**
     * 获取当前登录用户
     * @return 当前用户实体对象，如果不存在则返回null
     */
    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): User?

    // endregion

    // region 会话相关操作

    /**
     * 插入新会话到数据库
     * @param session 会话实体对象
     * @return 插入的行ID（Room自动处理）
     */
    @Insert
    suspend fun insertSession(session: ChatSession)

    /**
     * 获取所有会话的流式数据（实时更新）
     * @return 会话列表的Flow，按更新时间降序排列
     */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    /**
     * 获取所有聊天记录（一次性查询）
     * @return 所有消息列表，按时间戳升序排列（最早的消息在前）
     */
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    /**
     * 获取指定用户的所有会话流式数据
     * @param userId 用户ID
     * @return 会话列表的Flow，按更新时间降序排列
     */
    @Query("SELECT * FROM chat_sessions WHERE userId = :userId ORDER BY updatedAt DESC")
    fun getSessionsByUser(userId: Long): Flow<List<ChatSession>>

    /**
     * 根据会话ID获取会话详情
     * @param sessionId 会话ID
     * @return 会话实体对象，如果不存在则返回null
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    /**
     * 更新会话信息
     * @param session 更新后的会话实体对象
     */
    @Update
    suspend fun updateSession(session: ChatSession)

    /**
     * 根据会话ID删除会话
     * @param sessionId 要删除的会话ID
     */
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    /**
     * 删除指定的会话
     * @param session 要删除的会话实体对象
     */
    @Delete
    suspend fun deleteSession(session: ChatSession)

    /**
     * 删除指定会话的所有消息
     * @param sessionId 会话ID
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)

    /**
     * 更新会话的收藏状态
     * @param sessionId 会话ID
     * @param isFavorite 是否收藏
     */
    @Query("UPDATE chat_sessions SET isFavorite = :isFavorite WHERE id = :sessionId")
    suspend fun updateSessionFavoriteStatus(sessionId: Long, isFavorite: Boolean)


    /**
     * 插入新消息到数据库
     * @param message 消息实体对象
     * @return 插入的消息ID
     */
    @Insert
    suspend fun insertMessage(message: ChatMessage): Long

    /**
     * 获取指定会话的所有消息流式数据（实时更新）
     * @param sessionId 会话ID
     * @return 消息列表的Flow，按时间戳升序排列（最早的消息在前）
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySessionId(sessionId: Long): Flow<List<ChatMessage>>

    /**
     * 获取指定会话的所有消息列表（一次性查询）
     * @param sessionId 会话ID
     * @return 消息列表，按时间戳升序排列（最早的消息在前）
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionIdList(sessionId: Long): List<ChatMessage>


    /**
     * 更新消息信息
     * @param message 更新后的消息实体对象
     */
    @Update
    suspend fun updateMessage(message: ChatMessage)

    /**
     * 更新消息状态（Int参数版本，用于兼容性）
     * @param messageId 要更新的消息ID
     * @param status 新的状态值
     */
    @Query("UPDATE chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Int, status: String)

    /**
     * 获取指定会话的未读消息数量
     * @param sessionId 会话ID
     * @return 未读AI消息的数量（用户发送的消息不计入未读）
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND isRead = 0 AND senderType = 'AI'")
    suspend fun getUnreadCount(sessionId: Long): Int

    /**
     * 清空用户表（users）中的所有数据
     * 该操作会删除users表中所有用户记录，执行后不可恢复
     * 使用DELETE语句实现全表数据删除
     */
    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    /**
     * 清空会话表（chat_sessions）中的所有数据
     * 该操作会删除所有聊天会话记录，执行后不可恢复
     * 使用DELETE语句实现全表数据删除
     */
    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    /**
     * 清空消息表（chat_messages）中的所有数据
     * 该操作会删除所有聊天消息记录，包括所有会话的历史消息，执行后不可恢复
     * 使用DELETE语句实现全表数据删除
     */
    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    /**
     * 清空数据库（所有表）
     */
    @Transaction
    suspend fun clearDatabase() {
        clearAllMessages()
        clearAllSessions()
        clearAllUsers()
    }

}