package com.example.snoozer.alarm

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.snoozer.util.Converters
import java.time.DayOfWeek

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
    val isEnabled: Boolean = true
)
