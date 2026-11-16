package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.QuietStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime

class QuietStatusTest {

    private fun alarm(id: Int, sh: Int, sm: Int, eh: Int, em: Int, vararg days: DayOfWeek, enabled: Boolean = true) =
        Alarm(id = id, startHour = sh, startMinute = sm, endHour = eh, endMinute = em,
            daysOfWeek = days.toList(), work = "Work", isEnabled = enabled)

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun durationMinutes_sameDayOvernightAndZero() {
        assertEquals(90, AlarmTime.durationMinutes(alarm(1, 9, 0, 10, 30, MONDAY)))
        assertEquals(540, AlarmTime.durationMinutes(alarm(1, 22, 0, 7, 0, MONDAY)))
        assertEquals(0, AlarmTime.durationMinutes(alarm(1, 9, 0, 9, 0, MONDAY)))
    }

    @Test
    fun formatDuration_andTime() {
        assertEquals("45m", AlarmTime.formatDuration(45))
        assertEquals("2h", AlarmTime.formatDuration(120))
        assertEquals("1h 30m", AlarmTime.formatDuration(90))
        assertEquals("0m", AlarmTime.formatDuration(-5))
        assertEquals("09:05", AlarmTime.formatTime(9, 5))
    }

    @Test
    fun nothingScheduled_whenEmptyOrAllDisabled() {
        assertEquals(QuietStatus.NothingScheduled, AlarmTime.quietStatus(emptyList(), at(16, 8, 0)))
        val off = alarm(1, 9, 0, 10, 0, MONDAY, enabled = false)
        assertEquals(QuietStatus.NothingScheduled, AlarmTime.quietStatus(listOf(off), at(16, 8, 0)))
    }

    @Test
    fun nextQuiet_laterToday() {
        val a = alarm(1, 9, 0, 10, 0, MONDAY)
        assertEquals(QuietStatus.NextQuiet(at(16, 9, 0), a), AlarmTime.quietStatus(listOf(a), at(16, 8, 0)))
    }

    @Test
    fun nextQuiet_wrapsToNextWeekAfterTodaysWindowEnded() {
        val a = alarm(1, 9, 0, 10, 0, MONDAY)
        assertEquals(QuietStatus.NextQuiet(at(23, 9, 0), a), AlarmTime.quietStatus(listOf(a), at(16, 10, 0)))
    }

    @Test
    fun nextQuiet_picksEarliestAcrossAlarms() {
        val tue = alarm(1, 9, 0, 10, 0, TUESDAY)
        val monNight = alarm(2, 22, 0, 23, 0, MONDAY)
        assertEquals(QuietStatus.NextQuiet(at(16, 22, 0), monNight),
            AlarmTime.quietStatus(listOf(tue, monNight), at(16, 12, 0)))
    }

    @Test
    fun quietUntil_insideWindow_startIsInclusive() {
        val a = alarm(1, 9, 0, 10, 30, MONDAY)
        assertEquals(QuietStatus.QuietUntil(at(16, 10, 30), a), AlarmTime.quietStatus(listOf(a), at(16, 9, 0)))
    }

    @Test
    fun quietUntil_overnightFromYesterday() {
        val a = alarm(1, 22, 0, 7, 0, SUNDAY)
        assertEquals(QuietStatus.QuietUntil(at(16, 7, 0), a), AlarmTime.quietStatus(listOf(a), at(16, 6, 0)))
    }

    @Test
    fun quietUntil_mergesOverlappingAndBackToBackWindows() {
        val first = alarm(1, 9, 0, 11, 0, MONDAY)
        val inside = alarm(2, 10, 0, 10, 30, MONDAY)       // fully inside the first
        val touching = alarm(3, 11, 0, 12, 0, MONDAY)      // starts exactly when the first ends
        val later = alarm(4, 13, 0, 14, 0, MONDAY)         // gap before it, so not merged
        assertEquals(QuietStatus.QuietUntil(at(16, 12, 0), touching),
            AlarmTime.quietStatus(listOf(first, inside, touching, later), at(16, 10, 15)))
    }

    @Test
    fun minutesBetween_ignoresSeconds() {
        assertEquals(135, AlarmTime.minutesBetween(at(16, 19, 45).plusSeconds(30), at(16, 22, 0)))
    }
}
