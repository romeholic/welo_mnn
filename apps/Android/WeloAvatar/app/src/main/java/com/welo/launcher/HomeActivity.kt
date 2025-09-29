package com.welo.launcher

import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.databinding.ActivityHomeBinding
import com.taobao.meta.avatar.tts.TtsService
import com.welo.base.BaseActivity
import com.welo.constant.Constants
import com.welo.constant.Constants.FEATURE_TOUR_KEY
import com.welo.launcher.adapter.ViewPagerAdapter
import com.welo.launcher.anim.DepthPageTransformer
import com.welo.launcher.anim.ZoomOutPageTransformer
import com.welo.launcher.fragment.CameraFragment
import com.welo.launcher.fragment.HomeFragment
import com.welo.launcher.fragment.TaskFragment
import com.welo.launcher.viewmodel.HomeViewModel
import com.welo.login.LoginViewModel
import com.welo.manager.ChatInteractionManager
import com.welo.service.FloatingWindowService
import com.welo.service.ServiceInitializer
import com.welo.util.AudioPlayerUtil
import com.welo.util.LogUtil
import com.welo.util.OverlayPermissionHelper
import com.welo.util.PreferenceUtil
import com.welo.viewmodel.AgentViewModel

class HomeActivity : BaseActivity<ActivityHomeBinding, HomeViewModel>(), ServiceInitializer {

    private var floatingWindowService: FloatingWindowService? = null

    private val loginViewModel: LoginViewModel by viewModels()
    private val agentModel: AgentViewModel by lazy { AgentViewModel() }
    // 页面标题

    private var currentPage = 1

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
//        setupMemoryIcon()
        setupLogin()
        ChatInteractionManager.getInstance().initialize(this)
        initializeGuideModeTag()
        OverlayPermissionHelper.Companion.initNotification(this)
        OverlayPermissionHelper.Companion.initRecordingNotification(this)

        // 先检查权限，权限通过后再绑定服务
        if (OverlayPermissionHelper.Companion.hasOverlayPermission(this)) {
            startAndBindService()
        } else {
            OverlayPermissionHelper.Companion.checkAndStartFloatingService(this)
        }
        initViewPager()
        if (Constants.aiToolList.isEmpty()){
            viewModel.createAiToolsList()
        }

        LogUtil.d(TAG, "设备序列号 getSerialNumber: ${Constants.getSerialNumber()}")
    }
    private fun initViewPager() {
        // 创建适配器
        val adapter = ViewPagerAdapter(this)

        // 添加三个Fragment
        adapter.addFragment(CameraFragment.Companion.newInstance("", ""))
        adapter.addFragment(HomeFragment.Companion.newInstance("", ""))
        adapter.addFragment(TaskFragment.Companion.newInstance())

        // 设置适配器
        viewBinding.viewPager.adapter = adapter
        viewBinding.viewPager.setCurrentItem(currentPage, false) // 第二个参数设为false
        viewBinding.viewPager.isUserInputEnabled = true  // 确保滑动流畅

        viewBinding.viewPager.setPageTransformer(ZoomOutPageTransformer())
        viewBinding.viewPager.setPageTransformer(DepthPageTransformer())
        // 监听页面变化
        viewBinding.viewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 更新指示器状态
            }
        })
        if (PreferenceUtil.get()
                .getBoolean(FEATURE_TOUR_KEY)
        ) viewBinding.viewPager.setUserInputEnabled(false) else viewBinding.viewPager.setUserInputEnabled(
            true
        )
    }

    fun viewPagerToHome(){
        viewBinding.viewPager.currentItem = 1
    }

    private fun setupLogin() {
        loginViewModel.login("ming", "000000")
        loginViewModel.loginResult.observe(this) { result ->
            if (result.success != null) {
                agentModel.getAgent()
		viewModel.setLoginState(true)
            }
            if (result.error != null){
                loginViewModel.login("ming", "000000")
            }

        }
    }

    private fun startAndBindService() {
        OverlayPermissionHelper.Companion.startFloatingService(this)
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
    override fun adaptStatusBarTextColor() {
        // 强制使用浅色文字（适合深色背景）
        setStatusBarTextColor(false)
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {
        super.onActivityResult(requestCode, resultCode, data, caller)
        LogUtil.d(TAG, "onActivityResult called with requestCode: $requestCode")
        if (requestCode == OverlayPermissionHelper.Companion.REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startAndBindService() // 权限获取成功后再启动服务
            }
        }
    }

    override fun onDestroy() {
        try {
            unbindService(serviceConnection)
        }catch (e: Exception){

        }
        super.onDestroy()
    }

    override fun getRecognizeService() = RecognizeService(this)
    override fun getAudioPlayer(ttsService: TtsService): AudioPlayerUtil {
        return AudioPlayerUtil(ttsService)
    }

    override fun getContext(): Context = this
    private fun initializeGuideModeTag() {
        if (!PreferenceUtil.get().isKeyExists(FEATURE_TOUR_KEY)) {
            PreferenceUtil.get().putBoolean(FEATURE_TOUR_KEY, true)
        }
    }

    companion object {
        private const val TAG = "HomeActivity"

        init {
            System.loadLibrary("taoavatar")
        }
    }
}