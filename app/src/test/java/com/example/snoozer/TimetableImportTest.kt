package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.timetable.ImportUiState
import com.example.snoozer.timetable.ParsedClass
import com.example.snoozer.timetable.SkipReason
import com.example.snoozer.timetable.TimetableImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

class TimetableImportTest {

    private val today = LocalDate.of(2026, 9, 1)
    private fun cls(name: String, vararg days: java.time.DayOfWeek, from: Int = 8, until: LocalDate? = LocalDate.of(2026, 12, 4)) =
        ParsedClass(name, LocalTime.of(9, 30), LocalTime.of(10, 20), days.toSet(), LocalDate.of(2026, 9, from), until)

    @Test
    fun separateEventsForOneClass_mergeIntoOne() {
        // Some exporters write one event per weekday.
        val merged = TimetableImport.merge(listOf(cls("MATH 101", MONDAY, from = 14), cls("MATH 101", WEDNESDAY, from = 9)))
        val only = merged.single().cls
        assertEquals(setOf(MONDAY, WEDNESDAY), only.days)
        assertEquals(LocalDate.of(2026, 9, 9), only.from)
        // One open-ended part keeps the merged class open-ended.
        assertNull(TimetableImport.merge(listOf(cls("A", MONDAY), cls("A", FRIDAY, until = null))).single().cls.until)
    }

    @Test
    fun alreadySaved_isMarkedAndNotAddedTwice() {
        val c = TimetableImport.merge(listOf(cls("MATH 101", MONDAY, WEDNESDAY), cls("CS 135", FRIDAY)))
        val saved = TimetableImport.toAlarms(c, c.map { it.key }.toSet(), emptyList(), Category.Study)
        assertEquals(2, saved.size)
        val existing = saved.take(1).mapIndexed { i, a -> a.copy(id = i + 1) }
        val rows = TimetableImport.rows(c, TimetableImport.defaultChecked(c, existing, today), existing, today)
        assertEquals(listOf(true, false), rows.map { it.alreadyAdded })
        // Asking again adds only the one that's new.
        assertEquals(listOf("CS 135"), TimetableImport.toAlarms(c, c.map { it.key }.toSet(), existing, Category.Study).map { it.name })
    }

    @Test
    fun alarms_carryNameDatesAndCategory() {
        val c = TimetableImport.merge(listOf(cls("MATH 101", WEDNESDAY, MONDAY)))
        val a = TimetableImport.toAlarms(c, setOf(c[0].key), emptyList(), Category.Work).single()
        assertEquals(Alarm(startHour = 9, startMinute = 30, endHour = 10, endMinute = 20,
            daysOfWeek = listOf(MONDAY, WEDNESDAY), work = "Work", name = "MATH 101",
            fromDate = LocalDate.of(2026, 9, 8), untilDate = LocalDate.of(2026, 12, 4)), a)
        // A long name is cut to what the edit screen allows, and still counts as a duplicate.
        val long = TimetableImport.merge(listOf(cls("X".repeat(60), FRIDAY)))
        val saved = TimetableImport.toAlarms(long, setOf(long[0].key), emptyList(), Category.Study).single()
        assertEquals(EditForm.MAX_NAME, saved.name!!.length)
        assertTrue(TimetableImport.alreadyAdded(long[0].cls, listOf(saved)))
    }

    @Test
    fun pastClasses_startUnticked_andSaySo() {
        val c = TimetableImport.merge(listOf(cls("Old", MONDAY, until = LocalDate.of(2026, 4, 30))))
        assertTrue(TimetableImport.defaultChecked(c, emptyList(), today).isEmpty())
        val row = TimetableImport.rows(c, emptySet(), emptyList(), today).single()
        assertEquals("Already over (ended 30 Apr)", row.dates)
        assertFalse(row.checked)
    }

    @Test
    fun rowsAndLabels() {
        val c = TimetableImport.merge(listOf(cls("MATH 101", MONDAY, WEDNESDAY, FRIDAY), cls("Open", FRIDAY, until = null)))
        val rows = TimetableImport.rows(c, c.map { it.key }.toSet(), emptyList(), today)
        assertEquals("Mon, Wed, Fri", rows[0].days)
        assertEquals("09:30 – 10:20", rows[0].time)
        assertEquals("8 Sep – 4 Dec", rows[0].dates)
        assertEquals("from 8 Sep", rows[1].dates)
        val ready = ImportUiState.Ready(rows, Category.Study, null)
        assertEquals("Add 2 classes", ready.addLabel)
        assertEquals("Pick a class to add", ready.copy(rows = rows.map { it.copy(checked = false) }).addLabel)
        assertEquals("3 skipped: 2 all-day, 1 not weekly",
            TimetableImport.skippedText(mapOf(SkipReason.NotWeekly to 1, SkipReason.AllDay to 2)))
        assertNull(TimetableImport.skippedText(emptyMap()))
    }

    @Test
    fun sameClassInTwoTerms_staysTwo_butAReadingWeekGapJoins() {
        val fall = ParsedClass("MATH 101", LocalTime.of(9, 30), LocalTime.of(10, 20), setOf(MONDAY),
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 12, 7))
        val winter = fall.copy(from = LocalDate.of(2027, 1, 4), until = LocalDate.of(2027, 4, 5))
        val merged = TimetableImport.merge(listOf(winter, fall))
        assertEquals(2, merged.size)
        assertEquals(2, merged.map { it.key }.toSet().size) // keys stay distinct
        // A one-week gap (reading week) is the same term.
        val afterBreak = fall.copy(from = LocalDate.of(2026, 12, 14), until = LocalDate.of(2026, 12, 18))
        assertEquals(1, TimetableImport.merge(listOf(fall, afterBreak)).size)
    }
}
