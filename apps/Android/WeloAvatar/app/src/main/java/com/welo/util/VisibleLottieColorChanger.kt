package com.welo.util

import android.annotation.SuppressLint
import android.graphics.Color
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.layer.Layer
import com.airbnb.lottie.value.LottieValueCallback
import kotlin.random.Random

@SuppressLint("RestrictedApi")
object VisibleLottieColorChanger {

    fun applyRandomColorsWithPerformance(lottieView: LottieAnimationView) {
        val composition = lottieView.composition ?: return
        val layers = composition.layers ?: return

        // 批量操作，减少UI刷新次数
        lottieView.post {
            // 先暂停渲染
            lottieView.pauseAnimation()

            val startTime = System.currentTimeMillis()

            layers.forEachIndexed { index, layer ->
                if (index % 10 == 0) {
                    // 每10个图层稍微延迟一下，避免UI线程阻塞
                    Thread.sleep(1)
                }
                applyQuickColorToLayer(layer, lottieView)
            }

            val duration = System.currentTimeMillis() - startTime
            LogUtil.d("Performance", "Colored ${layers.size} layers in ${duration}ms")

            // 恢复渲染
            lottieView.resumeAnimation()
            lottieView.invalidate()
        }
    }

    private fun applyQuickColorToLayer(layer: Layer,lottieView: LottieAnimationView) {
        val randomColor = Color.BLUE//generateRandomColor()
        val keyPath = KeyPath(layer.name, "**")

        // 使用更高效的方法
        try {
            lottieView.addValueCallback(
                keyPath,
                LottieProperty.COLOR,
                LottieValueCallback(randomColor)
            )
        } catch (e: Exception) {
            // 忽略某些图层可能不支持颜色修改的情况
        }
    }
    private fun generateRandomColor(): Int {
        return Color.argb(
            255, // 不透明度固定为255（完全不透明）
            Random.nextInt(256), // R
            Random.nextInt(256), // G
            Random.nextInt(256)  // B
        )
    }
}
