package com.example.snoozer

import com.example.snoozer.timetable.IcsParser
import com.example.snoozer.timetable.ParsedClass
import com.example.snoozer.timetable.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IcsParserTest {

    private fun file(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("timetable/$name")) { "missing $name" }.readText()

    private val toronto = ZoneId.of("America/Toronto")
    private fun List<ParsedClass>.named(prefix: String) = single { it.name.startsWith(prefix) }

    @Test
    fun weeklyRepeats_withUntilCountAndNoEnd() {
        val result = IcsParser.parse(file("weekly.ics"), toronto)
        val math = result.classes.named("MATH 101")
        assertEquals("MATH 101 - Lecture", math.name)
        assertEquals(LocalTime.of(9, 30), math.start)
        assertEquals(LocalTime.of(10, 20), math.end)
        assertEquals(setOf(MONDAY, WEDNESDAY, FRIDAY), math.days)
        // DTSTART is Tue 8 Sep; the first Mon/Wed/Fri class on or after it is Wed 9 Sep.
        assertEquals(LocalDate.of(2026, 9, 9), math.from)
        // UNTIL 4 Dec 23:59 UTC is 18:59 in Toronto: Friday 4 Dec's 09:30 class is the last.
        assertEquals(LocalDate.of(2026, 12, 4), math.until)

        val lab = result.classes.named("CS 135")
        assertEquals("CS 135, Lab", lab.name) // "\," unescaped
        assertEquals(setOf(TUESDAY), lab.days)
        // COUNT=12 from Tue 8 Sep: the 12th is 24 Nov. Still 14:30 after the clocks change on 1 Nov.
        assertEquals(LocalDate.of(2026, 11, 24), lab.until)
        assertEquals(LocalTime.of(14, 30), lab.start)

        // A folded SUMMARY is joined back; DURATION gives the end; no end date means none.
        val engl = result.classes.named("ENGL 109")
        assertEquals("ENGL 109 Seminar with a very long folded name that continues here", engl.name)
        assertEquals(LocalTime.of(21, 20), engl.end)
        assertEquals(setOf(THURSDAY), engl.days)
        assertNull(engl.until)

        assertEquals(3, result.classes.size)
        // Every other week, an all-day week, a one-off exam; the cancelled event isn't counted.
        assertEquals(mapOf(SkipReason.NotWeekly to 1, SkipReason.AllDay to 1, SkipReason.OneOff to 1), result.skipped)
    }

    @Test
    fun timesMoveToThePhonesZone_includingTheDay() {
        // Thursday 18:30 in Toronto is Friday 07:30 in Seoul.
        val engl = IcsParser.parse(file("weekly.ics"), ZoneId.of("Asia/Seoul")).classes.named("ENGL 109")
        assertEquals(setOf(FRIDAY), engl.days)
        assertEquals(LocalTime.of(7, 30), engl.start)
    }

    @Test
    fun classesListedOneByOne_becomeWeekly() {
        val result = IcsParser.parse(file("listed.ics"), ZoneId.of("UTC"))
        // Three Fridays of the same tutorial: weekly, from the first to the last.
        val phys = result.classes.single()
        assertEquals("PHYS 121 Tutorial", phys.name)
        assertEquals(setOf(FRIDAY), phys.days)
        assertEquals(LocalDate.of(2026, 9, 11), phys.from)
        assertEquals(LocalDate.of(2026, 9, 25), phys.until)
        // A lone Monday tutorial and the office hour are one-offs.
        assertEquals(mapOf(SkipReason.OneOff to 2), result.skipped)
    }

    @Test
    fun overnightClass_andLongEvents() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Night lab
            DTSTART:20260907T220000
            DTEND:20260908T010000
            RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261130
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Field trip
            DTSTART:20260907T080000
            DTEND:20260908T090000
            RRULE:FREQ=WEEKLY
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Monthly
            DTSTART:20260907T080000
            DTEND:20260907T090000
            RRULE:FREQ=MONTHLY;BYDAY=1MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = IcsParser.parse(ics, ZoneId.of("UTC"))
        val night = result.classes.single()
        assertEquals(LocalTime.of(22, 0), night.start)
        assertEquals(LocalTime.of(1, 0), night.end)
        assertEquals(LocalDate.of(2026, 11, 30), night.until) // a date UNTIL counts the whole day
        assertEquals(mapOf(SkipReason.TooLong to 1, SkipReason.NotWeekly to 1), result.skipped)
    }

    @Test
    fun brokenOrForeignInput_isSkippedNotCrashed() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:No start
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Bad time
            DTSTART:2026-09-07 09:00
            DTEND:20260907T100000
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY;LANGUAGE=en:Windows zone
            DTSTART;TZID="Eastern Standard Time":20260907T090000
            DTEND;TZID="Eastern Standard Time":20260907T100000
            RRULE:FREQ=WEEKLY;COUNT=2
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val result = IcsParser.parse(ics, ZoneId.of("UTC"))
        // An unknown zone name is read as the phone's own zone.
        val c = result.classes.single()
        assertEquals("Windows zone", c.name)
        assertEquals(LocalTime.of(9, 0), c.start)
        assertEquals(mapOf(SkipReason.Unreadable to 2), result.skipped)
        assertTrue(IcsParser.looksLikeCalendar(ics))
        assertFalse(IcsParser.looksLikeCalendar("hello"))
    }

    @Test
    fun clockChangeInTheFilesZoneOnly_splitsTheClassAtTheRealHours() {
        // Edmonton moves its clocks on 1 Nov; Whitehorse stays at UTC-7 all year.
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:MATH 101
            DTSTART;TZID=America/Edmonton:20260907T093000
            DTEND;TZID=America/Edmonton:20260907T102000
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261204T235959Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val parts = IcsParser.parse(ics, ZoneId.of("America/Whitehorse")).classes.sortedBy { it.from }
        assertEquals(2, parts.size)
        assertEquals(LocalTime.of(8, 30), parts[0].start)
        assertEquals(LocalDate.of(2026, 10, 30), parts[0].until)
        assertEquals(LocalTime.of(9, 30), parts[1].start)
        assertEquals(LocalDate.of(2026, 11, 2), parts[1].from)
        assertEquals(LocalDate.of(2026, 12, 4), parts[1].until)
        // In the file's own zone it's one class all term.
        assertEquals(1, IcsParser.parse(ics, ZoneId.of("America/Edmonton")).classes.size)
    }

    @Test
    fun count_includesADtstartOffItsDays() {
        // DTSTART is a Tuesday, the rule is Mon/Wed: DTSTART is instance 1 of 4.
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Seminar
            DTSTART:20260908T100000
            DTEND:20260908T110000
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val c = IcsParser.parse(ics, ZoneId.of("UTC")).classes.single()
        assertEquals(LocalDate.of(2026, 9, 9), c.from)
        // Tue 8 (DTSTART), Wed 9, Mon 14, Wed 16; counting only Mon/Wed would run to Mon 21.
        assertEquals(LocalDate.of(2026, 9, 16), c.until)
        assertEquals(setOf(MONDAY, WEDNESDAY), c.days)
    }
}
