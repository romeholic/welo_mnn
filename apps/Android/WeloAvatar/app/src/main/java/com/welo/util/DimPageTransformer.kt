package com.welo.util

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class DimPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        // position 含义：
        // - 0：当前选中页（中间页）
        // - 1：右侧相邻页
        // - -1：左侧相邻页
        // 绝对值越大，离中间越远，透明度越低

        // 计算透明度：根据与中间页的距离动态调整
        val alpha = 1 - abs(position) * 0.5f  // 可自定义衰减系数（0.5f 表示最大透明度 50% 暗化）
        page.alpha = alpha.coerceAtLeast(0.2f)  // 最低透明度限制（避免全透明）
    }
}