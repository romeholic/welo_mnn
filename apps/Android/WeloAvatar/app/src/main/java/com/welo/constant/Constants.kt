package com.welo.constant

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import com.WeLoApplication
import com.alibaba.mls.api.ApplicationProvider
import com.welo.launcher.entity.AIOptionItem
import com.welo.storage.AgentManager
import com.welo.util.SystemUtils


object Constants {

    const val chatSessionId = 10086L
    const val sessionId = "ming"
    const val guidesessionId = "guidance"
    /**
     * 默认
     */
    const val AGENT_TYPE_WELO_CHAT = "welo-fast"

    /**
     * 工具
     */
    const val AGENT_TYPE_WELO_TOOLS = "welo"

    /**
     * 引导
     */
    const val AGENT_TYPE_WELO_GUIDANCE = "welo-guidance"
    /**
     * LLM模型存储路径
     */
    val SD_CARD_PATH = ApplicationProvider.get().filesDir.absolutePath
    /**
     * 测试服务器地址
     */
    private const val BASE_URL = "http://8.137.17.226:7860"
    /**
     * 用户引导TAG
     */
    const val FEATURE_TOUR_KEY = "feature_tour_key"
    /**
     * 用户引导结束后第一次进入home显示的欢迎词tag
     */
    const val FEATURE_HOME_WELCOME = "home_welcome_key"
    /**
     * 获取LLM模型的URL
     * @return 完整的LLM模型URL
     */
    fun getLlmPath(): String {
        val agentId = AgentManager.selectAgent()?.agentId ?: ""
        return "$BASE_URL/api/v1/build/$agentId/flow?event_delivery=direct"
    }

    /**
     * 上传图片
     */
    fun getImageUploadPath(): String{
        val agentId = AgentManager.selectAgent()?.agentId ?: ""
        return "$BASE_URL/api/v1/files/upload/$agentId"
    }

    /**
     * 加载图片地址
     */
    fun servicesImagePath(filePath: String): String{
        return "$BASE_URL/api/v1/files/download/$filePath"
    }

    var agentType : String= AGENT_TYPE_WELO_CHAT
    /**
     * 注册
     */
    const val REGISTER = "$BASE_URL/api/v1/users/"

    /**
     * 登录
     */
    const val LOGIN = "$BASE_URL/api/v1/login"

    /**
     * 刷新TOKEN
     */
    const val REFRESH_TOKEN = "$BASE_URL/api/v1/refresh"

    /**
     * 获取agent列表
     */
    const val AGENT_LIST = "$BASE_URL/api/v1/projects/welo"
    /**
     * 引导页的动画duration ，ms
     */
    const val GUIDE_ANIMATOR_DURATION = 3000L

    var aiToolList: List<AIOptionItem> = listOf()

//    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    @SuppressLint("HardwareIds")
    fun getSerialNumber(): String? {
        var serial: String? = ""
        try {
//            serial = Build.getSerial()
            serial = Settings.Secure.getString(WeLoApplication.getInstance().contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return serial
    }
}