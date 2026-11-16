package com.example.snoozer.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

    /** How long the window lasts, in minutes. Overnight windows wrap past midnight; equal times give 0. */
    fun durationMinutes(alarm: Alarm): Int =
        Math.floorMod(endMinuteOfDay(alarm) - startMinuteOfDay(alarm), MINUTES_PER_DAY)

    /** "09:05" style 24-hour time. */
    fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** Short human duration: "45m", "2h", "1h 30m". Negative values count as 0. */
    fun formatDuration(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val hours = m / 60
        val rest = m % 60
        return when {
            hours == 0 -> "${rest}m"
            rest == 0 -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }

    /** Whole minutes from [from] (seconds dropped) to [to], for "in 2h 15m" style labels. */
    fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Int =
        ChronoUnit.MINUTES.between(from.withSecond(0).withNano(0), to).toInt()

    /**
     * Whether the phone is quiet right now and until when, or when it goes quiet next.
     * Only enabled windows count. Overlapping and back-to-back windows are merged, matching
     * the receiver, which doesn't unmute while another window is still running.
     */
    fun quietStatus(alarms: List<Alarm>, now: LocalDateTime): QuietStatus {
        val windows = occurrences(alarms, now)
        val active = windows.filter { !it.start.isAfter(now) && it.end.isAfter(now) }
        if (active.isNotEmpty()) {
            var last = active.maxBy { it.end }
            // Keep extending while another window starts before (or exactly when) the run ends.
            while (true) {
                last = windows
                    .filter { !it.start.isAfter(last.end) && it.end.isAfter(last.end) }
                    .maxByOrNull { it.end } ?: break
            }
            return QuietStatus.QuietUntil(last.end, last.alarm)
        }
        val upcoming = windows.filter { it.start.isAfter(now) }.minByOrNull { it.start }
            ?: return QuietStatus.NothingScheduled
        return QuietStatus.NextQuiet(upcoming.start, upcoming.alarm)
    }

    private data class Window(val start: LocalDateTime, val end: LocalDateTime, val alarm: Alarm)

    /**
     * Concrete windows from yesterday (an overnight one may still be running) to a week ahead,
     * which covers the current and next quiet time for any weekly schedule. A run of merged
     * windows longer than that is cut at the end of the range.
     */
    private fun occurrences(alarms: List<Alarm>, now: LocalDateTime): List<Window> {
        val today = now.toLocalDate()
        return alarms
            .filter { it.isEnabled && durationMinutes(it) > 0 }
            .flatMap { alarm ->
                (-1L..7L).map { today.plusDays(it) }
                    .filter { it.dayOfWeek in alarm.daysOfWeek }
                    .map { date ->
                        val start = date.atTime(LocalTime.of(alarm.startHour, alarm.startMinute))
                        Window(start, start.plusMinutes(durationMinutes(alarm).toLong()), alarm)
                    }
            }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

/** What the phone is doing now, as shown in the status card. */
sealed interface QuietStatus {
    /** Muted by a window (or a run of overlapping windows) until [until]; [alarm] is the one that ends last. */
    data class QuietUntil(val until: LocalDateTime, val alarm: Alarm) : QuietStatus

    /** Not muted now; [alarm] starts the next quiet time at [at]. */
    data class NextQuiet(val at: LocalDateTime, val alarm: Alarm) : QuietStatus

    /** No enabled windows at all. */
    data object NothingScheduled : QuietStatus
}
