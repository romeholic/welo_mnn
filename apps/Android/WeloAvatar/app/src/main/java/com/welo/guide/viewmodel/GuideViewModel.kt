package com.welo.guide.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.welo.guide.entity.GuidePageBean
import com.welo.guide.entity.UserLabelBean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GuideViewModel: ViewModel() {
    private val chatVoice = listOf(
        UserLabelBean("极简", 0),
        UserLabelBean("亲切", 0),
        UserLabelBean("幽默", 0)
    )
    private val userHobby = listOf(
        UserLabelBean("篮球", 0),
        UserLabelBean("看电影", 0),
        UserLabelBean("美食探店", 0),
        UserLabelBean("听音乐", 0),
        UserLabelBean("足球", 0),
        UserLabelBean("骑车", 0),
        UserLabelBean("戏曲", 0),
    )
    private val guidePages = listOf(
        GuidePageBean(1, "你好我是小乐", "", 0, null, false),
        GuidePageBean(
            2,
            "接下来，让我们用简单的几步来探索最适合彼此的沟通方式吧",
            "",
            0,
            null,
            false
        ),
        GuidePageBean(3, "首先，我该怎么称呼您？", "", 0, null, true),
        GuidePageBean(4, "首先，希望我用怎样的语气跟你交流？", "", 1, chatVoice, true),
        GuidePageBean(5, "为了能够给你提供最好的体验，让我们先简单认识一下吧～", "", 0, null, true),
        GuidePageBean(6, "你喜欢的活动是？", "你也可以直接语音或打字告诉我", 2, userHobby, true),
    )

    private val _guidePages = MutableStateFlow<List<GuidePageBean>>(emptyList())
    val pageListData: StateFlow<List<GuidePageBean>> = _guidePages

    private val _currentPageData = MutableStateFlow<GuidePageBean?>(null)
    val currentPageData: StateFlow<GuidePageBean?> = _currentPageData
    fun initViewPager(){
        viewModelScope.launch {
            _guidePages.emit(guidePages)
        }
    }

    fun getCurrentPageData(position: Int){
        viewModelScope.launch {
            _currentPageData.emit(guidePages[position])
        }
    }
}