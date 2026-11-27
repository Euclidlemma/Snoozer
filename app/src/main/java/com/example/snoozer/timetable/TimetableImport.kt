package com.example.snoozer.timetable

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.dayPickers.WeekUi
import com.example.snoozer.R
import com.example.snoozer.util.Strings
import java.time.LocalDate
import java.time.LocalDateTime

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
            .flatMap { (_, group) -> terms(group) }
            .map { c -> Candidate(key = "${c.name}|${c.start}|${c.end}|${c.from}", cls = c) }
            .sortedWith(compareBy({ it.cls.days.min() }, { it.cls.start }, { it.cls.name }))

    /**
     * Joins the parts of one class (same name and times) whose dates touch or overlap. Parts
     * further apart than [TERM_GAP_DAYS] (the same course in fall and in winter) stay separate,
     * so the break between terms isn't muted. A reading week is a shorter gap and is joined.
     */
    private fun terms(parts: List<ParsedClass>): List<ParsedClass> {
        val out = mutableListOf<ParsedClass>()
        for (p in parts.sortedBy { it.from }) {
            val last = out.lastOrNull()
            val lastUntil = last?.until
            if (last != null && (lastUntil == null || !p.from.isAfter(lastUntil.plusDays(TERM_GAP_DAYS)))) {
                out[out.lastIndex] = last.copy(
                    days = last.days + p.days,
                    until = if (lastUntil == null || p.until == null) null else maxOf(lastUntil, p.until),
                )
            } else {
                out += p
            }
        }
        return out
    }

    /** Longer than a reading week, shorter than the break between two terms. */
    private const val TERM_GAP_DAYS = 21L

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

    fun rows(strings: Strings, candidates: List<Candidate>, checked: Set<String>, existing: List<Alarm>, today: LocalDate): List<ImportRowUi> =
        candidates.map { (key, c) ->
            ImportRowUi(
                key = key,
                name = c.name,
                time = strings.get(R.string.time_range, AlarmTime.formatTime(c.start.hour, c.start.minute), AlarmTime.formatTime(c.end.hour, c.end.minute)),
                endsNextDay = c.end < c.start,
                days = WeekUi.repeatText(strings, c.days.sorted()),
                dates = when {
                    isOver(c, today) -> strings.get(R.string.import_already_over, AlarmTime.formatDate(c.until!!, strings))
                    c.until != null -> strings.get(R.string.date_range, AlarmTime.formatDate(c.from, strings), AlarmTime.formatDate(c.until, strings))
                    else -> strings.get(R.string.import_from, AlarmTime.formatDate(c.from, strings))
                },
                checked = key in checked,
                alreadyAdded = alreadyAdded(c, existing),
            )
        }

    /**
     * Android lets an app hold 500 pending alarms, and each class day takes three (heads-up, mute,
     * unmute). Imports keep the total under this, leaving room for edits.
     */
    const val ALARM_BUDGET = 450

    /** Alarms these snoozers keep set: three per day, for every one that's on and not over. */
    fun alarmsUsed(alarms: List<Alarm>, now: LocalDateTime): Int =
        alarms.filter { it.isEnabled && !AlarmTime.hasEnded(it, now) }.sumOf { it.daysOfWeek.distinct().size * 3 }

    /** Why the ticked classes can't all be added (too many alarms), or null when they fit. */
    fun budgetNote(strings: Strings, candidates: List<Candidate>, checked: Set<String>, existing: List<Alarm>, now: LocalDateTime): String? {
        val adding = alarmsUsed(toAlarms(candidates, checked, existing, Category.Study), now)
        val used = alarmsUsed(existing, now)
        if (used + adding <= ALARM_BUDGET) return null
        val left = ((ALARM_BUDGET - used) / 3).coerceAtLeast(0)
        return strings.plural(R.plurals.import_budget, left, left)
    }

    /** "3 skipped: 2 all-day, 1 not weekly", in [SkipReason] order; null when nothing was skipped. */
    fun skippedText(strings: Strings, skipped: Map<SkipReason, Int>): String? {
        val total = skipped.values.sum()
        if (total == 0) return null
        val parts = SkipReason.entries.mapNotNull { r -> skipped[r]?.takeIf { it > 0 }?.let { strings.get(r.countRes, it) } }
        return strings.get(R.string.skipped_text, total, parts.joinToString(", "))
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
                    work = category.stored,
                    name = c.name.take(EditForm.MAX_NAME),
                    fromDate = c.from,
                    untilDate = c.until,
                )
            }
}
