package com.welo.base

// 新建 BaseLazyFragment.kt

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.welo.util.LogUtil

abstract class BaseLazyFragment : Fragment() {
    private var isLoaded = false  // 数据是否已加载
    private var isViewCreated = false  // 视图是否已创建
    private var isVisibleToUser = false  // 是否对用户可见

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true
        checkLoadData()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        this.isVisibleToUser = isVisibleToUser
        checkLoadData()
    }

    private fun checkLoadData() {
        LogUtil.d("BaseLazyFragment", "checkLoadData: isViewCreated=$isViewCreated, isVisibleToUser=$isVisibleToUser, isLoaded=$isLoaded")
        // 满足视图创建完成、对用户可见、数据未加载三个条件才加载数据
        if (isViewCreated && isVisibleToUser && !isLoaded) {
            loadData()
            isLoaded = true
        }
    }

    // 子类实现此方法加载数据
    abstract fun loadData()
}