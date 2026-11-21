package com.example.snoozer.EditAlarm

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import java.io.Serializable
import java.time.DayOfWeek

/** Quick picks for the repeat days. */
enum class RepeatPreset(val label: String, val days: Set<DayOfWeek>) {
    Weekdays("Weekdays", DayOfWeek.entries.take(5).toSet()),
    Weekends("Weekends", setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
    EveryDay("Every day", DayOfWeek.entries.toSet()),
}

/**
 * What the user has typed into the edit screen so far. Serializable so the ViewModel can
 * keep it in SavedStateHandle and restore it after the process is killed in the background.
 */
data class EditForm(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    /** Monday first, no duplicates. */
    val days: List<DayOfWeek>,
    val category: String,
) : Serializable {

    fun withStart(hour: Int, minute: Int) = copy(startHour = hour, startMinute = minute)
    fun withEnd(hour: Int, minute: Int) = copy(endHour = hour, endMinute = minute)
    fun toggleDay(day: DayOfWeek) = withDays(if (day in days) days - day else days + day)
    fun withPreset(preset: RepeatPreset) = withDays(preset.days)
    private fun withDays(newDays: Collection<DayOfWeek>) = copy(days = newDays.distinct().sorted())

    /** A window with these values, keeping everything else (id, on/off) from [base]. */
    fun applyTo(base: Alarm) = base.copy(
        startHour = startHour, startMinute = startMinute,
        endHour = endHour, endMinute = endMinute,
        daysOfWeek = days, work = category,
    )

    fun toNewAlarm() = applyTo(
        Alarm(startHour = 0, startMinute = 0, endHour = 0, endMinute = 0, daysOfWeek = emptyList(), work = category)
    )

    companion object {
        const val WORK = "Work"
        const val STUDY = "Study"

        fun from(alarm: Alarm) = EditForm(
            alarm.startHour, alarm.startMinute, alarm.endHour, alarm.endMinute,
            alarm.daysOfWeek.distinct().sorted(), alarm.work,
        )

        /** A new snoozer: 08:00-09:00, Work, on [day] if the user came from that day's page. */
        fun new(day: DayOfWeek?) = EditForm(8, 0, 9, 0, listOfNotNull(day), WORK)

        private const val serialVersionUID = 1L
    }
}

/** Everything the edit screen shows, derived from the form. Built by [EditUi.describe]. */
@Immutable
data class EditUiState(
    val isNew: Boolean,
    val category: String,
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
