package com.example.snoozer.report

import com.example.snoozer.R
import com.example.snoozer.dayPickers.WeekUi
import com.example.snoozer.util.Strings
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
            if (s.louder) turnedUp += category to localDate(maxOf(start, weekFrom), zone)
        }
        // Sessions and the longest stretch go by quiet runs: stretches back to back (a handover,
        // say Work 9-10, Study 10-12, Work 12-17) are one run, and each category counts once per
        // run, from its first start to its last end there. So that day is one 8h Work session and
        // one 2h Study session, not two Work sessions.
        val quiet = stretches.filter { !it.missed }
            .map { it to clip(it.startMillis, minOf(it.endMillis ?: nowMillis, nowMillis), weekFrom, weekTo) }
            .filter { (_, span) -> span != null }
            .map { (s, span) -> s.categoryValue to span!! }
            .sortedBy { it.second.first }
        val runs = mutableListOf<MutableList<Pair<Category, Pair<Long, Long>>>>()
        for (part in quiet) {
            val run = runs.lastOrNull()
            if (run != null && part.second.first <= run.maxOf { it.second.second } + JOIN_MS) run += part else runs += mutableListOf(part)
        }
        for (run in runs) {
            for ((category, parts) in run.groupBy { it.first }) {
                sessions.merge(category, 1, Int::plus)
                val from = parts.minOf { it.second.first }
                val span = parts.maxOf { it.second.second } - from
                if (longest == null || span > longest.first) longest = Triple(span, category, localDate(from, zone))
            }
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

    fun build(strings: Strings, stretches: List<QuietStretch>, now: LocalDateTime, zone: ZoneId): ReportUiState {
        val nowMillis = millis(now, zone)
        val thisWeek = weekStart(now.toLocalDate())
        val next = nextReport(strings, now)
        // Nothing logged before this week: there's no finished week to report yet.
        if (!startedBefore(stretches, thisWeek, zone)) {
            return ReportUiState.FirstWeek(soFar(strings, week(stretches, thisWeek, nowMillis, zone).totalMinutes), next)
        }
        val monday = thisWeek.minusWeeks(1)
        val last = week(stretches, monday, nowMillis, zone)
        // Only compare with the week before if Snoozer was already logging then.
        val before = if (startedBefore(stretches, monday, zone)) week(stretches, monday.minusWeeks(1), nowMillis, zone) else null

        val maxDay = last.days.maxOf { it.values.sum() }
        val quietestIndex = if (maxDay > 0) last.days.indexOfFirst { it.values.sum() == maxDay } else -1
        return ReportUiState.Ready(
            range = range(strings, monday),
            totalMinutes = last.totalMinutes,
            change = before?.let { change(strings, last.totalMinutes - it.totalMinutes) },
            work = category(strings, Category.Work, last, before),
            study = category(strings, Category.Study, last, before),
            days = last.days.mapIndexed { i, m ->
                val day = DayOfWeek.of(i + 1)
                DayBarUi(
                    letter = AlarmTime.dayName(day, TextStyle.NARROW, strings),
                    name = AlarmTime.dayName(day, TextStyle.FULL, strings),
                    workMinutes = m[Category.Work] ?: 0,
                    studyMinutes = m[Category.Study] ?: 0,
                    isQuietest = i == quietestIndex,
                )
            },
            highlights = highlights(strings, last, quietestIndex),
            notes = notes(strings, last),
            nextReport = next,
            earlier = (2..KEEP_WEEKS - 1).map { thisWeek.minusWeeks(it) }
                .filter { startedBefore(stretches, it.plusWeeks(1), zone) }
                .map { EarlierWeekUi(shortRange(strings, it), AlarmTime.formatDuration(week(stretches, it, nowMillis, zone).totalMinutes, strings)) },
        )
    }

    /** The week screen's strip. [seenWeek]: the report week last opened (for the dot). */
    fun strip(strings: Strings, stretches: List<QuietStretch>, now: LocalDateTime, zone: ZoneId, seenWeek: LocalDate?): ReportStripUi {
        val thisWeek = weekStart(now.toLocalDate())
        val nowMillis = millis(now, zone)
        if (!startedBefore(stretches, thisWeek, zone)) {
            val soFar = soFar(strings, week(stretches, thisWeek, nowMillis, zone).totalMinutes)
            return ReportStripUi(
                title = strings.get(R.string.report_first_title),
                detail = strings.get(R.string.report_so_far, soFar),
                isNew = false,
                description = strings.get(R.string.report_first_description, soFar),
            )
        }
        val last = week(stretches, thisWeek.minusWeeks(1), nowMillis, zone)
        val total = AlarmTime.formatDuration(last.totalMinutes, strings)
        val amounts = Category.entries.map { WeekUi.amount(strings, it, last.minutes(it)) }
        val isNew = seenWeek != thisWeek.minusWeeks(1)
        return ReportStripUi(
            title = strings.get(R.string.report_strip_title, total),
            detail = amounts.joinToString(" · "),
            isNew = isNew,
            description = strings.get(R.string.report_strip_description, total, amounts.joinToString(", ")) +
                if (isNew) strings.get(R.string.report_strip_new) else "",
        )
    }

    // ---- Pieces -------------------------------------------------------------------------------

    private fun category(strings: Strings, c: Category, last: Week, before: Week?): CategoryReportUi {
        val n = last.sessions[c] ?: 0
        return CategoryReportUi(
            category = c,
            minutes = last.minutes(c),
            sessions = strings.plural(R.plurals.report_sessions, n, n),
            change = before?.let {
                val diff = last.minutes(c) - it.minutes(c)
                when {
                    diff > 0 -> strings.get(R.string.report_up, AlarmTime.formatDuration(diff, strings))
                    diff < 0 -> strings.get(R.string.report_down, AlarmTime.formatDuration(-diff, strings))
                    else -> strings.get(R.string.report_same)
                }
            },
        )
    }

    private fun change(strings: Strings, diff: Int): ChangeUi = when {
        diff > 0 -> ChangeUi(strings.get(R.string.report_more, AlarmTime.formatDuration(diff, strings)), up = true)
        diff < 0 -> ChangeUi(strings.get(R.string.report_less, AlarmTime.formatDuration(-diff, strings)), up = false)
        else -> ChangeUi(strings.get(R.string.report_same_as_before), up = null)
    }

    private fun highlights(strings: Strings, w: Week, quietestIndex: Int): List<HighlightUi> {
        if (w.totalMinutes == 0) return emptyList()
        val out = mutableListOf<HighlightUi>()
        w.longest?.let { (min, c, day) ->
            out += HighlightUi(HighlightIcon.Trophy, strings.get(R.string.report_longest), AlarmTime.formatDuration(min, strings),
                strings.get(R.string.report_detail, strings.get(c.labelRes), short(strings, day.dayOfWeek)))
        }
        if (quietestIndex >= 0) {
            val day = DayOfWeek.of(quietestIndex + 1)
            out += HighlightUi(HighlightIcon.Calendar, strings.get(R.string.report_quietest_day), short(strings, day),
                AlarmTime.formatDuration(w.days[quietestIndex].values.sum(), strings))
        }
        // Days with Study (or with Work, in a week without Study).
        val c = if (w.minutes(Category.Study) > 0) Category.Study else Category.Work
        val days = w.days.indices.filter { (w.days[it][c] ?: 0) > 0 }.map { DayOfWeek.of(it + 1) }
        out += HighlightUi(HighlightIcon.Flame, strings.get(R.string.report_category_days, strings.get(c.labelRes)),
            strings.get(R.string.report_days_of_week, days.size), days.joinToString(" · ") { short(strings, it) })
        return out
    }

    private fun notes(strings: Strings, w: Week): List<String> {
        val out = mutableListOf<String>()
        if (w.missedMinutes > 0) {
            out += strings.get(R.string.report_missed, AlarmTime.formatDuration(w.missedMinutes, strings))
        }
        if (w.turnedUp.isNotEmpty()) {
            val n = w.turnedUp.size
            val which = w.turnedUp.take(3).joinToString(", ") { (c, d) ->
                strings.get(R.string.report_turned_up_item, short(strings, d.dayOfWeek), strings.get(c.labelRes))
            } + if (n > 3) ", …" else ""
            out += strings.plural(R.plurals.report_turned_up, n, n, which)
        }
        return out
    }

    /** "3h 20m quiet", or "nothing yet" before anything is counted. */
    private fun soFar(strings: Strings, minutes: Int): String =
        if (minutes == 0) strings.get(R.string.report_nothing_yet)
        else strings.get(R.string.report_so_far_quiet, AlarmTime.formatDuration(minutes, strings))

    /** "Next report Monday 00:00 · in 3 days" (or "· tomorrow"). */
    fun nextReport(strings: Strings, now: LocalDateTime): String {
        val next = weekStart(now.toLocalDate()).plusWeeks(1)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), next).toInt()
        return if (days == 1) strings.get(R.string.report_next_tomorrow)
        else strings.plural(R.plurals.report_next_in_days, days, days)
    }

    /** "Mon 17 – Sun 23 Nov", or "Mon 27 Oct – Sun 2 Nov" across months; "11월 17일 (월) – 11월 23일 (일)". */
    fun range(strings: Strings, monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return strings.get(
            if (monday.month == sunday.month) R.string.report_range_same_month else R.string.report_range,
            short(strings, monday.dayOfWeek), monday.dayOfMonth, AlarmTime.formatDate(monday, strings),
            short(strings, sunday.dayOfWeek), AlarmTime.formatDate(sunday, strings),
        )
    }

    /** "10 – 16 Nov", or "27 Oct – 2 Nov". */
    private fun shortRange(strings: Strings, monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return strings.get(
            if (monday.month == sunday.month) R.string.report_short_range_same_month else R.string.date_range,
            *(if (monday.month == sunday.month) arrayOf<Any>(monday.dayOfMonth, AlarmTime.formatDate(monday, strings), AlarmTime.formatDate(sunday, strings))
              else arrayOf<Any>(AlarmTime.formatDate(monday, strings), AlarmTime.formatDate(sunday, strings))),
        )
    }

    private fun short(strings: Strings, day: DayOfWeek) = AlarmTime.dayName(day, TextStyle.SHORT, strings)

    private fun startedBefore(stretches: List<QuietStretch>, date: LocalDate, zone: ZoneId): Boolean {
        val m = millis(date, zone)
        return stretches.any { it.startMillis < m }
    }

    /** [start]..[end] cut to [from]..[to], or null when they don't meet. */
    private fun clip(start: Long, end: Long, from: Long, to: Long): Pair<Long, Long>? {
        val s = maxOf(start, from)
        val e = minOf(end, to)
        return if (e > s) s to e else null
    }

    /** Stretches this close count as back to back (the log writes them in the same moment). */
    private const val JOIN_MS = 60_000L

    private fun overlap(start: Long, end: Long, from: Long, to: Long): Long = (minOf(end, to) - maxOf(start, from)).coerceAtLeast(0)

    private fun minutes(ms: Long): Int = ((ms + 30_000) / 60_000).toInt()

    fun millis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun millis(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()

    private fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
