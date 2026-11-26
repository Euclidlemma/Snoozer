package com.example.snoozer.report

import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietStretch
import com.example.snoozer.alarm.categoryValue
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Turns the log of quiet stretches into the weekly report. Pure (no Android), so it's
 * unit-tested. A week is Monday 00:00 to the next Monday 00:00 on the phone's clock ([zone]);
 * the report shown is always the last finished week, so it "refreshes" at Monday 00:00 by
 * itself, with nothing running at midnight.
 */
object WeeklyReport {

    /** Weeks of log kept: the report's week, the one before it (for the change), and 7 earlier. */
    const val KEEP_WEEKS = 9L

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The Monday of the week the report covers at [now]: the week just finished. */
    fun reportWeek(now: LocalDateTime): LocalDate = weekStart(now.toLocalDate()).minusWeeks(1)

    /** The oldest moment the report needs, for loading and for pruning the log. */
    fun keepFrom(now: LocalDateTime, zone: ZoneId): Long =
        millis(weekStart(now.toLocalDate()).minusWeeks(KEEP_WEEKS), zone)

    // ---- Totals for one week ------------------------------------------------------------------

    /** What one week added up to. Minutes are rounded once, at the end. */
    data class Week(
        val monday: LocalDate,
        /** Quiet minutes per day (Monday first) per category. */
        val days: List<Map<Category, Int>>,
        val sessions: Map<Category, Int>,
        /** The longest single stretch in the week: its minutes, category and start day. */
        val longest: Triple<Int, Category, LocalDate>?,
        /** Stretches whose sound was turned back on, with the day they started. */
        val turnedUp: List<Pair<Category, LocalDate>>,
        /** Planned quiet without Do Not Disturb access. */
        val missedMinutes: Int,
    ) {
        fun minutes(category: Category): Int = days.sumOf { it[category] ?: 0 }
        val totalMinutes: Int get() = Category.entries.sumOf { minutes(it) }
    }

    fun week(stretches: List<QuietStretch>, monday: LocalDate, nowMillis: Long, zone: ZoneId): Week {
        val bounds = (0..7).map { millis(monday.plusDays(it.toLong()), zone) } // 8 midnights
        val weekFrom = bounds.first()
        val weekTo = bounds.last()
        val dayMs = List(7) { mutableMapOf<Category, Long>() }
        val sessions = mutableMapOf<Category, Int>()
        var longest: Triple<Long, Category, LocalDate>? = null
        val turnedUp = mutableListOf<Pair<Category, LocalDate>>()
        var missedMs = 0L
        for (s in stretches) {
            val start = s.startMillis
            val end = minOf(s.endMillis ?: nowMillis, nowMillis).coerceAtLeast(start)
            val inWeek = overlap(start, end, weekFrom, weekTo)
            if (inWeek <= 0) continue
            val category = s.categoryValue
            if (s.missed) {
                missedMs += inWeek
                continue
            }
            for (d in 0 until 7) {
                val ms = overlap(start, end, bounds[d], bounds[d + 1])
                if (ms > 0) dayMs[d].merge(category, ms, Long::plus)
            }
            sessions.merge(category, 1, Int::plus)
            val startDay = localDate(maxOf(start, weekFrom), zone)
            if (longest == null || inWeek > longest.first) longest = Triple(inWeek, category, startDay)
            if (s.louder) turnedUp += category to startDay
        }
        return Week(
            monday = monday,
            days = dayMs.map { m -> m.mapValues { (_, ms) -> minutes(ms) } },
            sessions = sessions,
            longest = longest?.let { Triple(minutes(it.first), it.second, it.third) },
            turnedUp = turnedUp,
            missedMinutes = minutes(missedMs),
        )
    }

    // ---- The report screen --------------------------------------------------------------------

    fun build(stretches: List<QuietStretch>, now: LocalDateTime, zone: ZoneId): ReportUiState {
        val nowMillis = millis(now, zone)
        val thisWeek = weekStart(now.toLocalDate())
        val next = nextReport(now)
        // Nothing logged before this week: there's no finished week to report yet.
        if (!startedBefore(stretches, thisWeek, zone)) {
            return ReportUiState.FirstWeek(AlarmTime.formatDuration(week(stretches, thisWeek, nowMillis, zone).totalMinutes), next)
        }
        val monday = thisWeek.minusWeeks(1)
        val last = week(stretches, monday, nowMillis, zone)
        // Only compare with the week before if Snoozer was already logging then.
        val before = if (startedBefore(stretches, monday, zone)) week(stretches, monday.minusWeeks(1), nowMillis, zone) else null

        val maxDay = last.days.maxOf { it.values.sum() }
        val quietestIndex = if (maxDay > 0) last.days.indexOfFirst { it.values.sum() == maxDay } else -1
        return ReportUiState.Ready(
            range = range(monday),
            totalMinutes = last.totalMinutes,
            change = before?.let { change(last.totalMinutes - it.totalMinutes) },
            work = category(Category.Work, last, before),
            study = category(Category.Study, last, before),
            days = last.days.mapIndexed { i, m ->
                val day = DayOfWeek.of(i + 1)
                DayBarUi(
                    letter = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    workMinutes = m[Category.Work] ?: 0,
                    studyMinutes = m[Category.Study] ?: 0,
                    isQuietest = i == quietestIndex,
                )
            },
            highlights = highlights(last, quietestIndex),
            notes = notes(last),
            nextReport = next,
            earlier = (2..KEEP_WEEKS - 1).map { thisWeek.minusWeeks(it) }
                .filter { startedBefore(stretches, it.plusWeeks(1), zone) }
                .map { EarlierWeekUi(shortRange(it), AlarmTime.formatDuration(week(stretches, it, nowMillis, zone).totalMinutes)) },
        )
    }

