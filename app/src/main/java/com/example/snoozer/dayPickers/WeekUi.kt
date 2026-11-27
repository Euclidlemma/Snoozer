package com.example.snoozer.dayPickers

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import com.example.snoozer.alarm.oneDate
import com.example.snoozer.alarm.quietLevel
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.QuietStatus
import com.example.snoozer.R
import com.example.snoozer.util.Strings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Everything the week screen shows, already formatted. Built by [WeekUi.build]. */
@Immutable
data class WeekUiState(
    val status: StatusUi,
    /** Monday first, matching the pager pages. */
    val days: List<DayUi>,
    /** "This week · Work 32h · Study 6h", or null when no switched-on window is planned. */
    val weeklyTotals: String?,
    /** How many snoozers are past their last date (imported classes after the term). */
    val endedCount: Int = 0,
)

@Immutable
sealed interface StatusUi {
    /**
     * The phone is quiet now, at [level]. [category] is a running window that sets that level
     * (with overlaps, the quietest one wins), so the card never says "Work · Silent".
     */
    data class QuietNow(
        val until: String,
        val category: Category,
        val level: QuietLevel,
        val left: String,
        /** "then vibrate until 17:00" when this level ends before the quiet time does. */
        val then: String? = null,
    ) : StatusUi
    /** Not quiet; the next window starts at [time] on [day] ("today", "tomorrow", "Wed"). */
    data class Next(val time: String, val day: String, val category: Category, val level: QuietLevel, val startsIn: String) : StatusUi
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
    /** "MATH 101" for an imported class; null for most snoozers. */
    val name: String? = null,
    /** How quiet this window makes the phone (from its category). */
    val level: QuietLevel,
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
    /** "Until 4 Dec", "Starts 14 Sep" or "Ended 4 Dec" for a snoozer with dates; null otherwise. */
    val dateNote: String? = null,
    /** Past its last date: it won't quiet the phone again. */
    val ended: Boolean = false,
)

/** Pure mapping from saved alarms to [WeekUiState]; no Android types, so it is unit-tested. */
object WeekUi {

    /**
     * [canMute] is whether Snoozer has Do Not Disturb access. Without it the status says so
     * and no card is marked active, since nothing is actually muting the phone.
     */
    fun build(strings: Strings, alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean = true): WeekUiState = WeekUiState(
        status = status(strings, alarms, now, canMute),
        days = DayOfWeek.entries.map { day(strings, it, alarms, now, canMute) },
        weeklyTotals = weeklyTotalsLine(strings, weeklyMinutes(alarms, weekOf = now.toLocalDate())),
        endedCount = alarms.count { AlarmTime.hasEnded(it, now) },
    )

    /**
     * Planned quiet minutes per category over a week, from switched-on windows only. Each
     * window counts toward the day it starts (an overnight one too), and overlaps within a
     * category on the same day count once. Categories with nothing planned are left out.
     * With [weekOf], only the windows that run in that date's week (Monday to Sunday) count, so
     * classes before or after their term are left out.
     */
    fun weeklyMinutes(alarms: List<Alarm>, weekOf: LocalDate? = null): Map<Category, Int> {
        val enabled = alarms.filter { it.isEnabled }
        val monday = weekOf?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return Category.entries.associateWith { category ->
            val ofCategory = enabled.filter { it.category == category }
            DayOfWeek.entries.sumOf { day ->
                val date = monday?.plusDays(day.value - 1L)
                quietMinutes(ofCategory.filter { if (date == null) day in it.daysOfWeek else AlarmTime.startsOn(it, date) })
            }
        }.filterValues { it > 0 }
    }

    /** "This week · Work 32h · Study 6h", in [Category] order; null when [minutes] is empty. */
    fun weeklyTotalsLine(strings: Strings, minutes: Map<Category, Int>): String? {
        if (minutes.isEmpty()) return null
        val parts = Category.entries.mapNotNull { c -> minutes[c]?.let { amount(strings, c, it) } }
        return strings.get(R.string.this_week_totals, parts.joinToString(" · "))
    }

    /** "Work 32h" / "일 32시간". */
    fun amount(strings: Strings, category: Category, minutes: Int): String =
        strings.get(R.string.category_amount, strings.get(category.labelRes), AlarmTime.formatDuration(minutes, strings))

