package com.welo.room

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room 数据库中的类型转换器，用于解决 Room 无法直接处理 java.util.Date 类型数据的问题
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}