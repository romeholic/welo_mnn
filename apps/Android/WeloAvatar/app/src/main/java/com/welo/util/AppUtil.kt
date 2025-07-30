package com.welo.util

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.WorkerThread
import com.alibaba.mls.api.ApplicationProvider
import com.welo.entity.AppInfo


object AppUtil {

    private val appInfos = mutableListOf<AppInfo>()

    /**
     * 获取设备上安装的所有应用信息
     */
    @SuppressLint("QueryPermissionsNeeded")
    @WorkerThread  // 提示应在后台线程调用
    fun getInstalledApps(): List<AppInfo> {
        if (appInfos.isEmpty()) {
            val context = ApplicationProvider.get()
            val packageManager = context.packageManager

            val packageInfos = packageManager.getInstalledPackages(0)

            for (packageInfo in packageInfos) {
                // 跳过系统应用
                if ((packageInfo.applicationInfo?.flags?.and(
                        ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                    )) != 0
                ) {
                    continue
                }

                val applicationInfo = packageInfo.applicationInfo ?: continue
                val appName = applicationInfo.loadLabel(packageManager).toString()
                val packageName = packageInfo.packageName
                val versionName = packageInfo.versionName ?: ""
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                val appIcon = applicationInfo.loadIcon(packageManager)

                appInfos.add(AppInfo(packageName, appName, appIcon, versionCode, versionName))
            }
        }
        return appInfos
    }
}