    fun status(strings: Strings, alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean = true): StatusUi =
        if (!canMute) StatusUi.NoAccess
        else when (val s = AlarmTime.quietStatus(alarms, now)) {
            is QuietStatus.QuietUntil -> {
                // The level the phone is at now: the quietest running window's, as AlarmActions
                // applies it. Show a category that sets that level, so the two always match.
                val level = AlarmTime.quietLevelAt(alarms, now) ?: s.alarm.category.quietLevel
                val category = if (s.alarm.category.quietLevel == level) s.alarm.category
                    else alarms.first { it.isEnabled && AlarmTime.isActiveAt(it, now) && it.category.quietLevel == level }.category
                // How long *this* level lasts: the run of windows at this level, which can end
                // before the whole quiet run does (Study inside Work: silent until 12:00, then
                // vibrate until 17:00). The card shows the level's end and mentions the rest.
                val levelRun = AlarmTime.quietStatus(alarms.filter { it.category.quietLevel == level }, now)
                val levelEnd = (levelRun as? QuietStatus.QuietUntil)?.until?.takeIf { it.isBefore(s.until) }
                val end = levelEnd ?: s.until
                // A quieter window starting before that (Work 09-17 with Study 14-15, at 11:00):
                // the phone will go quieter mid-run, so say when ("silent from 14:00").
                val quieter = AlarmTime.quietStatus(alarms.filter { it.category.quietLevel > level }, now)
                val quieterStart = (quieter as? QuietStatus.NextQuiet)?.takeIf { it.at.isBefore(end) }
                StatusUi.QuietNow(
                    until = timeOn(strings, end, now),
                    category = category,
                    level = level,
                    left = strings.get(R.string.time_left, relativeSpan(strings, AlarmTime.minutesBetween(now, end))),
                    then = when {
                        quieterStart != null -> strings.get(R.string.level_from,
                            levelWord(strings, quieterStart.alarm.category.quietLevel), timeOn(strings, quieterStart.at, now))
                        levelEnd != null -> {
                            val next = AlarmTime.quietLevelAt(alarms, levelEnd)?.let { levelWord(strings, it) }
                                ?: strings.get(R.string.quiet_generic)
                            strings.get(R.string.then_level_until, next, timeOn(strings, s.until, now))
                        }
                        else -> null
                    },
                )
            }
            is QuietStatus.NextQuiet -> StatusUi.Next(
                time = AlarmTime.formatTime(s.at.hour, s.at.minute),
                day = relativeDay(strings, s.at, now),
                category = s.alarm.category,
                level = s.alarm.category.quietLevel,
                startsIn = strings.get(R.string.starts_in, relativeSpan(strings, AlarmTime.minutesBetween(now, s.at))),
            )
            QuietStatus.NothingScheduled -> StatusUi.NothingScheduled
        }

    private fun day(strings: Strings, day: DayOfWeek, alarms: List<Alarm>, now: LocalDateTime, canMute: Boolean): DayUi {
        // A one-date snoozer shows on its weekday only in its own week; the Month view has it always.
        val date = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(day.value - 1L)
        val onDay = alarms
            .filter { day in it.daysOfWeek && (it.oneDate == null || it.oneDate == date) }
            .sortedWith(compareBy({ it.startHour }, { it.startMinute }, { it.id }))
        val isToday = day == now.dayOfWeek
        // Ended classes and ones whose term hasn't started stay listed ("Ended 4 Dec", "Starts
        // 12 Jan") but don't count toward the day, matching "This week".
        val current = { a: Alarm -> !AlarmTime.hasEnded(a, now) && !AlarmTime.notStartedYet(a, now) }
        val enabled = onDay.filter { it.isEnabled && current(it) }
        val summary = when {
            onDay.isEmpty() -> null
            enabled.isEmpty() -> strings.get(R.string.day_summary, count(strings, onDay.size), strings.get(when {
                onDay.none { it.isEnabled } -> R.string.day_all_off
                onDay.filter { it.isEnabled }.all { AlarmTime.hasEnded(it, now) } ->
                    if (onDay.all { it.isEnabled }) R.string.day_all_ended else R.string.day_all_off_or_ended
                onDay.filter { it.isEnabled }.all { AlarmTime.notStartedYet(it, now) } -> R.string.day_not_started
                else -> R.string.day_none_this_week
            }))
            else -> strings.get(R.string.day_summary_quiet, count(strings, enabled.size),
                AlarmTime.formatDuration(quietMinutes(enabled), strings))
        }
        return DayUi(
            day = day,
            letter = AlarmTime.dayName(day, TextStyle.NARROW, strings),
            name = AlarmTime.dayName(day, TextStyle.FULL, strings),
            isToday = isToday,
            summary = summary,
            cards = onDay.map { card(strings, it, day, now, canMute) },
        )
    }

