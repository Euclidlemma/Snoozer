package com.example.snoozer.dayPickers

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import com.example.snoozer.alarm.QuietStatus
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Everything the week screen shows, already formatted. Built by [WeekUi.build]. */
@Immutable
data class WeekUiState(
    val status: StatusUi,
    /** Monday first, matching the pager pages. */
    val days: List<DayUi>,
)

@Immutable
sealed interface StatusUi {
    /** The phone is muted now. */
    data class QuietNow(val until: String, val category: Category, val left: String) : StatusUi
    /** Not muted; the next window starts at [time] on [day] ("today", "tomorrow", "Wed"). */
    data class Next(val time: String, val day: String, val category: Category, val startsIn: String) : StatusUi
    data object NothingScheduled : StatusUi
    /** Do Not Disturb access is off, so no window can mute the phone, whatever the schedule says. */
    data object NoAccess : StatusUi
}

@Immutable
data class DayUi(
    val day: DayOfWeek,
    /** "M", "T", ... for the week strip. */
    val letter: String,
    /** "Monday". */
    val name: String,
    val isToday: Boolean,
    /** "2 snoozers · 10h 30m quiet", or null when the day is empty. */
    val summary: String?,
    val cards: List<AlarmCardUi>,
)

@Immutable
data class AlarmCardUi(
    val id: Int,
    val category: Category,
    val start: String,
    val end: String,
    val endsNextDay: Boolean,
    /** "1h 30m". */
    val duration: String,
    /** Seven flags, Monday first: which days this window repeats on. */
    val repeatsOn: List<Boolean>,
    /** "Weekdays", "Every day", "Mon, Wed" — for screen readers and as a short label. */
    val repeatText: String,
    val enabled: Boolean,
    /** Muting the phone right now (because of this day's occurrence). */
    val activeNow: Boolean,
)

/** Pure mapping from saved alarms to [WeekUiState]; no Android types, so it is unit-tested. */
object WeekUi {

    /**
     * [canMute] is whether Snoozer has Do Not Disturb access. Without it the status says so
     * and no card is marked active, since nothing is actually muting the phone.
     */
    fun build(alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean = true): WeekUiState = WeekUiState(
        status = status(alarms, now, canMute),
        days = DayOfWeek.entries.map { day(it, alarms, now, canMute) },
    )

    fun status(alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean = true): StatusUi =
        if (!canMute) StatusUi.NoAccess
        else when (val s = AlarmTime.quietStatus(alarms, now)) {
            is QuietStatus.QuietUntil -> StatusUi.QuietNow(
                until = AlarmTime.formatTime(s.until.hour, s.until.minute) +
                    if (s.until.toLocalDate() != now.toLocalDate()) " ${relativeDay(s.until, now)}" else "",
                category = s.alarm.category,
                left = "${relativeSpan(AlarmTime.minutesBetween(now, s.until))} left",
            )
            is QuietStatus.NextQuiet -> StatusUi.Next(
                time = AlarmTime.formatTime(s.at.hour, s.at.minute),
                day = relativeDay(s.at, now),
                category = s.alarm.category,
                startsIn = "in ${relativeSpan(AlarmTime.minutesBetween(now, s.at))}",
            )
            QuietStatus.NothingScheduled -> StatusUi.NothingScheduled
        }

    private fun day(day: DayOfWeek, alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean): DayUi {
        val onDay = alarms
            .filter { day in it.daysOfWeek }
            .sortedWith(compareBy({ it.startHour }, { it.startMinute }, { it.id }))
        val isToday = day == now.dayOfWeek
        val enabled = onDay.filter { it.isEnabled }
        val summary = when {
            onDay.isEmpty() -> null
            enabled.isEmpty() -> "${count(onDay.size)} · all off"
            else -> "${count(enabled.size)} · ${AlarmTime.formatDuration(quietMinutes(enabled))} quiet"
        }
        return DayUi(
            day = day,
            letter = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
            name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            isToday = isToday,
            summary = summary,
            cards = onDay.map { card(it, day, now, canMute) },
        )
    }

    private fun card(alarm: Alarm, day: DayOfWeek, now: LocalDateTime, canMute: Boolean) = AlarmCardUi(
        id = alarm.id,
        category = alarm.category,
        start = AlarmTime.formatTime(alarm.startHour, alarm.startMinute),
        end = AlarmTime.formatTime(alarm.endHour, alarm.endMinute),
        endsNextDay = AlarmTime.isOvernight(alarm),
        duration = AlarmTime.formatDuration(AlarmTime.durationMinutes(alarm)),
        repeatsOn = DayOfWeek.entries.map { it in alarm.daysOfWeek },
        repeatText = repeatText(alarm.daysOfWeek),
        enabled = alarm.isEnabled,
        activeNow = canMute && alarm.isEnabled && occurrenceActive(alarm, day, now),
    )

    /**
     * Whether the occurrence that starts on [day] covers [now]. Checking the day matters for
     * overnight windows: Sun 22:00-07:00 at Monday 06:00 is active on Sunday's page only.
     */
    private fun occurrenceActive(alarm: Alarm, day: DayOfWeek, now: LocalDateTime): Boolean {
        if (!AlarmTime.isActiveAt(alarm, now)) return false
        val minute = now.hour * 60 + now.minute
        val startedToday = minute >= AlarmTime.startMinuteOfDay(alarm)
        return if (AlarmTime.isOvernight(alarm) && !startedToday) day == now.dayOfWeek.minus(1)
               else day == now.dayOfWeek
    }

    /** Minutes the phone is quiet across [alarms] that start on one day, counting overlaps once. */
    fun quietMinutes(alarms: List<Alarm>): Int {
        val ranges = alarms
            .map { AlarmTime.startMinuteOfDay(it) to AlarmTime.startMinuteOfDay(it) + AlarmTime.durationMinutes(it) }
            .filter { it.second > it.first }
            .sortedBy { it.first }
        var total = 0
        var runStart = -1
        var runEnd = -1
        for ((start, end) in ranges) {
            if (start > runEnd) {
                total += runEnd - runStart
                runStart = start
                runEnd = end
            } else if (end > runEnd) {
                runEnd = end
            }
        }
        return total + (runEnd - runStart)
    }

    fun repeatText(days: List<DayOfWeek>): String {
        val set = days.toSet()
        val weekdays = DayOfWeek.entries.take(5).toSet()
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        return when (set) {
            DayOfWeek.entries.toSet() -> "Every day"
            weekdays -> "Weekdays"
            weekend -> "Weekends"
            else -> DayOfWeek.entries.filter { it in set }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        }
    }

    private fun count(n: Int) = if (n == 1) "1 snoozer" else "$n snoozers"

    /** "today", "tomorrow", or a short weekday name for anything later. */
    private fun relativeDay(target: LocalDateTime, now: LocalDateTime): String {
        val days = target.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> target.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        }
    }

    /** "45m", "2h 15m", or whole days once it is a day or more away ("3 days"). */
    private fun relativeSpan(minutes: Int): String = when {
        minutes < 24 * 60 -> AlarmTime.formatDuration(minutes)
        minutes < 48 * 60 -> "1 day"
        else -> "${minutes / (24 * 60)} days"
    }
}
