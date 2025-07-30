package com

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.alibaba.mls.api.ApplicationProvider
import com.welo.util.LogUtil

class WeLoApplication : Application() {
    companion object {
        @Volatile
        private var instance: WeLoApplication? = null

        /**
         * 获取Application实例
         * 注意：需在Application.onCreate()之后调用
         */
        fun getInstance(): WeLoApplication {
            return instance ?: synchronized(this) {
                instance ?: throw IllegalStateException("WeLoApplication has not been initialized yet")
            }
        }
    }

    // 使用计数器跟踪前台Activity数量，更准确判断应用是否在前台
    private var foregroundActivityCount = 0
    private var isAppInForeground = false

    /**
     * 判断应用是否处于前台状态
     */
    fun isAppInForeground(): Boolean = isAppInForeground

    override fun onCreate() {
        super.onCreate()
        // 初始化单例实例
        instance = this
        ApplicationProvider.set(this)
        registerLifecycleCallbacks()
    }

    /**
     * 注册Activity生命周期回调，用于跟踪应用前后台状态
     */
    private fun registerLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                foregroundActivityCount++
                LogUtil.d("WeLoApplication", "Activity started: $foregroundActivityCount")
                // 当从0变为1时，说明应用进入前台
                if (foregroundActivityCount == 1) {
                    isAppInForeground = true
                    // 可以在这里添加应用进入前台的逻辑
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                foregroundActivityCount--
                LogUtil.d("WeLoApplication", "Activity stopped: $foregroundActivityCount")
                // 当从1变为0时，说明应用进入后台
                if (foregroundActivityCount == 0) {
                    isAppInForeground = false
                    // 可以在这里添加应用进入后台的逻辑
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onTerminate() {
        super.onTerminate()
        // 清除单例引用，避免内存泄漏
        instance = null
    }
}