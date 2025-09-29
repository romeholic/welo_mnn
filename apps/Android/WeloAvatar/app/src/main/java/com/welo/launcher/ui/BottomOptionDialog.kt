package com.welo.launcher.ui

import ResourceProvider
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.launcher.adapter.OptionAdapter
import com.welo.launcher.entity.OptionItem
import com.welo.util.SpaceItemDecoration
import androidx.core.graphics.toColorInt

class BottomOptionDialog(
    private val context: Context,
    private val onOptionSelected: (String) -> Unit
) {
    private lateinit var popupWindow: PopupWindow
    private var overlayView: View? = null

    fun show(anchorView: View) {
        createOverlay(anchorView.rootView as ViewGroup)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_bottom_sheet, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_options)

        val options = listOf(
            OptionItem(R.drawable.ic_upload_file, "上传文件") {
                onOptionSelected("upload_file")
                dismiss()
            },
            OptionItem(R.drawable.ic_upload_image, "上传图片") {
                onOptionSelected("upload_image")
                dismiss()
            },
            OptionItem(R.drawable.ic_upload_camer, "拍照上传") {
                onOptionSelected("upload_camera")
                dismiss()
            },
            OptionItem(R.drawable.ic_audio_call, "音频通话") {
                onOptionSelected("audio_call")
                dismiss()
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addItemDecoration(
            SpaceItemDecoration(
                ResourceProvider.get().getDimenPixelSize(R.dimen.dp_22)
            )
        )
        recyclerView.adapter = OptionAdapter(options)

        popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            // 设置背景，以便点击外部可以关闭
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true

            setOnDismissListener {
                removeOverlay()
            }
        }

        // 使点击外部可关闭
//        popupWindow.isOutsideTouchable = true

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = view.measuredHeight
        val popupWidth = view.measuredWidth

        // 获取按钮在屏幕上的位置
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        val offset = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_20)
        // 计算Y坐标（按钮的顶部减去弹出窗口的高度）
        val y = location[1] - popupHeight - offset // 20像素的额外间距

        // 计算X坐标（使弹窗右边与anchorView右边对齐）
        val x = location[0] + anchorView.width - popupWidth

        // 在按钮上方显示
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)
    }
    private fun createOverlay(rootView: ViewGroup) {
        overlayView = View(context).apply {
            setBackgroundColor("#80000000".toColorInt()) // 半透明黑色
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                dismiss()
            }
        }
        rootView.addView(overlayView)
    }

    private fun removeOverlay() {
        overlayView?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            overlayView = null
        }
    }
    fun dismiss() {
        if (::popupWindow.isInitialized) {
            popupWindow.dismiss()
        }
    }
}