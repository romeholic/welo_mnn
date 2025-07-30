package com.welo.entity

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable,
    val versionCode: Long,
    val versionName: String = ""
)
