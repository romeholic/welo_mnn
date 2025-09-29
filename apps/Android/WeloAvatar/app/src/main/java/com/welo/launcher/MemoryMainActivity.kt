package com.welo.launcher

import android.app.ComponentCaller
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.databinding.MemoryMainBinding
import com.welo.base.BaseActivity
import com.welo.launcher.adapter.ViewPagerAdapter
import com.welo.launcher.fragment.MemoryCalendarFragment
import com.welo.launcher.fragment.MemoryCircleFragment
import com.welo.launcher.fragment.MemoryMapFragment
import com.welo.launcher.viewmodel.HomeViewModel

class MemoryMainActivity : BaseActivity<MemoryMainBinding, HomeViewModel>() {

    private var currentPage = 0

    override fun createBinding(): MemoryMainBinding {
        ApplicationProvider.set(application = application)
        return MemoryMainBinding.inflate(layoutInflater)
    }

    override fun initView() {
        initViewPager()
        viewBinding.shexian.isSelected = true
        viewBinding.shexian.setOnClickListener { view ->
            currentPage = 0
            view.isSelected = true
            viewBinding.circle.isSelected = false
            viewBinding.calendar.isSelected = false
            updateBottomSelectView()
        }
        viewBinding.circle.setOnClickListener { view ->
            currentPage = 1
            view.isSelected = true
            viewBinding.shexian.isSelected = false
            viewBinding.calendar.isSelected = false
            updateBottomSelectView()
        }
        viewBinding.calendar.setOnClickListener { view ->
            currentPage = 2
            view.isSelected = true
            viewBinding.circle.isSelected = false
            viewBinding.shexian.isSelected = false
            updateBottomSelectView()
        }
    }

    private fun updateBottomSelectView() {
        viewBinding.memoryViewPager.currentItem = currentPage
        viewBinding.memoryViewPager.setCurrentItem(currentPage, false)
    }

    private fun initViewPager() {
        // 创建适配器
        val adapter = ViewPagerAdapter(this)

        // 添加三个Fragment
        adapter.addFragment(MemoryMapFragment.Companion.newInstance("", ""))
        adapter.addFragment(MemoryCircleFragment.Companion.newInstance("", ""))
        adapter.addFragment(MemoryCalendarFragment.Companion.newInstance("", ""))


        // 设置适配器
        viewBinding.memoryViewPager.adapter = adapter
        viewBinding.memoryViewPager.setCurrentItem(currentPage, false) // 第二个参数设为false
        viewBinding.memoryViewPager.isUserInputEnabled = true  // 确保滑动流畅

        // 监听页面变化
        viewBinding.memoryViewPager.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 更新指示器状态
                when (position) {
                    0 -> {
                        viewBinding.shexian.isSelected = true
                        viewBinding.circle.isSelected = false
                        viewBinding.calendar.isSelected = false
                    }

                    1 -> {
                        viewBinding.shexian.isSelected = false
                        viewBinding.circle.isSelected = true
                        viewBinding.calendar.isSelected = false
                    }

                    2 -> {
                        viewBinding.shexian.isSelected = false
                        viewBinding.circle.isSelected = false
                        viewBinding.calendar.isSelected = true
                    }
                }

            }
        })
    }


    override fun observeViewModel() {
    }

    override fun adaptStatusBarTextColor() {
        // 强制使用浅色文字（适合深色背景）
        setStatusBarTextColor(false)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {

    }

    override fun onDestroy() {

        super.onDestroy()
    }

    companion object {
        private const val TAG = "MemoryMainActivity"

    }
}