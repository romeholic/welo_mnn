package com.welo.launcher.anim

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class DepthPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        when {
            position < -1 -> // [-Infinity,-1)
                view.alpha = 0f
            position <= 0 -> { // [-1,0]
                view.alpha = 1 + position
                view.translationX = view.width * -position
                val scaleFactor = (1 + (1 - 0.75f) * -position)
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            }
            position <= 1 -> { // (0,1]
                view.alpha = 1 - position
                view.translationX = view.width * -position
                val scaleFactor = (1 - (1 - 0.75f) * position)
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            }
            else -> // (1,+Infinity]
                view.alpha = 0f
        }
    }
}