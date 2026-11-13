package com.example.snoozer.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Pure time math for mute windows, kept free of Android types so it can be unit-tested.
 *
 * A window runs from start (inclusive) to end (exclusive). If the end time is earlier
 * than the start time the window is "overnight": it starts on the selected day and
 * ends on the following day (e.g. Sun 22:00 -> Mon 07:00).
 */
object AlarmTime {

    fun startMinuteOfDay(alarm: Alarm) = alarm.startHour * 60 + alarm.startMinute
    fun endMinuteOfDay(alarm: Alarm) = alarm.endHour * 60 + alarm.endMinute

    fun isOvernight(alarm: Alarm) = endMinuteOfDay(alarm) < startMinuteOfDay(alarm)

    /** The weekday on which the unmute for a window starting on [startDay] fires. */
    fun endDay(alarm: Alarm, startDay: DayOfWeek): DayOfWeek =
        if (isOvernight(alarm)) startDay.plus(1) else startDay

    /** The first moment strictly after [now] that falls on [day] at [hour]:[minute]. */
    fun nextTrigger(day: DayOfWeek, hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime {
        var trigger = now
            .with(TemporalAdjusters.nextOrSame(day))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!trigger.isAfter(now)) trigger = trigger.plusWeeks(1)
        return trigger
    }

    /** Whether [now] falls inside any of this alarm's weekly mute windows. */
    fun isActiveAt(alarm: Alarm, now: LocalDateTime): Boolean {
        val start = startMinuteOfDay(alarm)
        val end = endMinuteOfDay(alarm)
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek
        return when {
            start < end -> today in alarm.daysOfWeek && minute >= start && minute < end
            start > end -> (today in alarm.daysOfWeek && minute >= start) ||
                    (today.minus(1) in alarm.daysOfWeek && minute < end)
            else -> false
        }
    }
}
