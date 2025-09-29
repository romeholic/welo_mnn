package com.welo.util

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    @SuppressLint("ConstantLocale")
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    @SuppressLint("ConstantLocale")
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val currentDate = Date()

        return if (isSameDay(date, currentDate)) {
            timeFormat.format(date)
        } else {
            dateFormat.format(date)
        }
    }
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance()
        val cal2 = java.util.Calendar.getInstance()
        cal1.time = date1
        cal2.time = date2
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}