package com.welo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.taobao.meta.avatar.R
import com.welo.util.LogUtil

class RecordingForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        LogUtil.d("WELOO#RecordingService", "onCreate called")
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.d("WELOO#RecordingService", "onStartCommand called with flags: $flags, startId: $startId")
        val notification = createNotification()
        startForeground(1, notification)
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "recording_channel"
        val channel = NotificationChannel(
            channelId,
            "Recording Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("录音中")
            .setContentText("正在使用麦克风...")
            .setSmallIcon(R.drawable.avatar_x)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}