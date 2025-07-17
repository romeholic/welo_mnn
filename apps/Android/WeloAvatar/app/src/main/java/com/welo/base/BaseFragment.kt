package com.welo.base

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.taobao.meta.avatar.R
import java.lang.reflect.ParameterizedType

abstract class BaseFragment<VB : ViewBinding, VM : ViewModel> : Fragment() {

    // 使用委托管理ViewBinding的生命周期
    private var _binding: VB? = null
    protected val binding get() = _binding!!

    // 延迟初始化ViewModel
    protected val viewModel: VM by lazy {
        ViewModelProvider(requireActivity(), createViewModelFactory())[getViewModelClass()]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = createBinding(inflater, container)
        Log.d("BaseFragment","onCreateView")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null){
            (activity as AppCompatActivity).setSupportActionBar(toolbar)
            (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(showToolBar())
            (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
            toolbar.navigationIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            if (showToolBar()){
                toolbar.setNavigationOnClickListener {
                    onToolbarClickListener()?.onToolBarClick()
                }
            }
        }
        initView()
        observeViewModel()
    }
    interface ToolbarClickListener {
        fun onToolBarClick() {} // 返回按钮点击
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 创建ViewModel工厂，可在子类中重写
    protected open fun createViewModelFactory(): ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)

    // 获取ViewModel的Class对象
    @Suppress("UNCHECKED_CAST")
    private fun getViewModelClass(): Class<VM> {
        val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[1]
        return type as Class<VM>
    }

    // 由子类实现创建ViewBinding的方法
    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    // 由子类实现初始化视图的方法
    protected abstract fun initView()

    // 由子类实现观察ViewModel数据的方法
    protected abstract fun observeViewModel()
    protected open fun showToolBar(): Boolean = false // 默认显示
    // 子类可重写此方法提供自定义点击处理
    protected open fun onToolbarClickListener(): ToolbarClickListener? = null
}