package com

import ResourceProvider
import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.alibaba.mls.api.ApplicationProvider
import com.bytedance.speech.speechengine.SpeechEngineGenerator
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.welo.room.AppDatabase
import com.welo.launcher.repository.ChatRepository
import com.welo.util.LogUtil
import com.welo.util.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class WeLoApplication : Application() {
    companion object {
        @Volatile
        private var instance: WeLoApplication? = null

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val chatRepository: ChatRepository by lazy {
            instance!!.getChatRepository()
        }

        fun getInstance(): WeLoApplication {
            return instance
                ?: throw IllegalStateException("WeLoApplication has not been initialized yet")
        }
    }

    private lateinit var appDatabase: AppDatabase
    private val foregroundActivityCount = AtomicInteger(0)

    @Volatile
    private var isAppInForeground = false

    fun isAppInForeground(): Boolean = isAppInForeground

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDatabase()
        initializeDefaultUser()
        ApplicationProvider.set(this)
        ResourceProvider.init(this)
        registerLifecycleCallbacks()
        prepareEnvironmentByteDance()
        sparkChainInit()
        PreferenceUtil.Companion.init(this)
    }

    private fun sparkChainInit() {
        val config = SparkChainConfig.builder()
            .appID("d1861174")
            .apiKey("fa05d8164a2a7769cc5e39c96e13b1df")
            .apiSecret("MzhlMzllZjQ0NmI5ODBjNWVlMTBlOTMx")
        SparkChain.getInst().init(this, config)
    }

    private fun initializeDatabase() {
        appDatabase = AppDatabase.getDatabase(this)
    }

    private fun getChatRepository(): ChatRepository {
        return ChatRepository(appDatabase.chatDao(), externalScope = applicationScope)
    }

    private fun initializeDefaultUser() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val currentUser = chatRepository.getCurrentUser()
                if (currentUser == null) {
                    chatRepository.createUser("Default User", true)
                    LogUtil.d("WeLoApplication", "Default user created")
                }
            } catch (e: Exception) {
                LogUtil.e("WeLoApplication", "Failed to initialize default user", e)
            }
        }
    }

    private fun registerLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                val count = foregroundActivityCount.incrementAndGet()
                LogUtil.d("WeLoApplication", "Activity started: $count")
                if (count == 1) {
                    isAppInForeground = true
                    onAppEnterForeground()
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                val count = foregroundActivityCount.decrementAndGet()
                LogUtil.d("WeLoApplication", "Activity stopped: $count")
                if (count == 0) {
                    isAppInForeground = false
                    onAppEnterBackground()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onAppEnterForeground() {
        // 应用进入前台的逻辑
        LogUtil.d("WeLoApplication", "App entered foreground")
    }

    private fun onAppEnterBackground() {
        // 应用进入后台的逻辑
        LogUtil.d("WeLoApplication", "App entered background")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        cleanupResources()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            cleanupResources()
        }
    }

    private fun cleanupResources() {
        // 清理不必要的缓存资源
        LogUtil.d("WeLoApplication", "Cleaning up resources")
    }

    override fun onTerminate() {
        super.onTerminate()
        instance = null
        applicationScope.cancel()
    }

    private fun prepareEnvironmentByteDance() {
        SpeechEngineGenerator.PrepareEnvironment(getApplicationContext(), instance)
    }

}