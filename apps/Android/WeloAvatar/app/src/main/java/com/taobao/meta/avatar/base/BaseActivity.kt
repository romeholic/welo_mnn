package com.taobao.meta.avatar.base

import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import java.lang.reflect.ParameterizedType

abstract class BaseActivity <VB : ViewBinding, VM : ViewModel> : AppCompatActivity() {

    // 使用委托管理ViewBinding的生命周期
    private var _binding: VB? = null
    protected val viewBinding get() = _binding!!

    // 延迟初始化ViewModel
    protected val viewModel: VM by lazy {
        ViewModelProvider(this, createViewModelFactory())[getViewModelClass()]
    }
    private val navBarHeight: Int
        get() {
            val insets = ViewCompat.getRootWindowInsets(viewBinding.root)
            return insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = createBinding()
        setContentView(viewBinding.root)
        initView()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    // 创建ViewModel工厂，可在子类中重写
    protected open fun createViewModelFactory(): ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)

    // 获取ViewModel的Class对象
    @Suppress("UNCHECKED_CAST")
    private fun getViewModelClass(): Class<VM> {
        val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[1]
        return type as Class<VM>
    }

    // 由子类实现创建ViewBinding的方法
    protected abstract fun createBinding(): VB

    // 由子类实现初始化视图的方法
    protected abstract fun initView()

    // 由子类实现观察ViewModel数据的方法
    protected abstract fun observeViewModel()

//    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
//        if (ev.action == MotionEvent.ACTION_DOWN) {
//            val view = currentFocus
//            if (view is EditText) {
//                val outRect = Rect()
//                view.getGlobalVisibleRect(outRect)
//                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
//                    view.clearFocus()
//                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
//                    imm.hideSoftInputFromWindow(view.windowToken, 0)
//                }
//            }
//        }
//        return super.dispatchTouchEvent(ev)
//    }
}