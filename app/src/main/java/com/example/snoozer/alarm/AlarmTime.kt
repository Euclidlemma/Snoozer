package com.example.snoozer.alarm

import com.example.snoozer.R
import com.example.snoozer.util.Strings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

    /** How long before a window starts its reminder fires. */
    const val REMINDER_LEAD_MINUTES = 5

    /** The earliest a reminder may come when it can only be inexact: its delivery window opens then. */
    const val REMINDER_EARLIEST_MINUTES = 10

    /**
     * When the reminder for the occurrence starting on [startDay] fires: [REMINDER_LEAD_MINUTES]
     * before the start, on the day before when the start is just after midnight (00:02 on
     * Monday reminds at 23:57 on Sunday).
     */
    fun reminderTime(alarm: Alarm, startDay: DayOfWeek): ReminderTime {
        val minute = startMinuteOfDay(alarm) - REMINDER_LEAD_MINUTES
        return if (minute >= 0) ReminderTime(startDay, minute / 60, minute % 60)
        else Math.floorMod(minute, MINUTES_PER_DAY).let { ReminderTime(startDay.minus(1), it / 60, it % 60) }
    }

    /**
     * Minutes from [now] until the next start of the occurrence that starts on [startDay]
     * (today's if it's still ahead, otherwise next week's).
     */
    fun minutesUntilStart(alarm: Alarm, startDay: DayOfWeek, now: LocalDateTime): Int {
        var start = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(startDay))
            .atTime(alarm.startHour, alarm.startMinute)
        if (!start.isAfter(now)) start = start.plusWeeks(1)
        return minutesBetween(now, start)
    }

    /** Whether [date] is inside the alarm's dates ([Alarm.fromDate] to [Alarm.untilDate], each optional). */
    fun inDates(alarm: Alarm, date: LocalDate): Boolean =
        (alarm.fromDate == null || !date.isBefore(alarm.fromDate)) &&
            (alarm.untilDate == null || !date.isAfter(alarm.untilDate))

    /** Whether a window of [alarm] starts on [date]: one of its days, and inside its dates. */
    fun startsOn(alarm: Alarm, date: LocalDate): Boolean = date.dayOfWeek in alarm.daysOfWeek && inDates(alarm, date)

    /**
     * Whether [now] falls inside one of this alarm's mute windows. An overnight window that
     * started the day before counts, including one that started on its last date.
     */
    fun isActiveAt(alarm: Alarm, now: LocalDateTime): Boolean {
        val start = startMinuteOfDay(alarm)
        val end = endMinuteOfDay(alarm)
        val minute = now.hour * 60 + now.minute
        val today = now.toLocalDate()
        return when {
            start < end -> startsOn(alarm, today) && minute >= start && minute < end
            start > end -> (startsOn(alarm, today) && minute >= start) ||
                    (startsOn(alarm, today.minusDays(1)) && minute < end)
            else -> false
        }
    }

    /** Past its last date, with nothing still running: it won't quiet the phone again. */
    fun hasEnded(alarm: Alarm, now: LocalDateTime): Boolean =
        alarm.untilDate != null && now.toLocalDate().isAfter(alarm.untilDate) && !isActiveAt(alarm, now)

    /** Its first date is still ahead. */
    fun notStartedYet(alarm: Alarm, now: LocalDateTime): Boolean =
        alarm.fromDate != null && now.toLocalDate().isBefore(alarm.fromDate)

    /**
     * Moves a weekly [trigger] into the alarm's dates. The window it belongs to starts
     * [startOffsetDays] after the trigger's date (an overnight unmute is a day after its start,
     * so -1; a reminder just before midnight is a day before, so +1). A trigger before the first
     * date moves on by whole weeks to the first window; one past the last date gives null.
     */
    fun withinDates(alarm: Alarm, trigger: ZonedDateTime, startOffsetDays: Long): ZonedDateTime? {
        var t = trigger
        val from = alarm.fromDate
        val firstStart = t.toLocalDate().plusDays(startOffsetDays)
        if (from != null && firstStart.isBefore(from)) {
            // plusWeeks keeps the wall-clock time, so a daylight-saving change in between is fine.
            t = t.plusWeeks((ChronoUnit.DAYS.between(firstStart, from) + 6) / 7)
        }
        val until = alarm.untilDate
        return if (until != null && t.toLocalDate().plusDays(startOffsetDays).isAfter(until)) null else t
    }

    /** How long the window lasts, in minutes. Overnight windows wrap past midnight; equal times give 0. */
    fun durationMinutes(alarm: Alarm): Int =
        Math.floorMod(endMinuteOfDay(alarm) - startMinuteOfDay(alarm), MINUTES_PER_DAY)


    /** [date] the way dates read on cards and in the calendar: "4 Dec" in English, "12월 4일" in Korean. */
    fun formatDate(date: LocalDate, strings: Strings): String =
        DateTimeFormatter.ofPattern(strings.get(R.string.format_date_short), strings.locale).format(date)

    /** "Wednesday 18 November" / "11월 18일 수요일", for titles and screen readers. */
    fun formatLongDate(date: LocalDate, strings: Strings): String =
        DateTimeFormatter.ofPattern(strings.get(R.string.format_date_long), strings.locale).format(date)

    /** "December 2026" / "2026년 12월". */
    fun formatMonth(month: java.time.YearMonth, strings: Strings): String =
        DateTimeFormatter.ofPattern(strings.get(R.string.format_month_year), strings.locale).format(month)

    /** [day]'s name in the person's language: "Thu", "Thursday" or "T"; "목", "목요일" or "목". */
    fun dayName(day: DayOfWeek, style: TextStyle, strings: Strings): String = day.getDisplayName(style, strings.locale)

    /** Short duration in the person's language: "45m", "2h", "1h 30m"; "45분", "2시간", "1시간 30분". */
    fun formatDuration(minutes: Int, strings: Strings): String {
        val m = minutes.coerceAtLeast(0)
        val hours = m / 60
        val rest = m % 60
        return when {
            hours == 0 -> strings.get(R.string.duration_minutes, rest)
            rest == 0 -> strings.get(R.string.duration_hours, hours)
            else -> strings.get(R.string.duration_hours_minutes, hours, rest)
        }
    }

    /** "09:05" style 24-hour time. */
    fun formatTime(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)


    /**
     * Real minutes from [from] (seconds dropped) to [to], for "in 2h 15m" style labels. Both are
     * read as wall-clock times in [zone], so a night the clocks change counts the hour that's
     * skipped or repeated (01:00 to 04:00 on spring-forward night is 2h, not 3h).
     */
    fun minutesBetween(from: LocalDateTime, to: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.MINUTES.between(from.withSecond(0).withNano(0).atZone(zone), to.atZone(zone)).toInt()

    /**
     * How quiet the phone should be at [now]: the quietest level among the enabled windows
     * running then, or null when none is. [excludingId] leaves out one window, e.g. the one
     * whose unmute is firing right now.
     */
    fun quietLevelAt(alarms: List<Alarm>, now: LocalDateTime, excludingId: Int? = null): QuietLevel? =
        QuietLevel.quietest(
            alarms
                .filter { it.id != excludingId && it.isEnabled && isActiveAt(it, now) }
                .map { it.category.quietLevel }
        )

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
     * windows longer than that is cut at the end of the range. A snoozer whose first date is
     * further ahead (a term that starts next month) gets its first week instead, so the next
     * quiet time is still found.
     */
    private fun occurrences(alarms: List<Alarm>, now: LocalDateTime): List<Window> {
        val today = now.toLocalDate()
        return alarms
            .filter { it.isEnabled && durationMinutes(it) > 0 }
            .flatMap { alarm ->
                val from = alarm.fromDate
                val base = if (from != null && from.isAfter(today)) from else today
                (-1L..7L).map { base.plusDays(it) }
                    .filter { startsOn(alarm, it) }
                    .map { date ->
                        // Both ends are wall-clock times, exactly when the scheduler fires the
                        // mute and unmute. Adding the duration to the start instead would land
                        // an hour off on a night the clocks change.
                        val start = date.atTime(LocalTime.of(alarm.startHour, alarm.startMinute))
                        val endDate = if (isOvernight(alarm)) date.plusDays(1) else date
                        val end = endDate.atTime(LocalTime.of(alarm.endHour, alarm.endMinute))
                        Window(start, end, alarm)
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

/** A reminder's weekly slot: [day] at [hour]:[minute]. */
data class ReminderTime(val day: DayOfWeek, val hour: Int, val minute: Int)

/** The reminder notification's words for [alarm]: a title and one line under it. */
data class ReminderText(val title: String, val body: String) {
    companion object {
        fun of(alarm: Alarm, strings: Strings, minutesUntilStart: Int = AlarmTime.REMINDER_LEAD_MINUTES): ReminderText {
            val start = AlarmTime.formatTime(alarm.startHour, alarm.startMinute)
            val end = AlarmTime.formatTime(alarm.endHour, alarm.endMinute)
            val overnight = AlarmTime.isOvernight(alarm)
            // A named snoozer (an imported class) says its name: "MATH 101 starts in 5 minutes".
            val name = alarm.name?.takeIf { it.isNotBlank() } ?: strings.get(alarm.category.labelRes)
            return when (alarm.category) {
                Category.Study -> ReminderText(
                    strings.plural(R.plurals.reminder_study_title, minutesUntilStart, name, minutesUntilStart),
                    if (overnight) strings.get(R.string.reminder_study_body_overnight, start, end)
                    else strings.get(R.string.reminder_study_body, start, end),
                )
                Category.Work -> ReminderText(
                    strings.get(R.string.reminder_work_title, name, start),
                    if (overnight) strings.get(R.string.reminder_work_body_overnight, end)
                    else strings.get(R.string.reminder_work_body, end),
                )
            }
        }
    }
}