    fun card(strings: Strings, alarm: Alarm, day: DayOfWeek, now: LocalDateTime, canMute: Boolean) = AlarmCardUi(
        id = alarm.id,
        category = alarm.category,
        name = alarm.name?.takeIf { it.isNotBlank() },
        level = alarm.category.quietLevel,
        start = AlarmTime.formatTime(alarm.startHour, alarm.startMinute),
        end = AlarmTime.formatTime(alarm.endHour, alarm.endMinute),
        endsNextDay = AlarmTime.isOvernight(alarm),
        duration = AlarmTime.formatDuration(AlarmTime.durationMinutes(alarm), strings),
        repeatsOn = DayOfWeek.entries.map { it in alarm.daysOfWeek },
        repeatText = repeatText(strings, alarm.daysOfWeek),
        enabled = alarm.isEnabled,
        activeNow = canMute && alarm.isEnabled && occurrenceActive(alarm, day, now),
        dateNote = dateNote(strings, alarm, now),
        ended = AlarmTime.hasEnded(alarm, now),
    )

    /** "Ended 4 Dec", "Starts 14 Sep" or "Until 4 Dec"; null for a snoozer without dates. */
    fun dateNote(strings: Strings, alarm: Alarm, now: LocalDateTime): String? = when {
        AlarmTime.hasEnded(alarm, now) -> strings.get(R.string.note_ended, AlarmTime.formatDate(alarm.untilDate!!, strings))
        alarm.oneDate != null -> alarm.oneDate!!.let { d ->
            if (d == now.toLocalDate()) strings.get(R.string.note_only_today)
            else strings.get(R.string.note_only_on, AlarmTime.dayName(d.dayOfWeek, TextStyle.SHORT, strings), AlarmTime.formatDate(d, strings))
        }
        AlarmTime.notStartedYet(alarm, now) -> strings.get(R.string.note_starts, AlarmTime.formatDate(alarm.fromDate!!, strings))
        alarm.untilDate != null -> strings.get(R.string.note_until, AlarmTime.formatDate(alarm.untilDate, strings))
        else -> null
    }

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

    fun repeatText(strings: Strings, days: List<DayOfWeek>): String {
        val set = days.toSet()
        val weekdays = DayOfWeek.entries.take(5).toSet()
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        return when (set) {
            DayOfWeek.entries.toSet() -> strings.get(R.string.repeat_every_day)
            weekdays -> strings.get(R.string.repeat_weekdays)
            weekend -> strings.get(R.string.repeat_weekends)
            else -> DayOfWeek.entries.filter { it in set }
                .joinToString(", ") { AlarmTime.dayName(it, TextStyle.SHORT, strings) }
        }
    }

    /** "2 snoozers". */
    fun count(strings: Strings, n: Int) = strings.plural(R.plurals.snoozer_count, n, n)

    /** "vibrate" or "silent" inside a sentence. */
    private fun levelWord(strings: Strings, level: QuietLevel) = strings.get(level.labelRes).lowercase(strings.locale)

    /** "17:00", or "07:00 tomorrow" / "07:00 Wed" when it isn't today ("내일 07:00" in Korean). */
    fun timeOn(strings: Strings, t: LocalDateTime, now: LocalDateTime): String {
        val time = AlarmTime.formatTime(t.hour, t.minute)
        return if (t.toLocalDate() == now.toLocalDate()) time
        else strings.get(R.string.time_on_day, time, relativeDay(strings, t, now))
    }

    /** "today", "tomorrow", or a short weekday name for anything later. */
    private fun relativeDay(strings: Strings, target: LocalDateTime, now: LocalDateTime): String {
        val days = target.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> strings.get(R.string.today)
            1L -> strings.get(R.string.tomorrow)
            else -> AlarmTime.dayName(target.dayOfWeek, TextStyle.SHORT, strings)
        }
    }

    /** "45m", "2h 15m", or whole days once it is a day or more away ("3 days"). */
    private fun relativeSpan(strings: Strings, minutes: Int): String = when {
        minutes < 24 * 60 -> AlarmTime.formatDuration(minutes, strings)
        else -> (minutes / (24 * 60)).let { strings.plural(R.plurals.days_span, it, it) }
    }
}
