package com.welo.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val spanCount: Int,      // 列数（如 2 列）
    private val spacing: Int,        // 间距（单位：px）
    private val includeEdge: Boolean // 是否包含边缘间距
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view) // Item 位置
        val column = position % spanCount                  // 计算当前 Item 所在列（0 ~ spanCount-1）

        if (includeEdge) {
            // 左右间距：边缘间距 + 中间间距分配
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount

            // 顶部间距：仅第一行添加顶部间距
            if (position < spanCount) {
                outRect.top = spacing
            }
            outRect.bottom = spacing // 底部间距（所有行统一）
        } else {
            // 左右间距：仅中间分配，边缘无间距
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount

            // 顶部间距：仅非第一行添加顶部间距
            if (position >= spanCount) {
                outRect.top = spacing
            }
        }
    }
}