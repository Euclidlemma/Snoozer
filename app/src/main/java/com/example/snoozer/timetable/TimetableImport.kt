package com.example.snoozer.timetable

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.dayPickers.WeekUi
import java.time.LocalDate

/**
 * Pure steps between a parsed file and saved snoozers, kept apart from the ViewModel so they
 * are unit-tested: merge the file's events into one class per name and times, mark the ones
 * already saved, build the preview rows, and turn the ticked ones into [Alarm]s.
 */
object TimetableImport {

    /** One class to offer: a [ParsedClass] merged with the file's other events for it. */
    data class Candidate(val key: String, val cls: ParsedClass)

    /**
     * One class per name, start and end: a class listed as separate events per weekday (or per
     * week) becomes one snoozer on all its days, from its first date to its last. Ordered by its
     * first weekday, then start time, then name.
     */
    fun merge(classes: List<ParsedClass>): List<Candidate> =
        classes.groupBy { Triple(it.name, it.start, it.end) }
            .map { (k, group) ->
                val until = if (group.any { it.until == null }) null else group.maxOf { it.until!! }
                val merged = ParsedClass(
                    name = k.first, start = k.second, end = k.third,
                    days = group.flatMap { it.days }.toSet(),
                    from = group.minOf { it.from },
                    until = until,
                )
                Candidate(key = "${merged.name}|${merged.start}|${merged.end}", cls = merged)
            }
            .sortedWith(compareBy({ it.cls.days.min() }, { it.cls.start }, { it.cls.name }))

    /** Already saved: a snoozer with the same name, times and days. */
    fun alreadyAdded(c: ParsedClass, existing: List<Alarm>): Boolean = existing.any { a ->
        // Names are saved cut to the edit screen's limit, so compare the cut name.
        a.name == c.name.take(EditForm.MAX_NAME) && a.startHour == c.start.hour && a.startMinute == c.start.minute &&
            a.endHour == c.end.hour && a.endMinute == c.end.minute && a.daysOfWeek.toSet() == c.days
    }

    /** Over before today: offered unticked, and said so. */
    fun isOver(c: ParsedClass, today: LocalDate): Boolean = c.until != null && c.until.isBefore(today)

    /** The keys ticked when the preview first opens: everything not already saved and not over. */
    fun defaultChecked(candidates: List<Candidate>, existing: List<Alarm>, today: LocalDate): Set<String> =
        candidates.filter { !alreadyAdded(it.cls, existing) && !isOver(it.cls, today) }.map { it.key }.toSet()

    fun rows(candidates: List<Candidate>, checked: Set<String>, existing: List<Alarm>, today: LocalDate): List<ImportRowUi> =
        candidates.map { (key, c) ->
            ImportRowUi(
                key = key,
                name = c.name,
                time = "${AlarmTime.formatTime(c.start.hour, c.start.minute)} – ${AlarmTime.formatTime(c.end.hour, c.end.minute)}",
                endsNextDay = c.end < c.start,
                days = WeekUi.repeatText(c.days.sorted()),
                dates = when {
                    isOver(c, today) -> "Already over (ended ${AlarmTime.formatDate(c.until!!)})"
                    c.until != null -> "${AlarmTime.formatDate(c.from)} – ${AlarmTime.formatDate(c.until)}"
                    else -> "from ${AlarmTime.formatDate(c.from)}"
                },
                checked = key in checked,
                alreadyAdded = alreadyAdded(c, existing),
            )
        }

    /** "3 skipped: 2 all-day, 1 not weekly", in [SkipReason] order; null when nothing was skipped. */
    fun skippedText(skipped: Map<SkipReason, Int>): String? {
        val total = skipped.values.sum()
        if (total == 0) return null
        val parts = SkipReason.entries.mapNotNull { r -> skipped[r]?.takeIf { it > 0 }?.let { "$it ${r.label}" } }
        return "$total skipped: " + parts.joinToString(", ")
    }

    /** The snoozers to save: the ticked candidates not already saved, all as [category]. */
    fun toAlarms(candidates: List<Candidate>, checked: Set<String>, existing: List<Alarm>, category: Category): List<Alarm> =
        candidates
            .filter { it.key in checked && !alreadyAdded(it.cls, existing) }
            .map { (_, c) ->
                Alarm(
                    startHour = c.start.hour, startMinute = c.start.minute,
                    endHour = c.end.hour, endMinute = c.end.minute,
                    daysOfWeek = c.days.sorted(),
                    work = category.label,
                    name = c.name.take(EditForm.MAX_NAME),
                    fromDate = c.from,
                    untilDate = c.until,
                )
            }
}
