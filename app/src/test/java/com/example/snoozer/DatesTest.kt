package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.QuietStatus
import com.example.snoozer.alarm.ReminderText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A snoozer's first and last dates (set by a timetable import), and its name. */
class DatesTest {

    // 2026-11-16 is a Monday. A "term" from Mon 16 Nov to Fri 4 Dec.
    private val from = LocalDate.of(2026, 11, 16)
    private val until = LocalDate.of(2026, 12, 4)
    private fun cls(sh: Int, eh: Int, vararg days: java.time.DayOfWeek, name: String? = "MATH 101") = Alarm(
        id = 7, startHour = sh, startMinute = 0, endHour = eh, endMinute = 0,
        daysOfWeek = days.toList(), work = "Study", name = name, fromDate = from, untilDate = until,
    )
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)
    private val zone = ZoneId.of("UTC")

    @Test
    fun activeOnlyBetweenItsDates() {
        val a = cls(9, 11, MONDAY, FRIDAY)
        assertFalse(AlarmTime.isActiveAt(a, at(2026, 11, 9, 10)))   // the Monday before the term
        assertTrue(AlarmTime.isActiveAt(a, at(2026, 11, 16, 10)))   // first day
        assertTrue(AlarmTime.isActiveAt(a, at(2026, 12, 4, 10)))    // last day (a Friday)
        assertFalse(AlarmTime.isActiveAt(a, at(2026, 12, 7, 10)))   // the Monday after
    }

    @Test
    fun overnightWindowOnTheLastDay_runsToItsEnd() {
        val a = cls(22, 2, FRIDAY)
        assertTrue(AlarmTime.isActiveAt(a, at(2026, 12, 5, 1)))     // Sat 01:00, started Fri 4 Dec
        assertFalse(AlarmTime.hasEnded(a, at(2026, 12, 5, 1)))
        assertTrue(AlarmTime.hasEnded(a, at(2026, 12, 5, 3)))
        assertFalse(AlarmTime.isActiveAt(a, at(2026, 12, 12, 1)))   // the next week: over
    }

    @Test
    fun endedAndNotStarted() {
        val a = cls(9, 11, MONDAY)
        assertTrue(AlarmTime.notStartedYet(a, at(2026, 11, 15, 12)))
        assertFalse(AlarmTime.notStartedYet(a, at(2026, 11, 16, 0)))
        assertFalse(AlarmTime.hasEnded(a, at(2026, 12, 4, 23)))
        assertTrue(AlarmTime.hasEnded(a, at(2026, 12, 5, 0)))
        // No dates: never ends, always started.
        val plain = a.copy(fromDate = null, untilDate = null)
        assertFalse(AlarmTime.hasEnded(plain, at(2030, 1, 1, 0)))
        assertFalse(AlarmTime.notStartedYet(plain, at(2000, 1, 1, 0)))
    }

    @Test
    fun withinDates_waitsForTheFirstWindow_andStopsAfterTheLast() {
        val a = cls(9, 11, WEDNESDAY)
        // Weekly trigger computed in October: moves on to the term's first Wednesday (18 Nov).
        val october = ZonedDateTime.of(2026, 10, 7, 9, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 9, 0, 0, 0, zone), AlarmTime.withinDates(a, october, 0))
        // Inside the term: unchanged.
        val inTerm = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, zone)
        assertEquals(inTerm, AlarmTime.withinDates(a, inTerm, 0))
        // After the last date: nothing.
        assertNull(AlarmTime.withinDates(a, ZonedDateTime.of(2026, 12, 9, 9, 0, 0, 0, zone), 0))
        // An overnight unmute on Saturday belongs to Friday's window (offset -1): still set.
        val friNight = cls(22, 2, FRIDAY)
        val satUnmute = ZonedDateTime.of(2026, 12, 5, 2, 0, 0, 0, zone)
        assertEquals(satUnmute, AlarmTime.withinDates(friNight, satUnmute, -1))
    }

    @Test
    fun nextQuietTime_isFoundEvenWhenTheTermStartsWeeksAhead() {
        val a = cls(9, 11, WEDNESDAY)
        val status = AlarmTime.quietStatus(listOf(a), at(2026, 10, 1, 12))
        assertEquals(QuietStatus.NextQuiet(at(2026, 11, 18, 9), a), status)
        // And nothing once the term is over.
        assertEquals(QuietStatus.NothingScheduled, AlarmTime.quietStatus(listOf(a), at(2026, 12, 10, 12)))
    }

    @Test
    fun quietLevel_ignoresClassesOutsideTheirDates() {
        val a = cls(9, 11, MONDAY)
        assertNull(AlarmTime.quietLevelAt(listOf(a), at(2026, 12, 7, 10)))
    }

    @Test
    fun reminderSaysTheClassName() {
        val study = cls(9, 11, MONDAY)
        assertEquals("MATH 101 starts in 5 minutes", ReminderText.of(study, EN).title)
        assertEquals("MATH 101 starts at 09:00", ReminderText.of(study.copy(work = "Work"), EN).title)
        // Unnamed (or blank) snoozers keep the category's words.
        assertEquals("Study starts in 5 minutes", ReminderText.of(study.copy(name = null), EN).title)
        assertEquals("Study starts in 5 minutes", ReminderText.of(study.copy(name = "  "), EN).title)
    }
}
