package com.welo.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpaceItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        // 垂直方向布局：给每个Item的顶部设置间距（第一个Item除外）
        if (parent.getChildAdapterPosition(view) != 0) {
            outRect.top = space
        }
    }
}