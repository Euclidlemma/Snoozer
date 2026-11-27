package com.example.snoozer.dayPickers

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import com.example.snoozer.alarm.oneDate
import com.example.snoozer.R
import com.example.snoozer.util.Strings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Which view the week screen shows: the weekly pages, or a month calendar. */
enum class WeekView(@androidx.annotation.StringRes val labelRes: Int) {
    Week(R.string.view_week),
    Month(R.string.view_month),
}

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
        strings: Strings,
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
            // A one-date snoozer has no "last day" to mark: it is that day.
            val endingNames = enabled.filter { it.untilDate == date && it.oneDate == null && !continues(it, enabled) }.map { label(strings, it) }
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
                description = describe(strings, date, work, study, endingNames),
            )
        }
        return MonthUiState(
            month = month,
            title = AlarmTime.formatMonth(month, strings),
            dayLetters = DayOfWeek.entries.map { AlarmTime.dayName(it, TextStyle.NARROW, strings) },
            weeks = cells.chunked(7),
            selected = selectedDay(strings, alarms, now, selected, canMute),
        )
    }

    /** The picked day: the windows starting then (switched-off ones too, dimmed), and first/last-class notes. */
    fun selectedDay(strings: Strings, alarms: List<Alarm>, now: LocalDateTime, date: LocalDate, canMute: Boolean = true): SelectedDayUi {
        val onDay = alarms
            .filter { AlarmTime.startsOn(it, date) }
            .sortedWith(compareBy({ it.startHour }, { it.startMinute }, { it.id }))
        val notes = alarms.filter { it.isEnabled && it.oneDate == null }.flatMap { a ->
            listOfNotNull(
                if (a.fromDate == date) strings.get(R.string.first_class, label(strings, a)) else null,
                if (a.untilDate == date && !continues(a, alarms)) strings.get(R.string.last_class, label(strings, a)) else null,
            )
        }
        return SelectedDayUi(
            date = date,
            title = AlarmTime.formatLongDate(date, strings),
            cards = onDay.map { a ->
                // The week card, with "quiet now" only for the window of this exact date.
                WeekUi.card(strings, a, date.dayOfWeek, now, canMute).copy(activeNow = canMute && a.isEnabled && runsAt(a, date, now))
            },
            notes = notes,
        )
    }

    /**
     * A class that goes on after its last date under another snoozer: the same name, picking up
     * within a few weeks (an import splits a class whose hour moves at a clock change). Its last
     * date isn't the class's last day, so it gets no end mark.
     */
    private fun continues(alarm: Alarm, all: List<Alarm>): Boolean {
        val until = alarm.untilDate ?: return false
        val name = alarm.name?.takeIf { it.isNotBlank() } ?: return false
        return all.any { o ->
            val from = o.fromDate
            o.id != alarm.id && o.isEnabled && o.name == name && from != null &&
                !from.isBefore(until) && !from.isAfter(until.plusDays(CONTINUE_DAYS))
        }
    }

    /** Same as the import's term gap: longer than a reading week. */
    private const val CONTINUE_DAYS = 21L

    /** Whether the window of [alarm] that starts on [date] covers [now]. */
    private fun runsAt(alarm: Alarm, date: LocalDate, now: LocalDateTime): Boolean {
        val start = date.atTime(LocalTime.of(alarm.startHour, alarm.startMinute))
        val end = (if (AlarmTime.isOvernight(alarm)) date.plusDays(1) else date)
            .atTime(LocalTime.of(alarm.endHour, alarm.endMinute))
        return !now.isBefore(start) && now.isBefore(end)
    }

    private fun label(strings: Strings, alarm: Alarm): String =
        alarm.name?.takeIf { it.isNotBlank() } ?: strings.get(alarm.category.labelRes)

    private fun describe(strings: Strings, date: LocalDate, work: Boolean, study: Boolean, ending: List<String>): String {
        val day = AlarmTime.formatLongDate(date, strings)
        val quiet = strings.get(when {
            work && study -> R.string.cell_work_and_study
            work -> Category.Work.labelRes
            study -> Category.Study.labelRes
            else -> R.string.cell_nothing
        })
        return if (ending.isEmpty()) strings.get(R.string.cell_description, day, quiet)
        else strings.get(R.string.cell_description_ends, day, quiet, ending.joinToString(", "))
    }
}
