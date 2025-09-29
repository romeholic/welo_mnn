package com.welo.room

import androidx.room.TypeConverter

class ListStringConverter {

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return value.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    @TypeConverter
    fun toString(list: List<String>?): String? {
        return list?.joinToString(",")?.takeIf { it.isNotBlank() }
    }
}