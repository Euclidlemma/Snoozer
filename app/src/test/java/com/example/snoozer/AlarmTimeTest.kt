package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeTest {

    private fun alarm(sh: Int, sm: Int, eh: Int, em: Int, vararg days: java.time.DayOfWeek) =
        Alarm(startHour = sh, startMinute = sm, endHour = eh, endMinute = em,
            daysOfWeek = days.toList(), work = "Work")

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute)
    private val zone = ZoneId.of("UTC")

    @Test
    fun sameDayWindow_activeOnlyInsideRangeOnSelectedDay() {
        val a = alarm(9, 0, 11, 0, MONDAY)
        assertTrue(AlarmTime.isActiveAt(a, at(16, 9, 0)))
        assertTrue(AlarmTime.isActiveAt(a, at(16, 10, 59)))
        assertFalse(AlarmTime.isActiveAt(a, at(16, 11, 0)))   // end is exclusive
        assertFalse(AlarmTime.isActiveAt(a, at(16, 8, 59)))
        assertFalse(AlarmTime.isActiveAt(a, at(17, 10, 0)))   // Tuesday not selected
    }

    @Test
    fun overnightWindow_spansIntoNextDay() {
        val a = alarm(22, 0, 7, 0, SUNDAY)
        assertTrue(AlarmTime.isOvernight(a))
        assertEquals(MONDAY, AlarmTime.endDay(a, SUNDAY))
        assertTrue(AlarmTime.isActiveAt(a, at(15, 23, 30)))   // Sunday night
        assertTrue(AlarmTime.isActiveAt(a, at(16, 6, 59)))    // Monday morning
        assertFalse(AlarmTime.isActiveAt(a, at(16, 7, 0)))
        assertFalse(AlarmTime.isActiveAt(a, at(16, 23, 0)))   // Monday night not selected
    }

    @Test
    fun saturdayOvernight_wrapsToSunday() {
        val a = alarm(23, 0, 1, 0, SATURDAY)
        assertEquals(SUNDAY, AlarmTime.endDay(a, SATURDAY))
        assertTrue(AlarmTime.isActiveAt(a, at(22, 0, 30)))    // Sunday 00:30
    }

    @Test
    fun equalStartAndEnd_isNeverActive() {
        assertFalse(AlarmTime.isActiveAt(alarm(9, 0, 9, 0, MONDAY), at(16, 9, 0)))
    }

    @Test
    fun nextTrigger_laterToday() {
        val now = ZonedDateTime.of(at(16, 8, 0), zone)
        assertEquals(ZonedDateTime.of(at(16, 9, 0), zone), AlarmTime.nextTrigger(MONDAY, 9, 0, now))
    }

    @Test
    fun nextTrigger_pastOrExactlyNow_rollsToNextWeek() {
        val now = ZonedDateTime.of(at(16, 9, 0), zone)
        assertEquals(ZonedDateTime.of(at(23, 9, 0), zone), AlarmTime.nextTrigger(MONDAY, 9, 0, now))
    }

    @Test
    fun nextTrigger_laterThisWeek() {
        val now = ZonedDateTime.of(at(17, 12, 0), zone)   // Tuesday
        assertEquals(ZonedDateTime.of(at(20, 9, 0), zone), AlarmTime.nextTrigger(FRIDAY, 9, 0, now))
        assertEquals(ZonedDateTime.of(at(24, 9, 0), zone), AlarmTime.nextTrigger(TUESDAY, 9, 0, now))
    }
}
