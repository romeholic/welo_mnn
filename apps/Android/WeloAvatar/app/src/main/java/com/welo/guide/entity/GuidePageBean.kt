package com.welo.guide.entity

data class GuidePageBean(
    val step: Int = 0,
    val title: String = "",
    val content: String = "",

    val type: Int = 0,//0 默认标签， 1 AI,2 用户
    val userLabelBean: List<UserLabelBean>? = emptyList(),
    val isLast: Boolean = false
)
data class UserLabelBean(
    val label: String = "",
    val srcId: Int = 0
)
