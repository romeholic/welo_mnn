package com.welo.activity

import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ActivityGuideBinding
import com.welo.base.BaseActivity
import com.welo.viewmodel.GuideViewModel

class GuideActivity : BaseActivity<ActivityGuideBinding, GuideViewModel>() {
    private lateinit var navController: NavController

    override fun createBinding(): ActivityGuideBinding {
       return ActivityGuideBinding.inflate(layoutInflater)
    }

    override fun initView() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.guide_container) as NavHostFragment
        navController = navHostFragment.navController

    }

    override fun observeViewModel() {

    }
}