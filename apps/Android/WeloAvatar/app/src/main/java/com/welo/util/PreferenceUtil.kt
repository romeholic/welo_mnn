package com.welo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences 工具类，提供简单的键值对存储功能
 */
class PreferenceUtil private constructor(context: Context, name: String = "app_preferences") {
    companion object {
        @Volatile
        private var instance: PreferenceUtil? = null

        /**
         * 初始化工具类实例（需在应用启动时调用）
         */
        fun init(context: Context, name: String = "app_preferences"): PreferenceUtil {
            return instance ?: synchronized(this) {
                instance ?: PreferenceUtil(context.applicationContext, name).also { instance = it }
            }
        }

        /**
         * 获取工具类实例
         */
        fun get(): PreferenceUtil {
            return instance ?: throw IllegalStateException("PreferenceUtil 未初始化，请先调用 init()")
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * 存储布尔值
     */
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    /**
     * 获取布尔值，默认值为 false
     */
    fun getBoolean(key: String): Boolean = prefs.getBoolean(key, false)

    /**
     * 获取布尔值，指定默认值
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    /**
     * 存储整数值
     */
    fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    /**
     * 获取整数值，默认值为 0
     */
    fun getInt(key: String): Int = prefs.getInt(key, 0)

    /**
     * 获取整数值，指定默认值
     */
    fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    /**
     * 存储长整数值
     */
    fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    /**
     * 获取长整数值，默认值为 0
     */
    fun getLong(key: String): Long = prefs.getLong(key, 0L)

    /**
     * 获取长整数值，指定默认值
     */
    fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    /**
     * 存储浮点数值
     */
    fun putFloat(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    /**
     * 获取浮点数值，默认值为 0f
     */
    fun getFloat(key: String): Float = prefs.getFloat(key, 0f)

    /**
     * 获取浮点数值，指定默认值
     */
    fun getFloat(key: String, defaultValue: Float): Float = prefs.getFloat(key, defaultValue)

    /**
     * 存储字符串
     */
    fun putString(key: String, value: String?) {
        prefs.edit { putString(key, value) }
    }

    /**
     * 获取字符串，默认值为 null
     */
    fun getString(key: String): String? = prefs.getString(key, null)

    /**
     * 获取字符串，指定默认值
     */
    fun getString(key: String, defaultValue: String?): String? = prefs.getString(key, defaultValue)

    /**
     * 存储字符串集合
     */
    fun putStringSet(key: String, value: Set<String>?) {
        prefs.edit { putStringSet(key, value) }
    }

    /**
     * 获取字符串集合，默认值为 null
     */
    fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)

    /**
     * 获取字符串集合，指定默认值
     */
    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        prefs.getStringSet(key, defaultValue)

    /**
     * 检查是否包含某个键
     */
    fun contains(key: String): Boolean = prefs.contains(key)

    /**
     * 移除指定键值对
     */
    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    /**
     * 清除所有数据
     */
    fun clear() {
        prefs.edit { clear() }
    }

    /**
     * 注册偏好变化监听器
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * 注销偏好变化监听器
     */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}