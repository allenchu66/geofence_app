package com.allenchu66.geofenceapp

import androidx.room.TypeConverter

// 1. 定義 Converter
class Converters {

    // 把資料庫裡的 String 轉回 List<String>?
    @TypeConverter
    fun fromString(value: String?): List<String>? {
        // 如果是 null，就回傳 null
        if (value.isNullOrEmpty()) return null
        // 否則以逗號拆分
        return value.split(",")
    }

    // 把 List<String>? 轉成存到資料庫的 String?
    @TypeConverter
    fun listToString(list: List<String>?): String? {
        // 如果是 null，就存 null；若是空陣列也可以存 ""
        return list?.joinToString(",")
    }
}
