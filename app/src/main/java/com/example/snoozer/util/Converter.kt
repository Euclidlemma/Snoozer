package com.example.snoozer.util

import androidx.room.TypeConverter
import java.time.DayOfWeek

class Converters {
    @TypeConverter
    fun fromDaysOfWeek(days: List<DayOfWeek>): String {
        return days.joinToString(",") { it.name }
    }

    @TypeConverter
    fun toDaysOfWeek(data: String): List<DayOfWeek> {
        return if (data.isEmpty()) {
            emptyList()
        } else {
            data.split(",").map { DayOfWeek.valueOf(it) }
        }
    }
}
