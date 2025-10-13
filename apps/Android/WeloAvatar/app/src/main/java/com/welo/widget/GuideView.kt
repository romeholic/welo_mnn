package com.welo.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.lottie.LottieAnimationView
import com.taobao.meta.avatar.databinding.FragmentHomeGuideBinding
import com.welo.base.gone
import com.welo.base.visible
import com.welo.launcher.adapter.ChatAdapter
import com.welo.constant.Constants.GUIDE_ANIMATOR_DURATION

class GuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private val binding = FragmentHomeGuideBinding.inflate(LayoutInflater.from(context), this, true)

    private val animatorScale = 4f

    companion object {
        private const val TAG = "GuideView"
    }

    private lateinit var guideChatAdapter: ChatAdapter
    private val animation_id_list = listOf(
        "guide_1.json",
        "guide_2.json",
        "guide_3.json",
        "guide_4.json",
        "guide_5.json"
    )
    var max_round = animation_id_list.size

    // 定义尺寸最大限制
//    private val maxSizePx = 260.dpToPx()
//
//    // 目标放大尺寸（每次+）
//    private val targetPx = 20.dpToPx()
//
//    // 初始尺寸
//    private val initialSize = 120.dpToPx()
//    // 动画duration
//    private val dur: Long = 500

    init {
        initView()
    }

    private fun initView() {
        setupRecycleView()
        setupChooseInputButton()
        setupGuideAnimate()
    }

    private fun setupRecycleView() {
        guideChatAdapter = ChatAdapter(
            onRetry = { },
            onMessageLongClick = { },
            onContentUpdated = {
            }
        )
        binding.messageList.apply {
            adapter = guideChatAdapter
            setItemViewCacheSize(10)
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams.height = 190.dpToPx()
            setOnTouchListener { _, _ -> true }
            requestLayout()
        }
    }

    private fun setupChooseInputButton() {
        binding.guideText.setOnClickListener {
            it
            binding.guideEdit.visible()
            binding.guideEdit.showTextInputMode()
            binding.guideChooseInput.gone()
        }
        binding.guideVoice.setOnClickListener {
            it
            binding.guideEdit.visible()
            binding.guideEdit.showVoiceInputMode()
            binding.guideChooseInput.gone()
        }
    }

    private fun setupGuideAnimate() {
        binding.guideAnimate.apply {
            setAnimation("guide_0.json")
            repeatCount = ValueAnimator.INFINITE
            playAnimation()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    //向下移出list

    fun translationView(target: View, endY: Int) {
        target.animate()
            .translationY(endY.toFloat())
            .setDuration(GUIDE_ANIMATOR_DURATION)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    //放大ORB
    fun stepExpandWithLottie(lottieView: LottieAnimationView) {
        val scaleAnim = ValueAnimator.ofFloat(1f, animatorScale).apply {
            duration = GUIDE_ANIMATOR_DURATION // 独立控制缩放时长
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                lottieView.scaleX = it.animatedValue as Float
                lottieView.scaleY = it.animatedValue as Float
            }
        }
        scaleAnim.start()
        translationView(lottieView,binding.root.height/2)

        // 添加播放状态监听
        lottieView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                translationView(
                    binding.mainSub,
                    binding.mainSub.y.toInt() + binding.mainSub.height
                )
            }

            override fun onAnimationEnd(animation: Animator) {
                //锁定放大后的倍数
                lottieView.scaleX = animatorScale
                lottieView.scaleY = animatorScale
                lottieView.removeAllAnimatorListeners() // 清理监听器
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        lottieView.playAnimation()
    }

    //切换动画
    fun animateViewExpansion(round: Int) {
        binding.guideAnimate.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                // 加载新动画并淡入
                binding.guideAnimate.apply {
                    animation_id_list.forEachIndexed { index, string ->
                        if (round == index && round < animation_id_list.size) {
                            setAnimation(string)
                            alpha = 0f
                            repeatCount = ValueAnimator.INFINITE
                            playAnimation()
                            animate().alpha(1f).setDuration(300).start()
                        }
                    }
                }
            }
            .start()


//        动态调整动画大小代码，暂时保留
//        val startWidth = binding.guideAnimate.width
//        val startHeight = binding.guideAnimate.height
//        // 检查是否超出限制
//        if (startWidth + targetPx >= maxSizePx) {
//            resetToInitialSize(binding.guideAnimate) // 超出则重置
//            return
//        }

//        ValueAnimator.ofInt(startWidth, startWidth + targetPx).apply {
//            duration = dur
//            interpolator = OvershootInterpolator()
//            addUpdateListener { animator ->
//                val newWidth = animator.animatedValue as Int
//                binding.guideAnimate.layoutParams = binding.guideAnimate.layoutParams.apply {
//                    width = newWidth
//                    height = (newWidth.toFloat() / startWidth * startHeight).toInt()
//                }
//                binding.guideAnimate.requestLayout()
//            }
//            start()
//            binding.guideAnimate.apply {
//                animation_id_list.forEachIndexed { index, string ->
//                    if(round == index && round < animation_id_list.size){
//                        setAnimation(string)
//                        repeatCount = ValueAnimator.INFINITE
//                        playAnimation()
//                    }
//                }
//            }
//        }
    }

//    private fun resetToInitialSize(view: View) {
//
//        ValueAnimator.ofInt(view.width, initialSize).apply {
//            duration = dur
//            interpolator = OvershootInterpolator()
//            addUpdateListener { animator ->
//                val newSize = animator.animatedValue as Int
//                view.layoutParams = view.layoutParams.apply {
//                    width = newSize
//                    height = newSize
//                }
//                view.requestLayout()
//            }
//            start()
//        }
//    }
}