package com.example.snoozer.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.snoozer.util.Converters
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "alarms")
@TypeConverters(Converters::class)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: List<DayOfWeek>,
    val work: String,
    val isEnabled: Boolean = true,
    /** Optional, e.g. a class from an imported timetable ("MATH 101"). */
    val name: String? = null,
    /** The first day a window may start, or null for "already". Set by a timetable import. */
    val fromDate: LocalDate? = null,
    /**
     * The last day a window may start, or null to repeat every week. A window starting that day
     * still runs to its end, even past midnight. Set by a timetable import (the term's end).
     */
    val untilDate: LocalDate? = null,
)
