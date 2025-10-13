package com.welo.launcher.viewmodel

import ResourceProvider
import androidx.lifecycle.ViewModel
import com.taobao.meta.avatar.R
import com.welo.constant.Constants
import com.welo.launcher.entity.AIOptionItem

class HomeViewModel: ViewModel() {

//    private val tokenRefreshObserver = TokenRefreshObserver(WeLoApplication.getInstance())
//
//    init {
//        tokenRefreshObserver.observeTokenRefreshStatus { result ->
//            when (result) {
//                is TokenRefreshObserver.Result.Failure -> {
//                    // 处理刷新失败，可能需要跳转到登录页面
//                    if (result.errorCode == "TOKEN_EXPIRED") {
//                        // 通知UI需要重新登录
//                    }
//                }
//                else -> {} // 成功不需要特殊处理
//            }
//        }
//    }
    fun createAiToolsList(): List<AIOptionItem> {
        val options: MutableList<AIOptionItem> = mutableListOf()
        val desc = ResourceProvider.get().getStringArray(R.array.ai_tools_desc_list)
        val title = ResourceProvider.get().getStringArray(R.array.ai_tools_title_list)
        val textHint = ResourceProvider.get().getStringArray(R.array.ai_tools_hint_list)
        val resIds = listOf(
            R.drawable.icon_ai_research,
            R.drawable.icon_ai_search,
            R.drawable.icon_ai_compose,
            R.drawable.icon_ai_drawing,
            R.drawable.icon_ai_writing,
            R.drawable.icon_ai_translate,
            R.drawable.icon_ai_answer,
        )

        val minSize = minOf(title.size, desc.size)
        for (index in 0 until minSize) {
            options.add(AIOptionItem(
                resIds[index],
                title[index],
                desc[index],
                textHint[index]
            ))
        }
        Constants.aiToolList = options
        return options
    }
}