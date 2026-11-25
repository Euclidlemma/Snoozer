package com.example.snoozer.EditAlarm

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import java.time.DayOfWeek

/** Where a new snoozer of a category starts: its usual time and days. */
data class CategoryDefaults(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: List<DayOfWeek>,
)

/** Work: a working day, 09:00-17:00 on weekdays. Study: an evening session, 19:00-21:00 on weekdays. */
val Category.defaults: CategoryDefaults
    get() = when (this) {
        Category.Work -> CategoryDefaults(9, 0, 17, 0, DayOfWeek.entries.take(5))
        Category.Study -> CategoryDefaults(19, 0, 21, 0, DayOfWeek.entries.take(5))
    }

/** Quick picks for the repeat days. */
enum class RepeatPreset(val label: String, val days: Set<DayOfWeek>) {
    Weekdays("Weekdays", DayOfWeek.entries.take(5).toSet()),
    Weekends("Weekends", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    EveryDay("Every day", DayOfWeek.entries.toSet()),
}

/**
 * What the user has typed into the edit screen so far. The ViewModel keeps it in
 * SavedStateHandle as text ([encode] / [decode]) so it survives the process being killed.
 */
data class EditForm(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    /** Monday first, no duplicates. */
    val days: List<DayOfWeek>,
    val category: Category,
) {
    /** "8:0:9:0:1,3:Work": times, ISO day numbers, category label. Read back by [decode]. */
    fun encode(): String =
        "$startHour:$startMinute:$endHour:$endMinute:${days.joinToString(",") { it.value.toString() }}:${category.label}"

    /**
     * Switches category. For a new snoozer the times (and days) follow the category's starting
     * window, but only the parts the user hasn't changed yet: once they've picked their own
     * times or days, switching category keeps them. Editing a saved snoozer never moves times.
     * [fromDay] is the day page the new snoozer was opened from, if any.
     */
    fun switchCategory(to: Category, isNew: Boolean, fromDay: DayOfWeek?): EditForm {
        if (!isNew || to == category) return copy(category = to)
        val untouched = new(fromDay, category)
        val target = new(fromDay, to)
        val timesUntouched = startHour == untouched.startHour && startMinute == untouched.startMinute &&
            endHour == untouched.endHour && endMinute == untouched.endMinute
        return copy(
            category = to,
            startHour = if (timesUntouched) target.startHour else startHour,
            startMinute = if (timesUntouched) target.startMinute else startMinute,
            endHour = if (timesUntouched) target.endHour else endHour,
            endMinute = if (timesUntouched) target.endMinute else endMinute,
            days = if (days == untouched.days) target.days else days,
        )
    }

    fun withStart(hour: Int, minute: Int) = copy(startHour = hour, startMinute = minute)
    fun withEnd(hour: Int, minute: Int) = copy(endHour = hour, endMinute = minute)
    fun toggleDay(day: DayOfWeek) = withDays(if (day in days) days - day else days + day)
    fun withPreset(preset: RepeatPreset) = withDays(preset.days)
    private fun withDays(newDays: Collection<DayOfWeek>) = copy(days = newDays.distinct().sorted())

    /** A window with these values, keeping everything else (id, on/off) from [base]. */
    fun applyTo(base: Alarm) = base.copy(
        startHour = startHour, startMinute = startMinute,
        endHour = endHour, endMinute = endMinute,
        daysOfWeek = days, work = category.label,
    )

    fun toNewAlarm() = applyTo(
        Alarm(startHour = 0, startMinute = 0, endHour = 0, endMinute = 0, daysOfWeek = emptyList(), work = category.label)
    )

    companion object {
        fun from(alarm: Alarm) = EditForm(
            alarm.startHour, alarm.startMinute, alarm.endHour, alarm.endMinute,
            alarm.daysOfWeek.distinct().sorted(), alarm.category,
        )

        /** A new snoozer: 08:00-09:00, Work, on [day] if the user came from that day's page. */
        /**
         * A new snoozer with [category]'s starting window. Opened from a day's page, it starts
         * on just that day; otherwise on the category's usual days.
         */
        fun new(day: DayOfWeek?, category: Category = Category.Work): EditForm {
            val d = category.defaults
            return EditForm(d.startHour, d.startMinute, d.endHour, d.endMinute,
                if (day != null) listOf(day) else d.days, category)
        }

        /**
         * Reads text written by [encode]. Returns null for anything malformed or out of range
         * (a different format, a bad hour, an unknown day), so the caller starts fresh.
         */
        fun decode(text: String): EditForm? {
            val parts = text.split(":")
            if (parts.size != 6) return null
            val (sh, sm, eh, em) = parts.take(4).map { it.toIntOrNull() ?: return null }
            if (sh !in 0..23 || eh !in 0..23 || sm !in 0..59 || em !in 0..59) return null
            val days = if (parts[4].isEmpty()) emptyList() else parts[4].split(",").map { value ->
                value.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: return null
            }
            val category = Category.entries.firstOrNull { it.label == parts[5] } ?: return null
            return EditForm(sh, sm, eh, em, days.distinct().sorted(), category)
        }
    }
}

/** Everything the edit screen shows, derived from the form. Built by [EditUi.describe]. */
@Immutable
data class EditUiState(
    val isNew: Boolean,
    val category: Category,
    val start: String,
    val end: String,
    val days: List<DayOfWeek>,
    /** The preset matching the chosen days exactly, if any. */
    val preset: RepeatPreset?,
    /** "Quiet for 1h 30m", or "Quiet for 9h, until 07:00 the next day" for overnight windows. */
    val summary: String?,
    /** Why the form can't be saved yet, shown next to the Save button. */
    val error: String?,
    val hasChanges: Boolean,
) {
    val canSave: Boolean get() = error == null
}

object EditUi {
    fun describe(form: EditForm, original: EditForm, isNew: Boolean): EditUiState {
        val probe = form.toNewAlarm()
        val minutes = AlarmTime.durationMinutes(probe)
        val end = AlarmTime.formatTime(form.endHour, form.endMinute)
        val error = when {
            minutes == 0 -> "Start and end can't be the same time."
            form.days.isEmpty() -> "Pick at least one day."
            else -> null
        }
        val summary = when {
            minutes == 0 -> null
            AlarmTime.isOvernight(probe) -> "Quiet for ${AlarmTime.formatDuration(minutes)}, until $end the next day"
            else -> "Quiet for ${AlarmTime.formatDuration(minutes)}"
        }
        return EditUiState(
            isNew = isNew,
            category = form.category,
            start = AlarmTime.formatTime(form.startHour, form.startMinute),
            end = end,
            days = form.days,
            preset = RepeatPreset.entries.firstOrNull { it.days == form.days.toSet() },
            summary = summary,
            error = error,
            // A new snoozer always counts as changed, so backing out asks before discarding it.
            hasChanges = isNew || form != original,
        )
    }
}
