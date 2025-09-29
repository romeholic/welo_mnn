package com.welo.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.databinding.FragmentHomeGuideBinding
import com.welo.base.gone
import com.welo.base.visible
import com.welo.launcher.adapter.ChatAdapter

class GuideView  @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private val binding = FragmentHomeGuideBinding.inflate(LayoutInflater.from(context), this, true)
    companion object {
        private const val TAG = "GuideView"
    }

    private lateinit var guideChatAdapter: ChatAdapter
    init {
        initView()
        setupClickListeners()
    }
    private fun initView(){
        setupRecycleView()
        setupChooseInputButton()
        setupGuideAnimate()
    }

    private fun setupClickListeners(){
//        if (PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY))
    }
    private fun setupRecycleView() {
        guideChatAdapter = ChatAdapter(
            onRetry = {  },
            onMessageLongClick = {  },
            onContentUpdated = {
            }
        )
        binding.messageList.apply {
            adapter =guideChatAdapter
            setItemViewCacheSize(10)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            layoutParams.height=230.dpToPx()
            setOnTouchListener { _, _ -> true }
            requestLayout()
        }
    }
    private fun setupChooseInputButton(){
        binding.guideText.setOnClickListener { it
            binding.guideEdit.visible()
            binding.guideEdit.showTextInputMode()
            binding.guideChooseInput.gone()
        }
        binding.guideVoice.setOnClickListener { it
            binding.guideEdit.visible()
            binding.guideEdit.showVoiceInputMode()
            binding.guideChooseInput.gone()
        }
    }
    private fun setupGuideAnimate() {
        binding.guideAnimate.apply {
            setAnimation("orb_anim.json")
            repeatCount = ValueAnimator.INFINITE
            playAnimation()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}