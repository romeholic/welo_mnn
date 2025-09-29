package com.welo.guide

import android.animation.ValueAnimator
import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.taobao.meta.avatar.asr.RecognizeService
import com.taobao.meta.avatar.databinding.ActivityGuideBinding
import com.taobao.meta.avatar.tts.TtsService
import com.welo.launcher.adapter.ViewPagerAdapter
import com.welo.base.BaseActivity
import com.welo.base.gone
import com.welo.base.visible
import com.welo.guide.entity.GuidePageBean
import com.welo.guide.fragment.GuideFragment
import com.welo.service.ServiceInitializer
import com.welo.util.AudioPlayerUtil
import com.welo.guide.viewmodel.GuideViewModel
import com.welo.viewmodel.MessageViewModel
import kotlinx.coroutines.launch

class GuideActivity : BaseActivity<ActivityGuideBinding, GuideViewModel>() , ServiceInitializer {
    private var currentPage = 0
    private lateinit var messageViewModel: MessageViewModel

    override fun createBinding(): ActivityGuideBinding {
        return ActivityGuideBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun initView() {
        viewBinding.guideBg.apply {
            setAnimation("orb_anim.json")
            repeatCount = ValueAnimator.INFINITE
            playAnimation()
        }
        viewModel.initViewPager()

        messageViewModel = ViewModelProvider.NewInstanceFactory().create(MessageViewModel::class.java)

    }
    override fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.pageListData.collect { guidePageBeans ->
                initViewPager(guidePageBeans)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        caller: ComponentCaller
    ) {
        super.onActivityResult(requestCode, resultCode, data, caller)
    }
    override fun getRecognizeService() = RecognizeService(this)
    override fun getAudioPlayer(ttsService: TtsService): AudioPlayerUtil {
        return AudioPlayerUtil(ttsService)
    }

    override fun getContext(): Context = this
    private fun initViewPager(guidePages: List<GuidePageBean>) {
        // 创建适配器
        val adapter = ViewPagerAdapter(this)
        guidePages.forEachIndexed { index, guidePageBean ->
            adapter.addFragment(GuideFragment.Companion.newInstance(index, guidePageBean.title))
        }

        // 设置适配器
        viewBinding.guideViewPage.apply {
            this.adapter = adapter
            currentItem = currentPage
            isUserInputEnabled = true  // 确保滑动流畅
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    viewModel.getCurrentPageData(position)
                    if (guidePages[position].type == 1) {
                        if (viewBinding.guideBg.isVisible) {
                            viewBinding.guideBg.cancelAnimation()
                            viewBinding.guideBg.gone()
                        }
                    } else {
                        if (viewBinding.guideBg.isGone) {
                            viewBinding.guideBg.visible()
                            viewBinding.guideBg.playAnimation()
                        }
                    }
                }
            })
        }
    }
}