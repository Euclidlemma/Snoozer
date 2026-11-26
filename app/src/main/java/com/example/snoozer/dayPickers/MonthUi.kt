package com.example.snoozer.dayPickers

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Which view the week screen shows: the weekly pages, or a month calendar. */
enum class WeekView { Week, Month }

/** The month calendar: a grid of days, and the picked day's quiet times under it. */
@Immutable
data class MonthUiState(
    val month: YearMonth,
    /** "December 2026". */
    val title: String,
    /** "M", "T", ... Monday first. */
    val dayLetters: List<String>,
    /** Whole weeks, Monday first; days from the months around it fill the first and last rows. */
    val weeks: List<List<DayCellUi>>,
    val selected: SelectedDayUi,
)

@Immutable
data class DayCellUi(
    val date: LocalDate,
    /** "18". */
    val number: String,
    /** In the month shown (not a neighbouring month's day filling the grid). */
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    /** A switched-on Work window starts that day. */
    val work: Boolean,
    /** A switched-on Study window starts that day. */
    val study: Boolean,
    /** A class's last day (its term ends). */
    val endsHere: Boolean,
    /** For screen readers: "Wednesday 18 November: Work and Study, last day of MATH 101". */
    val description: String,
)

@Immutable
data class SelectedDayUi(
    val date: LocalDate,
    /** "Wednesday 18 November". */
    val title: String,
    /** The windows that start that day, as the week screen's cards. */
    val cards: List<AlarmCardUi>,
    /** "First MATH 101 class", "Last CS 135 class". */
    val notes: List<String>,
)

/** Pure mapping from saved alarms to [MonthUiState]; unit-tested like [WeekUi]. */
object MonthUi {

    fun build(
        alarms: List<Alarm>,
        now: LocalDateTime,
        month: YearMonth,
        selected: LocalDate,
        canMute: Boolean = true,
    ): MonthUiState {
        val today = now.toLocalDate()
        val enabled = alarms.filter { it.isEnabled && AlarmTime.durationMinutes(it) > 0 }
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val cells = days.map { date ->
            val starting = enabled.filter { AlarmTime.startsOn(it, date) }
            val endingNames = enabled.filter { it.untilDate == date }.map(::label)
            val work = starting.any { it.category == Category.Work }
            val study = starting.any { it.category == Category.Study }
            DayCellUi(
                date = date,
                number = date.dayOfMonth.toString(),
                inMonth = YearMonth.from(date) == month,
                isToday = date == today,
                isSelected = date == selected,
                work = work,
                study = study,
                endsHere = endingNames.isNotEmpty(),
                description = describe(date, work, study, endingNames),
            )
        }
        return MonthUiState(
            month = month,
            title = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
            dayLetters = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) },
            weeks = cells.chunked(7),
            selected = selectedDay(alarms, now, selected, canMute),
        )
    }

    /** The picked day: the windows starting then (switched-off ones too, dimmed), and first/last-class notes. */
    fun selectedDay(alarms: List<Alarm>, now: LocalDateTime, date: LocalDate, canMute: Boolean = true): SelectedDayUi {
        val onDay = alarms
            .filter { AlarmTime.startsOn(it, date) }
            .sortedWith(compareBy({ it.startHour }, { it.startMinute }, { it.id }))
        val notes = alarms.filter { it.isEnabled }.flatMap { a ->
            listOfNotNull(
                if (a.fromDate == date) "First ${label(a)} class" else null,
                if (a.untilDate == date) "Last ${label(a)} class" else null,
            )
        }
        return SelectedDayUi(
            date = date,
            title = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.dayOfMonth} " +
                date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            cards = onDay.map { a ->
                // The week card, with "quiet now" only for the window of this exact date.
                WeekUi.card(a, date.dayOfWeek, now, canMute).copy(activeNow = canMute && a.isEnabled && runsAt(a, date, now))
            },
            notes = notes,
        )
    }

    /** Whether the window of [alarm] that starts on [date] covers [now]. */
    private fun runsAt(alarm: Alarm, date: LocalDate, now: LocalDateTime): Boolean {
        val start = date.atTime(LocalTime.of(alarm.startHour, alarm.startMinute))
        val end = (if (AlarmTime.isOvernight(alarm)) date.plusDays(1) else date)
            .atTime(LocalTime.of(alarm.endHour, alarm.endMinute))
        return !now.isBefore(start) && now.isBefore(end)
    }

    private fun label(alarm: Alarm): String = alarm.name?.takeIf { it.isNotBlank() } ?: alarm.category.label

    private fun describe(date: LocalDate, work: Boolean, study: Boolean, ending: List<String>): String {
        val day = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.dayOfMonth} " +
            date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val quiet = when {
            work && study -> "Work and Study"
            work -> "Work"
            study -> "Study"
            else -> "nothing planned"
        }
        val ends = if (ending.isEmpty()) "" else ", last day of " + ending.joinToString(", ")
        return "$day: $quiet$ends"
    }
}
