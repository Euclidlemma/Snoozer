package com.example.snoozer.EditAlarm

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.CategoryExplanation
import com.example.snoozer.alarm.explain
import com.example.snoozer.alarm.category
import com.example.snoozer.util.Permissions
import java.time.DayOfWeek
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

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
    /** Optional; blank means no name. */
    val name: String = "",
    /** The first and last dates a window may start (from a timetable import); null for none. */
    val fromDate: LocalDate? = null,
    val untilDate: LocalDate? = null,
) {
    /**
     * "8:0:9:0:1,3:Work:MATH%20101:2026-09-08:2026-12-04": times, ISO day numbers, category
     * label, then the name (URL-encoded, so a ":" in it can't split the text) and the two dates
     * (empty when unset). Read back by [decode].
     */
    fun encode(): String =
        "$startHour:$startMinute:$endHour:$endMinute:${days.joinToString(",") { it.value.toString() }}:${category.label}" +
            ":${URLEncoder.encode(name, "UTF-8")}:${fromDate ?: ""}:${untilDate ?: ""}"

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
    fun withName(value: String) = copy(name = value.take(MAX_NAME))
    /** Drops the dates: the snoozer then repeats every week. */
    fun clearDates() = copy(fromDate = null, untilDate = null)
    private fun withDays(newDays: Collection<DayOfWeek>) = copy(days = newDays.distinct().sorted())

    /** A window with these values, keeping everything else (id, on/off) from [base]. */
    fun applyTo(base: Alarm) = base.copy(
        startHour = startHour, startMinute = startMinute,
        endHour = endHour, endMinute = endMinute,
        daysOfWeek = days, work = category.label,
        name = name.trim().takeIf { it.isNotEmpty() },
        fromDate = fromDate, untilDate = untilDate,
    )

    fun toNewAlarm() = applyTo(
        Alarm(startHour = 0, startMinute = 0, endHour = 0, endMinute = 0, daysOfWeek = emptyList(), work = category.label)
    )

    companion object {
        /** Long enough for "MATH 101 · Lecture · Room 2034", short enough for a card. */
        const val MAX_NAME = 40

        fun from(alarm: Alarm) = EditForm(
            alarm.startHour, alarm.startMinute, alarm.endHour, alarm.endMinute,
            alarm.daysOfWeek.distinct().sorted(), alarm.category,
            name = alarm.name.orEmpty(), fromDate = alarm.fromDate, untilDate = alarm.untilDate,
        )

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
            // 6 parts: written before names and dates existed; still read back.
            if (parts.size != 6 && parts.size != 9) return null
            val (sh, sm, eh, em) = parts.take(4).map { it.toIntOrNull() ?: return null }
            if (sh !in 0..23 || eh !in 0..23 || sm !in 0..59 || em !in 0..59) return null
            val days = if (parts[4].isEmpty()) emptyList() else parts[4].split(",").map { value ->
                value.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: return null
            }
            val category = Category.entries.firstOrNull { it.label == parts[5] } ?: return null
            if (parts.size == 6) return EditForm(sh, sm, eh, em, days.distinct().sorted(), category)
            val name = runCatching { URLDecoder.decode(parts[6], "UTF-8") }.getOrNull() ?: return null
            fun date(text: String): Result<LocalDate?> =
                if (text.isEmpty()) Result.success(null) else runCatching { LocalDate.parse(text) }
            val from = date(parts[7]).getOrElse { return null }
            val until = date(parts[8]).getOrElse { return null }
            return EditForm(sh, sm, eh, em, days.distinct().sorted(), category, name.take(MAX_NAME), from, until)
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
    /** What the chosen category does, for the "What happens" panel. Same source as onboarding. */
    val explanation: CategoryExplanation,
    /** What's typed in the Name field. */
    val name: String = "",
    /** "14 Sep – 4 Dec" when the snoozer has dates (an imported class); null otherwise. */
    val dates: String? = null,
) {
    val canSave: Boolean get() = error == null
}

object EditUi {
    /**
     * The panel's explanation, with the heads-up line telling the truth about [permissions].
     * Each category has its own reminder channel, which can be off on its own. Setup only grants
     * the app-wide permission (Android 13+); a channel turned off is fixed in Android's settings.
     */
    fun explainFor(category: Category, permissions: Permissions?): CategoryExplanation {
        if (permissions == null) return category.explain(remindersOn = true)
        return category.explain(
            remindersOn = permissions.notifications && category !in permissions.reminderChannelsOff,
            setupHasNotifications = permissions.notificationsApply && permissions.reminderChannelsOff.isEmpty(),
        )
    }

    /** "14 Sep – 4 Dec", "Until 4 Dec", "From 14 Sep", or null with neither. */
    fun datesText(from: LocalDate?, until: LocalDate?): String? = when {
        from != null && until != null -> "${AlarmTime.formatDate(from)} – ${AlarmTime.formatDate(until)}"
        until != null -> "Until ${AlarmTime.formatDate(until)}"
        from != null -> "From ${AlarmTime.formatDate(from)}"
        else -> null
    }

    fun describe(
        form: EditForm,
        original: EditForm,
        isNew: Boolean,
        /** Null assumes reminders can be shown (previews and tests that don't care). */
        permissions: Permissions? = null,
    ): EditUiState {
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
            explanation = explainFor(form.category, permissions),
            name = form.name,
            dates = datesText(form.fromDate, form.untilDate),
        )
    }
}
