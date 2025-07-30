// Created by ruoyi.sjd on 2025/3/12.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.record

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.alibaba.mls.api.ApplicationProvider
import com.welo.MainActivityWeLoActivity
import com.welo.util.LogUtil


object RecordPermission {
    const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    const val MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 1001
    const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 1002

    const val REQUEST_RECORD_OVERLAY_PERMISSION = 1003
    val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    // 检查是否有存储权限
     fun checkStoragePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：检查 MANAGE_EXTERNAL_STORAGE 或分区存储权限
            if (Environment.isExternalStorageManager()) {
                return true // 拥有所有文件访问权限
            }
            // 或检查是否有访问媒体文件的权限（通过 MediaStore）
            return ContextCompat.checkSelfPermission(
                ApplicationProvider.get(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 10及以下：检查传统存储权限
            return ContextCompat.checkSelfPermission(
                ApplicationProvider.get(), Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        ApplicationProvider.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 请求存储权限
     fun requestStoragePermissions(activity: MainActivityWeLoActivity) {
        LogUtil.d("RecordPermission", "requestStoragePermissions called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：引导用户到设置页面授权 MANAGE_EXTERNAL_STORAGE
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            // 传入当前应用的包名，定位到目标应用
            intent.data = "package:${activity.packageName}".toUri()
            activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE)
        } else {
            // Android 10及以下：请求传统存储权限
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                READ_EXTERNAL_STORAGE_REQUEST_CODE
            )
        }
    }

    // 检查是否有录音权限
     fun checkRecordAudioPermission(activity: MainActivityWeLoActivity): Boolean {
         if (checkStoragePermissions()){
             requestStoragePermissions(activity)
             return false
         }else{
             return true
         }
    }
}