package com.example.snoozer

import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietStretch
import com.example.snoozer.report.HighlightIcon
import com.example.snoozer.report.ReportUiState
import com.example.snoozer.report.WeeklyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyReportTest {

    private val zone = ZoneId.of("America/Edmonton")
    // Wed 25 Nov 2026, 10:00. The report covers Mon 16 - Sun 22 Nov.
    private val now = LocalDateTime.of(2026, 11, 25, 10, 0)
    private fun t(d: Int, h: Int, m: Int = 0, month: Int = 11) = LocalDateTime.of(2026, month, d, h, m)
    private fun ms(t: LocalDateTime) = WeeklyReport.millis(t, zone)
    private fun s(c: Category, from: LocalDateTime, to: LocalDateTime?, louder: Boolean = false, missed: Boolean = false) =
        QuietStretch(startMillis = ms(from), endMillis = to?.let(::ms), category = c.stored, louder = louder, missed = missed)

    private fun ready(vararg stretches: QuietStretch) = WeeklyReport.build(EN, stretches.toList(), now, zone) as ReportUiState.Ready

    @Test
    fun totals_perCategoryAndDay_withTheChangeFromTheWeekBefore() {
        val r = ready(
            s(Category.Work, t(9, 9), t(9, 17)),            // the week before: Work 8h
            s(Category.Work, t(16, 9), t(16, 10)),          // Mon Work 1h
            s(Category.Study, t(16, 10), t(16, 12)),        // Mon Study 2h (inside Work)
            s(Category.Work, t(16, 12), t(16, 17)),         // Mon Work 5h
            s(Category.Study, t(19, 19), t(19, 21, 30)),    // Thu Study 2h 30m
        )
        assertEquals("Mon 16 – Sun 22 Nov", r.range)
        assertEquals(6 * 60, r.work.minutes)
        assertEquals(270, r.study.minutes)
        assertEquals(630, r.totalMinutes)
        assertEquals("2h 30m more than the week before", r.change!!.text)
        assertEquals("▼ 2h", r.work.change)
        // Work 09-17 with Study inside is one 8h Work session, not two.
        assertEquals("1 session", r.work.sessions)
        assertEquals("2 sessions", r.study.sessions)
        assertEquals("8h", r.highlights.first().value)
        assertEquals("Work · Mon", r.highlights.first().detail)
        assertEquals(listOf(360 + 120, 0, 0, 150, 0, 0, 0), r.days.map { it.totalMinutes })
        assertTrue(r.days[0].isQuietest)
        // Earlier weeks: only those Snoozer was already logging (9 - 15 Nov).
        assertEquals(listOf("9 – 15 Nov" to "8h"), r.earlier.map { it.range to it.total })
    }

    @Test
    fun stretchesAcrossMidnightAndTheWeekEdge_areCutWhereTheyBelong() {
        val r = ready(
            s(Category.Work, t(9, 9), t(9, 10)),             // history before
            s(Category.Study, t(15, 22), t(16, 2)),         // Sun 22:00 -> Mon 02:00: only 2h is in the week
            s(Category.Study, t(22, 23), t(23, 1)),         // Sun 23:00 -> Mon 01:00: 1h in, 1h next week
        )
        assertEquals(listOf(120, 0, 0, 0, 0, 0, 60), r.days.map { it.studyMinutes })
        assertEquals(180, r.study.minutes)
    }

    @Test
    fun aClockChangeDay_countsRealTime() {
        // 1 Nov 2026: Edmonton's clocks go back at 02:00, so 00:00-04:00 is 5 real hours.
        val nowNov = LocalDateTime.of(2026, 11, 4, 12, 0)
        val stretches = listOf(
            s(Category.Work, t(20, 9, month = 10), t(20, 10, month = 10)),
            s(Category.Study, t(1, 0), t(1, 4)),
        )
        val r = WeeklyReport.build(EN, stretches, nowNov, zone) as ReportUiState.Ready
        assertEquals("Mon 26 Oct – Sun 1 Nov", r.range)
        assertEquals(300, r.days[6].studyMinutes)
    }

    @Test
    fun aStretchStillGoing_countsUpToNow_andTheFirstWeekSaysSo() {
        val first = WeeklyReport.build(EN, listOf(s(Category.Work, t(25, 9), null)), now, zone)
        assertEquals(ReportUiState.FirstWeek("1h quiet", "Next report Monday 00:00 · in 5 days"), first)
        val strip = WeeklyReport.strip(EN, listOf(s(Category.Work, t(25, 9), null)), now, zone, seenWeek = null)
        assertEquals("Your first report arrives Monday", strip.title)
        assertFalse(strip.isNew)
    }

    @Test
    fun highlightsAndNotes() {
        val r = ready(
            s(Category.Work, t(10, 9), t(10, 10)),
            s(Category.Work, t(17, 9), t(17, 11, 40)),                          // Tue: longest, 2h 40m
            s(Category.Study, t(18, 19), t(18, 21), louder = true),              // Wed, turned up
            s(Category.Study, t(20, 19), t(20, 20)),
            s(Category.Work, t(21, 9), t(21, 10, 40), missed = true),            // no access
        )
        assertEquals(listOf(HighlightIcon.Trophy, HighlightIcon.Calendar, HighlightIcon.Flame), r.highlights.map { it.icon })
        assertEquals("2h 40m", r.highlights[0].value)
        assertEquals("Work · Tue", r.highlights[0].detail)
        assertEquals("Tue", r.highlights[1].value)
        assertEquals("2 of 7", r.highlights[2].value)
        assertEquals("Wed · Fri", r.highlights[2].detail)
        assertEquals(
            listOf(
                "1h 40m planned but not quiet: Snoozer didn't have Do Not Disturb access.",
                "You turned the sound back on during 1 quiet time (Wed Study).",
            ),
            r.notes,
        )
        // Missed time isn't quiet time.
        assertEquals(160, r.work.minutes)
        // Without logging the week before, nothing to compare.
        val noHistory = ready(s(Category.Work, t(17, 9), t(17, 10)))
        assertNull(noHistory.change)
        assertNull(noHistory.work.change)
    }

    @Test
    fun strip_showsLastWeek_andTheDotUntilOpened() {
        val stretches = listOf(s(Category.Work, t(17, 9), t(17, 11)), s(Category.Study, t(18, 19), t(18, 20)))
        val strip = WeeklyReport.strip(EN, stretches, now, zone, seenWeek = LocalDate.of(2026, 11, 9))
        assertEquals("Last week · 3h quiet", strip.title)
        assertEquals("Work 2h · Study 1h", strip.detail)
        assertTrue(strip.isNew)
        assertEquals("Last week's report: 3h quiet. Work 2h, Study 1h. New.", strip.description)
        assertFalse(WeeklyReport.strip(EN, stretches, now, zone, seenWeek = LocalDate.of(2026, 11, 16)).isNew)
        assertEquals(LocalDate.of(2026, 11, 16), WeeklyReport.reportWeek(now))
        assertEquals("Next report Monday 00:00 · tomorrow", WeeklyReport.nextReport(EN, LocalDateTime.of(2026, 11, 29, 23, 0)))
    }
}
