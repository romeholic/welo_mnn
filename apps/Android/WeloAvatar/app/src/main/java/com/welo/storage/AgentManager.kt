package com.welo.storage

import com.welo.entity.AgentData
import com.welo.entity.FlowItem
import com.welo.entity.UserAgentBean
import com.welo.util.LogUtil
import java.util.concurrent.CopyOnWriteArrayList

object AgentManager {

    // 可以考虑将agentList设为私有，通过方法访问，增强封装性
    private val agentList: MutableList<UserAgentBean> = CopyOnWriteArrayList()
    private var selectAgent: UserAgentBean? = null

    /**
     * 保存 AgentData 到本地存储
     * @param agentData AgentData 实例
     * @return 成功保存的UserAgentBean数量
     */
    fun saveAgent(agentData: AgentData): Int {
        return runCatching {
            // 确保 agentData 的属性不为 null
            val flows = requireNotNull(agentData.flows) { "AgentData flows cannot be null" }
            val items = requireNotNull(flows.items) { "AgentData flows items cannot be null" }

            var savedCount = 0
            items.forEach { flow ->
                // 验证flow的必要属性
                val agentId = requireNotNull(flow.id) { "Flow id cannot be null" }

                // 检查是否已存在相同agentId的实例，避免重复添加
                if (agentList.none { it.agentId == agentId }) {
                    val userAgent = flow.toUserAgentBean()
                    agentList.add(userAgent)
                    savedCount++
                    // 这里可以将 userAgent 保存到本地数据库或其他存储方式
                    saveToStorage(userAgent)
                }
            }
            savedCount
        }.onFailure {
            // 处理异常情况，例如记录日志或抛出异常
            when (it) {
                is IllegalArgumentException -> throw it // 直接抛出验证异常
                else -> throw IllegalArgumentException(
                    "Failed to save AgentData: ${it.message}",
                    it
                )
            }
        }.getOrThrow()
    }

    /**
     * 获取指定索引的Agent
     * @param index 索引位置，默认0
     * @return 对应的UserAgentBean
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    fun selectAgent(index: Int = 0): UserAgentBean? {
        runCatching {
            // 检查列表是否为空
            if (agentList.isEmpty()) {
                throw IllegalStateException("Agent list is empty")
            }
            // 检查索引是否有效
            if (index < 0 || index >= agentList.size) {
                throw IndexOutOfBoundsException("Index $index out of bounds for size ${agentList.size}")
            }
            selectAgent = agentList[index]
        }.onFailure {
            LogUtil.d("AgentManager", "Error selecting agent: ${it.message}")
        }
        return selectAgent
    }

    /**
     * 将Flow转换为UserAgentBean
     */
    private fun FlowItem.toUserAgentBean(): UserAgentBean {
        return UserAgentBean(
            agentId = requireNotNull(id) { "Flow id cannot be null" },
            description = description, // 为description提供默认值
            icon = icon ?: ""
        )
    }

    /**
     * 获取当前选中的Agent
     * @return 当前选中的UserAgentBean，如果没有选中则返回null
     */
    fun getSelectedAgent(): UserAgentBean? = selectAgent

    /**
     * 获取所有已保存的UserAgentBean列表
     * @return 不可变的UserAgentBean列表
     */
    fun getAllAgents(): List<UserAgentBean> = agentList.toList() // 返回不可变副本，避免外部修改

    /**
     * 保存UserAgentBean到存储系统
     * 可以根据需要实现数据库存储、SharedPreferences等
     */
    private fun saveToStorage(userAgent: UserAgentBean) {
        // 实际存储逻辑
        // 例如：database.userAgentDao().insert(userAgent)
    }
}