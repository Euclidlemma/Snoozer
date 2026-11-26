package com.example.snoozer.util

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate

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

    /** Dates are stored as ISO text ("2026-12-04"), which sorts and reads back exactly. */
    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toDate(text: String?): LocalDate? = text?.let(LocalDate::parse)
}
