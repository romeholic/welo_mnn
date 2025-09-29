package com.welo.launcher.entity

data class OptionItem(
    val iconResId: Int,
    val title: String,
    val action: () -> Unit
)
