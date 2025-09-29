package com.welo.launcher.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter (fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {

    // 存储Fragment的列表
    private val fragments = mutableListOf<Fragment>()

    // 添加Fragment到列表
    fun addFragment(fragment: Fragment) {
        fragments.add(fragment)
    }

    // 创建指定位置的Fragment
    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }

    // 返回Fragment的数量
    override fun getItemCount(): Int {
        return fragments.size
    }
}