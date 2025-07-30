package com.welo

import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.databinding.ActivityHomeBinding
import com.taobao.meta.avatar.tts.TtsService
import com.welo.adapter.ViewPagerAdapter
import com.welo.base.BaseActivity
import com.welo.fragment.AppFragment
import com.welo.fragment.CameraFragment
import com.welo.fragment.HomeFragment
import com.welo.service.FloatingWindowService
import com.welo.service.ServiceInitializer
import com.welo.util.AudioPlayerUtil
import com.welo.util.LogUtil
import com.welo.util.OverlayPermissionHelper
import com.welo.util.OverlayPermissionHelper.Companion.REQUEST_OVERLAY_PERMISSION
import com.welo.util.PreferenceUtil
import com.welo.viewmodel.HomeViewModel


class HomeActivity : BaseActivity<ActivityHomeBinding, HomeViewModel>(), ServiceInitializer {

    private var floatingWindowService: FloatingWindowService? = null
    // 页面标题
    private val titles = listOf("首页", "发现", "我的")
    // 指示器点的集合
    private val indicators = mutableListOf<View>()

    private var currentPage = 1
    private var lastSelectedPosition = -1
    private val serviceConnection = object : ServiceConnection {

        @RequiresApi(Build.VERSION_CODES.Q)
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            val binder = service as FloatingWindowService.LocalBinder
            floatingWindowService = binder.getService().apply {
                setServiceInitializer(this@HomeActivity)  // 传递 Activity 的接口实现
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatingWindowService = null
        }
    }
    override fun createBinding(): ActivityHomeBinding {
        ApplicationProvider.set(application = application)
        return ActivityHomeBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun initView() {
        hideSystemBarsCompat()
        PreferenceUtil.init(this)
        OverlayPermissionHelper.initNotification(this)
        OverlayPermissionHelper.initRecordingNotification(this)

        // 先检查权限，权限通过后再绑定服务
        if (OverlayPermissionHelper.hasOverlayPermission(this)) {
            startAndBindService()
        } else {
            OverlayPermissionHelper.checkAndStartFloatingService(this)
        }
        initViewPager()
        initIndicators()
    }
    private fun initViewPager() {
        // 创建适配器
        val adapter = ViewPagerAdapter(this)

        // 添加三个Fragment
        adapter.addFragment(CameraFragment.newInstance("",""))
        adapter.addFragment(HomeFragment.newInstance("",""))
        adapter.addFragment(AppFragment.newInstance("",""))

        // 设置适配器
        viewBinding.viewPager.adapter = adapter
        viewBinding.viewPager.currentItem = currentPage
        viewBinding.viewPager.offscreenPageLimit = 1  // 只预加载相邻1个页面
        viewBinding.viewPager.isUserInputEnabled = true  // 确保滑动流畅

        // 监听页面变化
        viewBinding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 更新指示器状态
                updateIndicators(position)
            }
        })
    }

    private fun initIndicators() {
        // 清除之前的指示器
        viewBinding.indicatorContainer.removeAllViews()
        indicators.clear()

        // 创建指示器点
        for (i in titles.indices) {
            val indicator = View(this)
            // 设置指示器默认样式
            val params = LinearLayout.LayoutParams(16, 16)
            params.setMargins(8, 0, 8, 0)
            indicator.layoutParams = params
            indicator.setBackgroundResource(R.drawable.indicator_unselected)

            // 添加到容器
            viewBinding.indicatorContainer.addView(indicator)
            indicators.add(indicator)
        }

        // 默认选中第一个页面
        updateIndicators(currentPage)
    }

    private fun updateIndicators(selectedPosition: Int) {
        if (selectedPosition == lastSelectedPosition) return
        lastSelectedPosition = selectedPosition
        // 更新所有指示器状态
        for (i in indicators.indices) {
            indicators[i].setBackgroundResource(
                if (i == selectedPosition)
                    R.drawable.indicator_selected
                else
                    R.drawable.indicator_unselected
            )
        }
    }
    private fun startAndBindService() {
        OverlayPermissionHelper.startFloatingService(this)
        try {
            val bound = bindService(
                Intent(this, FloatingWindowService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )
            if (!bound) {
                LogUtil.e(TAG, "Service binding failed")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Service binding error", e)
        }
    }
    override fun observeViewModel() {

    }

    // 处理权限请求结果
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {
        super.onActivityResult(requestCode, resultCode, data, caller)
        LogUtil.d(TAG, "onActivityResult called with requestCode: $requestCode")
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startAndBindService() // 权限获取成功后再启动服务
            }
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }

    override fun getRecognizeService() = RecognizeService(this)
    override fun getAudioPlayer(ttsService: TtsService): AudioPlayerUtil {
        return AudioPlayerUtil(ttsService)
    }

    override fun getContext(): Context = this

    companion object{
        private const val TAG = "HomeActivity"
        init {
            System.loadLibrary("taoavatar")
        }
    }
}