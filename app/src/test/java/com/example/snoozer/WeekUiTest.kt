package com.example.snoozer

import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.dayPickers.StatusUi
import com.example.snoozer.dayPickers.WeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime

class WeekUiTest {

    private fun alarm(id: Int, sh: Int, sm: Int, eh: Int, em: Int, vararg days: DayOfWeek,
                      work: String = "Work", enabled: Boolean = true) =
        Alarm(id = id, startHour = sh, startMinute = sm, endHour = eh, endMinute = em,
            daysOfWeek = days.toList(), work = work, isEnabled = enabled)

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun days_mondayFirst_withTodayMarkedAndSortedCards() {
        val late = alarm(1, 22, 0, 23, 0, MONDAY)
        val early = alarm(2, 9, 0, 10, 30, MONDAY, WEDNESDAY)
        val ui = WeekUi.build(EN, listOf(late, early), at(18, 8, 0))   // Wednesday

        assertEquals(7, ui.days.size)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), ui.days.map { it.letter })
        assertEquals(WEDNESDAY, ui.days.single { it.isToday }.day)
        assertEquals(listOf(2, 1), ui.days[0].cards.map { it.id })
        assertEquals(listOf(2), ui.days[2].cards.map { it.id })
        assertNull(ui.days[1].summary)
    }

    @Test
    fun card_formatsTimesDurationAndRepeat() {
        val a = alarm(5, 22, 0, 7, 0, MONDAY, FRIDAY, work = "Study")
        val card = WeekUi.build(EN, listOf(a), at(16, 12, 0)).days[0].cards.single()
        assertEquals("22:00", card.start)
        assertEquals("07:00", card.end)
        assertTrue(card.endsNextDay)
        assertEquals("9h", card.duration)
        assertEquals("Mon, Fri", card.repeatText)
        assertEquals(listOf(true, false, false, false, true, false, false), card.repeatsOn)
        assertEquals(Category.Study, card.category)
    }

    @Test
    fun summary_countsOverlapOnce_andSkipsSwitchedOff() {
        val a = alarm(1, 9, 0, 11, 0, MONDAY)
        val b = alarm(2, 10, 0, 12, 0, MONDAY)
        val off = alarm(3, 20, 0, 21, 0, MONDAY, enabled = false)
        assertEquals("2 snoozers · 3h quiet", WeekUi.build(EN, listOf(a, b, off), at(16, 8, 0)).days[0].summary)
        assertEquals("1 snoozer · all off", WeekUi.build(EN, listOf(off), at(16, 8, 0)).days[0].summary)
    }

    @Test
    fun activeNow_overnightBelongsToTheStartDayPage() {
        val a = alarm(1, 22, 0, 7, 0, SUNDAY, MONDAY)
        val ui = WeekUi.build(EN, listOf(a), at(16, 6, 0))   // Monday 06:00, inside Sunday's window
        assertTrue(ui.days[6].cards.single().activeNow)   // Sunday page
        assertFalse(ui.days[0].cards.single().activeNow)  // Monday's own window starts at 22:00
    }

    @Test
    fun status_quietNow() {
        val a = alarm(1, 9, 0, 10, 30, MONDAY, work = "Study")
        assertEquals(StatusUi.QuietNow(until = "10:30", category = Category.Study, level = QuietLevel.Silent, left = "1h 10m left"),
            WeekUi.status(EN, listOf(a), at(16, 9, 20)))
    }

    @Test
    fun status_next_todayTomorrowAndLater() {
        val mon = alarm(1, 22, 0, 23, 0, MONDAY)
        assertEquals(StatusUi.Next("22:00", "today", Category.Work, QuietLevel.Vibrate, "in 2h 15m"),
            WeekUi.status(EN, listOf(mon), at(16, 19, 45)))
        assertEquals(StatusUi.Next("22:00", "tomorrow", Category.Work, QuietLevel.Vibrate, "in 1 day"),
            WeekUi.status(EN, listOf(mon), at(15, 21, 0)))   // Sunday evening
        val sat = alarm(2, 9, 0, 10, 0, SATURDAY)
        assertEquals(StatusUi.Next("09:00", "Sat", Category.Work, QuietLevel.Vibrate, "in 5 days"),
            WeekUi.status(EN, listOf(sat), at(16, 8, 0)))
    }

    @Test
    fun withoutDndAccess_statusSaysPausedAndNoCardIsActive() {
        val a = alarm(1, 9, 0, 10, 30, MONDAY)
        val ui = WeekUi.build(EN, listOf(a), at(16, 9, 20), canMute = false)   // inside the window
        assertEquals(StatusUi.NoAccess, ui.status)
        assertFalse(ui.days[0].cards.single().activeNow)
    }

    @Test
    fun status_overlap_showsTheQuietestWindowAndItsCategory() {
        // Work 09:00-17:00 contains Study 10:00-12:00. Work ends last, but at 11:00 Study's
        // silence is what the phone is at, so the card says Study, Silent.
        val work = alarm(1, 9, 0, 17, 0, MONDAY)
        val study = alarm(2, 10, 0, 12, 0, MONDAY, work = "Study")
        val during = WeekUi.status(EN, listOf(work, study), at(16, 11, 0)) as StatusUi.QuietNow
        assertEquals(Category.Study, during.category)
        assertEquals(QuietLevel.Silent, during.level)
        // Silent only until Study ends; the rest of the quiet time is mentioned separately.
        assertEquals("12:00", during.until)
        assertEquals("1h left", during.left)
        assertEquals("then vibrate until 17:00", during.then)
        // After Study ends, it's Work on vibrate until the end, with nothing after it.
        val after = WeekUi.status(EN, listOf(work, study), at(16, 13, 0)) as StatusUi.QuietNow
        assertEquals(Category.Work, after.category)
        assertEquals(QuietLevel.Vibrate, after.level)
        assertEquals("17:00", after.until)
        assertNull(after.then)
    }

    @Test
    fun status_quieterWindowLaterInTheRun_isMentioned() {
        val work = alarm(1, 9, 0, 17, 0, MONDAY)
        val study = alarm(2, 14, 0, 15, 0, MONDAY, work = "Study")
        val s = WeekUi.status(EN, listOf(work, study), at(16, 11, 0)) as StatusUi.QuietNow
        assertEquals(QuietLevel.Vibrate, s.level)
        assertEquals("17:00", s.until)
        assertEquals("silent from 14:00", s.then)
    }

    @Test
    fun card_carriesItsCategorysLevel() {
        val study = alarm(1, 10, 0, 12, 0, MONDAY, work = "Study")
        assertEquals(QuietLevel.Silent, WeekUi.build(EN, listOf(study), at(16, 8, 0)).days[0].cards.single().level)
    }

    @Test
    fun weeklyTotals_perCategory() {
        val work = alarm(1, 9, 0, 17, 0, *DayOfWeek.entries.take(5).toTypedArray())   // 5 x 8h
        val study = alarm(2, 19, 0, 21, 0, MONDAY, WEDNESDAY, work = "Study")           // 2 x 2h
        assertEquals(mapOf(Category.Work to 40 * 60, Category.Study to 4 * 60), WeekUi.weeklyMinutes(listOf(work, study)))
        assertEquals("This week · Work 40h · Study 4h", WeekUi.build(EN, listOf(work, study), at(16, 8, 0)).weeklyTotals)
    }

    @Test
    fun weeklyTotals_overlapsWithinACategoryCountOnce_acrossCategoriesSeparately() {
        val a = alarm(1, 9, 0, 11, 0, MONDAY)
        val b = alarm(2, 10, 0, 12, 0, MONDAY)                   // overlaps a: Work = 3h, not 4h
        val study = alarm(3, 10, 0, 11, 0, MONDAY, work = "Study") // inside Work, counted for Study
        assertEquals(mapOf(Category.Work to 180, Category.Study to 60), WeekUi.weeklyMinutes(listOf(a, b, study)))
    }

    @Test
    fun weeklyTotals_skipOffWindows_countOvernight_andHideWhenEmpty() {
        val off = alarm(1, 9, 0, 17, 0, MONDAY, enabled = false)
        val night = alarm(2, 22, 0, 7, 0, SUNDAY, work = "Study")   // 9h, counted on Sunday
        assertEquals("This week · Study 9h", WeekUi.build(EN, listOf(off, night), at(16, 8, 0)).weeklyTotals)
        assertNull(WeekUi.build(EN, listOf(off), at(16, 8, 0)).weeklyTotals)
        assertNull(WeekUi.build(EN, emptyList(), at(16, 8, 0)).weeklyTotals)
    }

    @Test
    fun status_nothingScheduled() {
        assertEquals(StatusUi.NothingScheduled, WeekUi.status(EN, emptyList(), at(16, 8, 0)))
    }

    @Test
    fun repeatText_presets() {
        assertEquals("Every day", WeekUi.repeatText(EN, DayOfWeek.entries))
        assertEquals("Weekdays", WeekUi.repeatText(EN, DayOfWeek.entries.take(5)))
        assertEquals("Weekends", WeekUi.repeatText(EN, listOf(SUNDAY, SATURDAY)))
    }
}
