package com.example.snoozer

import com.example.snoozer.alarm.Category
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
        val ui = WeekUi.build(listOf(late, early), at(18, 8, 0))   // Wednesday

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
        val card = WeekUi.build(listOf(a), at(16, 12, 0)).days[0].cards.single()
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
        assertEquals("2 snoozers · 3h quiet", WeekUi.build(listOf(a, b, off), at(16, 8, 0)).days[0].summary)
        assertEquals("1 snoozer · all off", WeekUi.build(listOf(off), at(16, 8, 0)).days[0].summary)
    }

    @Test
    fun activeNow_overnightBelongsToTheStartDayPage() {
        val a = alarm(1, 22, 0, 7, 0, SUNDAY, MONDAY)
        val ui = WeekUi.build(listOf(a), at(16, 6, 0))   // Monday 06:00, inside Sunday's window
        assertTrue(ui.days[6].cards.single().activeNow)   // Sunday page
        assertFalse(ui.days[0].cards.single().activeNow)  // Monday's own window starts at 22:00
    }

    @Test
    fun status_quietNow() {
        val a = alarm(1, 9, 0, 10, 30, MONDAY, work = "Study")
        assertEquals(StatusUi.QuietNow(until = "10:30", category = Category.Study, left = "1h 10m left"),
            WeekUi.status(listOf(a), at(16, 9, 20)))
    }

    @Test
    fun status_next_todayTomorrowAndLater() {
        val mon = alarm(1, 22, 0, 23, 0, MONDAY)
        assertEquals(StatusUi.Next("22:00", "today", Category.Work, "in 2h 15m"),
            WeekUi.status(listOf(mon), at(16, 19, 45)))
        assertEquals(StatusUi.Next("22:00", "tomorrow", Category.Work, "in 1 day"),
            WeekUi.status(listOf(mon), at(15, 21, 0)))   // Sunday evening
        val sat = alarm(2, 9, 0, 10, 0, SATURDAY)
        assertEquals(StatusUi.Next("09:00", "Sat", Category.Work, "in 5 days"),
            WeekUi.status(listOf(sat), at(16, 8, 0)))
    }

    @Test
    fun withoutDndAccess_statusSaysPausedAndNoCardIsActive() {
        val a = alarm(1, 9, 0, 10, 30, MONDAY)
        val ui = WeekUi.build(listOf(a), at(16, 9, 20), canMute = false)   // inside the window
        assertEquals(StatusUi.NoAccess, ui.status)
        assertFalse(ui.days[0].cards.single().activeNow)
    }

    @Test
    fun status_nothingScheduled() {
        assertEquals(StatusUi.NothingScheduled, WeekUi.status(emptyList(), at(16, 8, 0)))
    }

    @Test
    fun repeatText_presets() {
        assertEquals("Every day", WeekUi.repeatText(DayOfWeek.entries))
        assertEquals("Weekdays", WeekUi.repeatText(DayOfWeek.entries.take(5)))
        assertEquals("Weekends", WeekUi.repeatText(listOf(SUNDAY, SATURDAY)))
    }
}
