package com.welo.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.welo.service.FloatingWindowService
import androidx.core.net.toUri

/**
 * 悬浮窗权限帮助类
 */
class OverlayPermissionHelper {
    companion object {
        private const val TAG = "OverlayPermissionHelper"
        const val REQUEST_OVERLAY_PERMISSION: Int = 1001

        /**
         * 初始化通知渠道
         * 在 Android 8.0 及以上版本需要创建通知渠道
         */
        fun initNotification(context: Context) {
            // 在启动服务前创建通知渠道（建议在 Application 或 Activity 中初始化）
            val channelName = "Foreground Service Channel"
            val importance = NotificationManager.IMPORTANCE_DEFAULT // 至少为 IMPORTANCE_LOW 以上

            val channel =
                NotificationChannel(FloatingWindowService.CHANNEL_ID, channelName, importance)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        fun initRecordingNotification(context: Context) {
            // 在启动录音服务前创建通知渠道
            val channelName = "Recording Service Channel"
            val importance = NotificationManager.IMPORTANCE_LOW // 录音服务通常不需要高优先级

            val channel = NotificationChannel("recording_channel", channelName, importance)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        /**
         * 检查是否有悬浮窗权限
         */
        fun hasOverlayPermission(context: Context?): Boolean {
            return Settings.canDrawOverlays(context)
        }

        private fun hasRequiredPermissions(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // 检查 Android 14+ 特殊权限
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        @SuppressLint("QueryPermissionsNeeded")
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun checkAndStartFloatingService(context: Activity) {
            val overlayPermission = hasOverlayPermission(context)
            val hasRequiredPermissions = hasRequiredPermissions(context)
            LogUtil.d(
                TAG,
                "Overlay Permission: $overlayPermission, Special Permission: $hasRequiredPermissions"
            )
            when {
                !hasOverlayPermission(context) -> {
                    openAppSpecificOverlaySettings(context)
                }
                !hasRequiredPermissions(context) -> {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE),
                        REQUEST_OVERLAY_PERMISSION // 使用统一请求码
                    )
                }
                else -> {
                    startFloatingService(context)
                }
            }
        }

        fun startFloatingService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.startForegroundService(intent)
        }

        private fun openXiaomiOverlaySettings(context: Context) {
            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
        }

        private fun openOppoOverlaySettings(context: Context) {
            try {
                val intent = Intent().apply {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
        }

        private fun openHuaweiOverlaySettings(context: Context) {
            try {
                val intent = Intent().apply {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
                    )
                    putExtra("packageName", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    openAppDetailsSettings(context)
                }
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
        }

        private fun openAppDetailsSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        @SuppressLint("QueryPermissionsNeeded")
        fun openAppSpecificOverlaySettings(context: Activity) {
            LogUtil.d(TAG, "Opening app-specific overlay settings:${Build.MANUFACTURER}")
            when {
                // 小米设备
                Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) -> {
                    openXiaomiOverlaySettings(context)
                }
                // OPPO设备
                Build.MANUFACTURER.equals("OPPO", ignoreCase = true) -> {
                    openOppoOverlaySettings(context)
                }
                // 华为设备
                Build.MANUFACTURER.equals("Huawei", ignoreCase = true) -> {
                    openHuaweiOverlaySettings(context)
                }
                // 其他设备尝试标准方式
                else -> {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        // 设置目标应用的包名
                        data = ("package:" + context.packageName).toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        LogUtil.d(TAG, "Requesting overlay permission")
                        context.startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                    } else {
                        openAppDetailsSettings(context)
                    }
                }
            }
        }
    }


}