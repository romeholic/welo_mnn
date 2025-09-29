package com.welo.launcher.anim

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class ZoomOutPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        when {
            position < -1 -> // [-Infinity,-1)
                view.alpha = 0f
            position <= 1 -> { // [-1,1]
                val scaleFactor = 0.85f.coerceAtLeast(1 - abs(position))
                val vertMargin = view.height * (1 - scaleFactor) / 2
                val horzMargin = view.width * (1 - scaleFactor) / 2

                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                view.translationX = if (position < 0) {
                    horzMargin - vertMargin / 2
                } else {
                    horzMargin + vertMargin / 2
                }

                // 淡出页面
                view.alpha = 0.5f + (scaleFactor - 0.85f) / (1 - 0.85f) * (1 - 0.5f)
            }
            else -> // (1,+Infinity]
                view.alpha = 0f
        }
    }
}