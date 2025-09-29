package com.welo.base

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import java.lang.reflect.ParameterizedType


abstract class BaseActivity<VB : ViewBinding, VM : ViewModel> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val viewBinding get() = _binding!!

    protected val viewModel: VM by lazy {
        ViewModelProvider(this, createViewModelFactory())[getViewModelClass()]
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = createBinding()
        setContentView(viewBinding.root)

        // 2. 启用边缘到边缘模式
        enableEdgeToEdge()

        // 3. 适配布局内边距（避免内容被系统栏遮挡）
        adaptLayoutPadding(viewBinding.root)

        // 初始化视图和观察ViewModel
        initView()
        observeViewModel()

    }

    /**
     * 启用边缘到边缘模式的核心配置
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun enableEdgeToEdge() {
        val window = getWindow()
        if (window == null) return

        // 关键：允许应用内容延伸至系统栏下方（解除系统对布局的约束）
        window.setDecorFitsSystemWindows(false)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val insetsController = window.insetsController
        if (insetsController != null) {
            // 浅色背景时，设置状态栏文字为深色（反之则注释此行）
            insetsController.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
            // 可选：设置导航栏图标为深色（适用于浅色背景）
            insetsController.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
        hideSystemBarsCompat()
        adaptStatusBarTextColor()
    }

    /**
     * 调整布局内边距，避免内容被状态栏/导航栏遮挡
     */
    private fun adaptLayoutPadding(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(
            rootView
        ) { v: View?, insets: WindowInsetsCompat? ->
            // 获取系统栏（状态栏+导航栏）的边距
            val systemInsets: Insets = insets!!.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )

            // 获取刘海屏/挖孔屏的安全区域（避免内容被裁切）
            val cutoutInsets: Insets =
                insets.getInsets(WindowInsetsCompat.Type.displayCutout())

            // 计算最终内边距（取两者最大值，确保兼容性）
            val left: Int = systemInsets.left.coerceAtLeast(cutoutInsets.left)
            val top: Int = systemInsets.top.coerceAtLeast(cutoutInsets.top)
            val right: Int = systemInsets.right.coerceAtLeast(cutoutInsets.right)
            val bottom: Int = systemInsets.bottom.coerceAtLeast(cutoutInsets.bottom)

            // 设置布局内边距
            v!!.setPadding(left, top, right, bottom)
            insets.consumeSystemWindowInsets()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onResume() {
        super.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    // 计算颜色的亮度（0-255）
    private fun calculateColorBrightness(color: Int): Double {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (0.299 * red + 0.587 * green + 0.114 * blue)
    }

    // 判断颜色是否为深色
    private fun isDarkColor(color: Int): Boolean {
        val brightness = calculateColorBrightness(color)
        return brightness < 128
    }

    // 从根视图获取主要背景色
    private fun getRootBackgroundColor(): Int {
        return try {
            // 首先尝试获取根视图的背景色
            val background = viewBinding.root.background
            if (background is android.graphics.drawable.ColorDrawable) {
                background.color
            } else {
                // 如果根视图没有背景色，则检查主题的背景色
                val typedArray = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
                val backgroundColor = typedArray.getColor(0, Color.WHITE)
                typedArray.recycle()
                backgroundColor
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Color.WHITE
        }
    }

    // 自适应状态栏字体颜色
    protected open fun adaptStatusBarTextColor() {
        val backgroundColor = getRootBackgroundColor()
        val isDarkBackground = isDarkColor(backgroundColor)

        // 对于深色背景，使用浅色文字；对于浅色背景，使用深色文字
        setStatusBarTextColor(!isDarkBackground)
    }

    // 设置状态栏字体颜色
    protected fun setStatusBarTextColor(useDarkText: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                val appearance = if (useDarkText) {
                    // 使用深色文字
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                } else {
                    // 使用浅色文字
                    0
                }
                controller.setSystemBarsAppearance(
                    appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            var systemUiVisibility = window.decorView.systemUiVisibility
            if (useDarkText) {
                // 启用深色状态栏文字
                systemUiVisibility = systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                // 禁用深色状态栏文字（使用浅色文字）
                systemUiVisibility =
                    systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.decorView.systemUiVisibility = systemUiVisibility
        }
    }

    // 隐藏系统栏（导航栏）
    fun hideSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // 显示系统栏（导航栏）
    fun showSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.show(WindowInsetsCompat.Type.navigationBars())
    }

    protected open fun createViewModelFactory(): ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    @Suppress("UNCHECKED_CAST")
    private fun getViewModelClass(): Class<VM> {
        val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[1]
        return type as Class<VM>
    }

    protected abstract fun createBinding(): VB
    protected abstract fun initView()
    protected abstract fun observeViewModel()
}