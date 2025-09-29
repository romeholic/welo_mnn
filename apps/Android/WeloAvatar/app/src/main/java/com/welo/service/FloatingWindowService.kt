package com.welo.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import com.taobao.meta.avatar.R
import com.welo.launcher.HomeActivity
import com.welo.util.LogUtil
import com.welo.viewmodel.MessageViewModel
import com.welo.widget.ChatFloatView
import com.welo.widget.FloatingView


class FloatingWindowService : Service() {
    companion object {
        private const val NOTIFICATION_ID: Int = 10086
        const val CHANNEL_ID: String = "floating_window_channel"
    }
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var serviceInitializer: ServiceInitializer? = null

    // 声明ViewModel实例
    private lateinit var messageViewModel: MessageViewModel
    private var chatFloatView: ChatFloatView? = null

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        LogUtil.d("FloatingWindowService", "onCreate called")
        startForegroundWithNotification()
        // 初始化悬浮窗
//        setServiceInitializer()
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    fun setServiceInitializer(initializer: ServiceInitializer) {
        this.serviceInitializer = initializer
        createChatFloatView()
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createChatFloatView() {
        messageViewModel = ViewModelProvider.NewInstanceFactory().create(MessageViewModel::class.java)

        FloatingView(this)
        // 初始化悬浮窗，并传入ViewModel
//        serviceInitializer?.let {
//            chatFloatView = ChatFloatView(context = this, serviceInitializer = it).apply {
//                initWithViewModel(messageViewModel) // 自定义方法传入ViewModel
//            }
//        } ?: run {
//            LogUtil.e("FloatingWindowService", "ServiceInitializer is null, cannot create ChatFloatView")
//        }
    }
    @SuppressLint("ForegroundServiceType")
    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.avatar_x)
                .setContentTitle("悬浮窗服务运行中")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            // 旧版本处理
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮窗服务运行中")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // 在 Service 的 onStartCommand 中创建通知
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.avatar_x) // 必须设置小图标（不能用 mipmap）
            .setContentTitle("Foreground Service") // 标题（可选但建议设置）
            .setContentText("Running...") // 内容（可选但建议设置）
            .setContentIntent(pendingIntent) // 点击意图（可选）
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 优先级
            .build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 返回 START_STICKY 使服务被杀死后自动重启
        // 创建通知
        val notification = createForegroundNotification()
        // 启动前台服务（id 必须大于 0）
        startForeground(NOTIFICATION_ID, notification) // 第一个参数为通知 ID（非 0）
        return START_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager!!.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return LocalBinder()
    }
    // 定义明确的 Binder 子类
    inner class LocalBinder : Binder() {
        fun getService(): FloatingWindowService = this@FloatingWindowService
    }
}