    /** The week screen's strip. [seenWeek]: the report week last opened (for the dot). */
    fun strip(stretches: List<QuietStretch>, now: LocalDateTime, zone: ZoneId, seenWeek: LocalDate?): ReportStripUi {
        val thisWeek = weekStart(now.toLocalDate())
        val nowMillis = millis(now, zone)
        if (!startedBefore(stretches, thisWeek, zone)) {
            val soFar = AlarmTime.formatDuration(week(stretches, thisWeek, nowMillis, zone).totalMinutes)
            return ReportStripUi(
                title = "Your first report arrives Monday",
                detail = "So far this week: $soFar",
                isNew = false,
                description = "Weekly report. Your first report arrives Monday. So far this week: $soFar quiet.",
            )
        }
        val last = week(stretches, thisWeek.minusWeeks(1), nowMillis, zone)
        val total = AlarmTime.formatDuration(last.totalMinutes)
        val split = Category.entries.joinToString(" · ") { "${it.label} ${AlarmTime.formatDuration(last.minutes(it))}" }
        val isNew = seenWeek != thisWeek.minusWeeks(1)
        return ReportStripUi(
            title = "Last week · $total quiet",
            detail = split,
            isNew = isNew,
            description = "Last week's report: $total quiet. ${split.replace(" · ", ", ")}." + if (isNew) " New." else "",
        )
    }

    // ---- Pieces -------------------------------------------------------------------------------

    private fun category(c: Category, last: Week, before: Week?): CategoryReportUi {
        val n = last.sessions[c] ?: 0
        return CategoryReportUi(
            category = c,
            minutes = last.minutes(c),
            sessions = if (n == 1) "1 session" else "$n sessions",
            change = before?.let {
                val diff = last.minutes(c) - it.minutes(c)
                when {
                    diff > 0 -> "▲ ${AlarmTime.formatDuration(diff)}"
                    diff < 0 -> "▼ ${AlarmTime.formatDuration(-diff)}"
                    else -> "same"
                }
            },
        )
    }

    private fun change(diff: Int): ChangeUi = when {
        diff > 0 -> ChangeUi("${AlarmTime.formatDuration(diff)} more than the week before", up = true)
        diff < 0 -> ChangeUi("${AlarmTime.formatDuration(-diff)} less than the week before", up = false)
        else -> ChangeUi("The same as the week before", up = null)
    }

    private fun highlights(w: Week, quietestIndex: Int): List<HighlightUi> {
        if (w.totalMinutes == 0) return emptyList()
        val out = mutableListOf<HighlightUi>()
        w.longest?.let { (min, c, day) ->
            out += HighlightUi(HighlightIcon.Trophy, "Longest stretch", AlarmTime.formatDuration(min), "${c.label} · ${short(day.dayOfWeek)}")
        }
        if (quietestIndex >= 0) {
            val day = DayOfWeek.of(quietestIndex + 1)
            out += HighlightUi(HighlightIcon.Calendar, "Quietest day", short(day), AlarmTime.formatDuration(w.days[quietestIndex].values.sum()))
        }
        // Days with Study (or with Work, in a week without Study).
        val c = if (w.minutes(Category.Study) > 0) Category.Study else Category.Work
        val days = w.days.indices.filter { (w.days[it][c] ?: 0) > 0 }.map { DayOfWeek.of(it + 1) }
        out += HighlightUi(HighlightIcon.Flame, "${c.label} days", "${days.size} of 7", days.joinToString(" · ") { short(it) })
        return out
    }

    private fun notes(w: Week): List<String> {
        val out = mutableListOf<String>()
        if (w.missedMinutes > 0) {
            out += "${AlarmTime.formatDuration(w.missedMinutes)} planned but not quiet: Snoozer didn't have Do Not Disturb access."
        }
        if (w.turnedUp.isNotEmpty()) {
            val n = w.turnedUp.size
            val which = w.turnedUp.take(3).joinToString(", ") { (c, d) -> "${short(d.dayOfWeek)} ${c.label}" } +
                if (n > 3) ", …" else ""
            out += "You turned the sound back on during " + (if (n == 1) "1 quiet time" else "$n quiet times") + " ($which)."
        }
        return out
    }

    /** "Next report Monday 00:00 · in 3 days" (or "· tomorrow"). */
    fun nextReport(now: LocalDateTime): String {
        val next = weekStart(now.toLocalDate()).plusWeeks(1)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), next)
        return "Next report Monday 00:00 · " + if (days == 1L) "tomorrow" else "in $days days"
    }

    /** "Mon 17 – Sun 23 Nov", or "Mon 27 Oct – Sun 2 Nov" across months. */
    fun range(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val from = if (monday.month == sunday.month) "Mon ${monday.dayOfMonth}" else "Mon ${AlarmTime.formatDate(monday)}"
        return "$from – Sun ${AlarmTime.formatDate(sunday)}"
    }

    /** "10 – 16 Nov", or "27 Oct – 2 Nov". */
    private fun shortRange(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val from = if (monday.month == sunday.month) "${monday.dayOfMonth}" else AlarmTime.formatDate(monday)
        return "$from – ${AlarmTime.formatDate(sunday)}"
    }

    private fun short(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun startedBefore(stretches: List<QuietStretch>, date: LocalDate, zone: ZoneId): Boolean {
        val m = millis(date, zone)
        return stretches.any { it.startMillis < m }
    }

    private fun overlap(start: Long, end: Long, from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

    private fun minutes(ms: Long): Int = ((ms + 30_000) / 60_000).toInt()

    fun millis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun millis(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()

    private fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
