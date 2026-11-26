package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.EditAlarm.EditUi
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.dayPickers.MonthUi
import com.example.snoozer.dayPickers.WeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class MonthUiTest {

    // Wed 18 Nov 2026, 09:20.
    private val now = LocalDateTime.of(2026, 11, 18, 9, 20)
    private val work = Alarm(1, 9, 0, 17, 0, DayOfWeek.entries.take(5), work = "Work")
    private val math = Alarm(2, 10, 30, 11, 20, listOf(MONDAY, FRIDAY), work = "Study", name = "MATH 101",
        fromDate = LocalDate.of(2026, 9, 7), untilDate = LocalDate.of(2026, 12, 4))
    private val lab = Alarm(3, 14, 0, 16, 0, listOf(TUESDAY), work = "Study", name = "CS 135", isEnabled = false)
    private val alarms = listOf(work, math, lab)

    @Test
    fun grid_isWholeWeeksMondayFirst() {
        val m = MonthUi.build(alarms, now, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 18))
        assertEquals("November 2026", m.title)
        // 1 Nov 2026 is a Sunday: the grid starts Mon 26 Oct and ends Sun 6 Dec.
        assertEquals(LocalDate.of(2026, 10, 26), m.weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 12, 6), m.weeks.last().last().date)
        assertTrue(m.weeks.all { it.size == 7 })
        assertFalse(m.weeks.first().first().inMonth)
        assertTrue(m.weeks.flatten().single { it.isToday }.isSelected)
    }

    @Test
    fun marks_followCategoriesDatesAndSwitches() {
        val m = MonthUi.build(alarms, now, YearMonth.of(2026, 12), LocalDate.of(2026, 12, 4))
        val cells = m.weeks.flatten().associateBy { it.date }
        val fri4 = cells.getValue(LocalDate.of(2026, 12, 4))
        assertTrue(fri4.work && fri4.study && fri4.endsHere)
        assertEquals("Friday 4 December: Work and Study, last day of MATH 101", fri4.description)
        // After the term: Work only. The switched-off lab marks nothing.
        val mon7 = cells.getValue(LocalDate.of(2026, 12, 7))
        assertTrue(mon7.work)
        assertFalse(mon7.study)
        assertFalse(cells.getValue(LocalDate.of(2026, 12, 1)).study)
        assertEquals("Saturday 5 December: nothing planned", cells.getValue(LocalDate.of(2026, 12, 5)).description)
    }

    @Test
    fun selectedDay_listsThatDatesWindows_andQuietNowOnlyToday() {
        val today = MonthUi.selectedDay(alarms, now, LocalDate.of(2026, 11, 18))
        assertEquals("Wednesday 18 November", today.title)
        assertEquals(listOf(1), today.cards.map { it.id })
        assertTrue(today.cards.single().activeNow)
        // Next Wednesday: same window, but it isn't running now.
        assertFalse(MonthUi.selectedDay(alarms, now, LocalDate.of(2026, 11, 25)).cards.single().activeNow)
        // The last class's day says so; the switched-off lab is still listed on its Tuesday.
        assertEquals(listOf("Last MATH 101 class"), MonthUi.selectedDay(alarms, now, LocalDate.of(2026, 12, 4)).notes)
        assertEquals(listOf(1, 3), MonthUi.selectedDay(alarms, now, LocalDate.of(2026, 11, 24)).cards.map { it.id })
    }

    @Test
    fun weekCards_sayTheNameAndDates() {
        val friday = WeekUi.build(alarms, now).days[4]
        val card = friday.cards.single { it.id == 2 }
        assertEquals("MATH 101", card.name)
        assertEquals("Until 4 Dec", card.dateNote)
        val after = WeekUi.build(alarms, LocalDateTime.of(2026, 12, 9, 12, 0))
        assertEquals(1, after.endedCount)
        assertEquals("Ended 4 Dec", after.days[0].cards.single { it.id == 2 }.dateNote)
        val before = WeekUi.build(alarms, LocalDateTime.of(2026, 8, 20, 12, 0))
        assertEquals("Starts 7 Sep", before.days[0].cards.single { it.id == 2 }.dateNote)
        assertNull(before.days[0].cards.single { it.id == 1 }.dateNote)
    }

    @Test
    fun aClassSplitAtAClockChange_hasNoMidTermEndMark() {
        val before = Alarm(5, 8, 30, 9, 20, listOf(MONDAY, FRIDAY), work = "Study", name = "MATH 101",
            fromDate = LocalDate.of(2026, 9, 7), untilDate = LocalDate.of(2026, 10, 30))
        val after = before.copy(id = 6, startHour = 9, endHour = 10, fromDate = LocalDate.of(2026, 11, 2), untilDate = LocalDate.of(2026, 12, 4))
        val oct = MonthUi.build(listOf(before, after), now, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 30))
        assertFalse(oct.weeks.flatten().single { it.date == LocalDate.of(2026, 10, 30) }.endsHere)
        assertTrue(oct.selected.notes.isEmpty())
        // The real last class still gets one.
        assertEquals(listOf("Last MATH 101 class"), MonthUi.selectedDay(listOf(before, after), now, LocalDate.of(2026, 12, 4)).notes)
    }

    @Test
    fun daySummary_leavesOutClassesBeforeTheirTerm() {
        val january = Alarm(9, 14, 30, 16, 20, listOf(TUESDAY), work = "Study", name = "LAB",
            fromDate = LocalDate.of(2027, 1, 12), untilDate = LocalDate.of(2027, 3, 30))
        val tuesday = WeekUi.build(listOf(january), now).days[1]
        assertEquals("1 snoozer · not started yet", tuesday.summary)
    }

    @Test
    fun weeklyTotals_leaveOutClassesOutsideTheirTerm() {
        // In term: Work 5 x 8h, Study 2 x 50m.
        assertEquals(mapOf(Category.Work to 2400, Category.Study to 100), WeekUi.weeklyMinutes(alarms, now.toLocalDate()))
        // After it: Work only.
        assertEquals(mapOf(Category.Work to 2400), WeekUi.weeklyMinutes(alarms, LocalDate.of(2026, 12, 9)))
    }

    @Test
    fun editForm_keepsNameAndDatesThroughSavedState() {
        val f = EditForm.from(math).withName("MATH: 101 · Lecture")
        assertEquals(f, EditForm.decode(f.encode()))
        // Text written before names existed still reads back.
        assertEquals(EditForm(8, 0, 9, 0, listOf(MONDAY), Category.Work), EditForm.decode("8:0:9:0:1:Work"))
        assertNull(EditForm.decode("8:0:9:0:1:Work:x:not-a-date:"))
        assertEquals("7 Sep – 4 Dec", EditUi.describe(f, f, isNew = false).dates)
        assertNull(EditUi.describe(f.clearDates(), f, isNew = false).dates)
        assertEquals(EditForm.MAX_NAME, f.withName("y".repeat(100)).name.length)
        // Saved: blank names become none.
        assertNull(f.withName("   ").applyTo(math).name)
    }
}
