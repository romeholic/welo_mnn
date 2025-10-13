package com.welo.launcher.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.base.gone
import com.welo.base.visible

object AnimUtil {
    private const val ANIMATION_DURATION = 300L

    /**
     * 聊天界面进入动画
     */
    @SuppressLint("Recycle")
    fun toChatAnime(imageView: ImageView, recyclerView: RecyclerView,animeEnd: () -> Unit) {
        val animatorSet = AnimatorSet()

        // ImageView 动画
        imageView.visibility = View.VISIBLE
        imageView.alpha = 0.3f
        val imageViewAlphaAnim = ObjectAnimator.ofFloat(imageView, View.ALPHA, 0.3f, 1f)

        // RecyclerView 动画
        recyclerView.visibility = View.VISIBLE
        recyclerView.alpha = 0f
        recyclerView.scaleX = 0.3f
        recyclerView.scaleY = 0.3f

        val recyclerViewAlphaAnim = ObjectAnimator.ofFloat(recyclerView, View.ALPHA, 0f, 1f)
        val recyclerViewScaleXAnim = ObjectAnimator.ofFloat(recyclerView, View.SCALE_X, 0.3f, 1f)
        val recyclerViewScaleYAnim = ObjectAnimator.ofFloat(recyclerView, View.SCALE_Y, 0.3f, 1f)

        // 正确的动画顺序设置
        animatorSet.play(imageViewAlphaAnim).before(recyclerViewAlphaAnim)
        animatorSet.play(recyclerViewAlphaAnim).with(recyclerViewScaleXAnim).with(recyclerViewScaleYAnim)

        animatorSet.duration = ANIMATION_DURATION
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        // 添加动画结束监听
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                imageView.setImageResource(R.drawable.blurry_mask)
                animeEnd.invoke() // 调用回调函数
            }

            override fun onAnimationCancel(animation: Animator) {
                super.onAnimationCancel(animation)
                animeEnd.invoke() // 如果动画被取消，也调用回调函数
            }
        })
        animatorSet.start()
    }

     fun showTextInputModeAnim(textViews:List<View>,audioViews:List<View>,width: Int) {
        // 确保视图可见
        textViews.forEach { it.visible() }
        audioViews.forEach { it.visible() }

        textViews.forEach { it.translationX = -width.toFloat() }  // 文本组从左侧开始
        audioViews.forEach { it.translationX = 0f }  // 语音组从当前位置开始

        // 创建进入动画（文本组从左侧进入）
        val enterAnimators = textViews.map { view ->
            ObjectAnimator.ofFloat(view, "translationX", -width.toFloat(), 0f).apply {
                duration = ANIMATION_DURATION
            }
        }

        // 创建退出动画（语音组向右侧退出）
        val exitAnimators = audioViews.map { view ->
            ObjectAnimator.ofFloat(view, "translationX", 0f, width.toFloat()).apply {
                duration = ANIMATION_DURATION
            }
        }

        // 创建动画集合
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(enterAnimators + exitAnimators)
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                audioViews.forEach {
                    it.gone()
                    it.translationX = 0f
                }
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        animatorSet.start()
    }

    /**
     * 切换到语音输入模式
     */
    fun showVoiceInputMode(textViews:List<View>,audioViews:List<View>,width: Int) {


        // 确保视图可见
        textViews.forEach { it.visible() }
        audioViews.forEach { it.visible() }

        // 创建进入动画（语音组从右侧进入）
        val enterAnimators = audioViews.map { view ->
            ObjectAnimator.ofFloat(view, "translationX", width.toFloat(), 0f).apply {
                duration = ANIMATION_DURATION
            }
        }

        // 创建退出动画（文本组向左退出）
        val exitAnimators = textViews.map { view ->
            ObjectAnimator.ofFloat(view, "translationX", 0f, -width.toFloat()).apply {
                duration = ANIMATION_DURATION
            }
        }

        // 创建动画集合
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(enterAnimators + exitAnimators)
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                textViews.forEach {
                    it.gone()
                    it.translationX = 0f
                }
            }

            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        animatorSet.start()
    }
}