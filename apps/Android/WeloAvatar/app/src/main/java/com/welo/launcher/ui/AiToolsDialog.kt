package com.welo.launcher.ui

import AiToolsAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.taobao.meta.avatar.R
import com.welo.launcher.entity.AIOptionItem
import com.welo.util.GridSpacingItemDecoration

class AiToolsDialog(
    private val context: Context,
    private val options: List<AIOptionItem>,
    private val onOptionSelected: (optionItem: AIOptionItem, position: Int) -> Unit
) {
    var bottomSheetDialog: BottomSheetDialog =
        BottomSheetDialog(context, R.style.BottomSheetDialogTheme)

    @SuppressLint("InflateParams")
    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_tools, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.ai_tools_rv)

        bottomSheetDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        bottomSheetDialog.setContentView(view)

        extendBehindNavigationBar(bottomSheetDialog)

        recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            val space = context.resources.getDimensionPixelSize(R.dimen.dp_8)
            addItemDecoration(GridSpacingItemDecoration(2, space, true))
            adapter = AiToolsAdapter(options) { selectedOption, position ->
                onOptionSelected(selectedOption, position) // 正确的参数类型
                dismiss()
            }
        }
        setBottomPaddingForNavigation(view)


        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)

        bottomSheetDialog.setOnShowListener {
            extendBehindNavigationBar(bottomSheetDialog)
        }
        bottomSheetDialog.show()
    }

    /**
     * 允许内容延伸到底部导航栏
     */
    private fun extendBehindNavigationBar(dialog: BottomSheetDialog) {
        dialog.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT

                // 设置导航栏图标颜色（深色或浅色）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    var flags = window.decorView.systemUiVisibility
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    window.decorView.systemUiVisibility = flags
                }
            }
        }
    }

    /**
     * 为导航栏设置底部padding（可选）
     */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun setBottomPaddingForNavigation(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.viewTreeObserver.addOnGlobalLayoutListener {
                val resources = context.resources
                val resourceId = resources.getIdentifier(
                    "navigation_bar_height", "dimen", "android"
                )
                if (resourceId > 0) {
                    val navigationBarHeight = resources.getDimensionPixelSize(resourceId)
                    view.setPadding(0, 0, 0, navigationBarHeight)
                }
            }
        }
    }

    fun dismiss() {
        if (bottomSheetDialog.isShowing) {
            bottomSheetDialog.dismiss()
        }
    }
}