package com.taobao.meta.avatar.widget

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.base.BaseActivity
import com.taobao.meta.avatar.databinding.ActivityGuideBinding

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