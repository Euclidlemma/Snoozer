package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.EditAlarm.EditUi
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.oneDate
import com.example.snoozer.dayPickers.MonthUi
import com.example.snoozer.dayPickers.WeekUi
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
import java.time.YearMonth

/** Snoozers on one date only ("One date" on the new-snoozer screen). */
class OneDateTest {

    // Wed 25 Nov 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 25, 10, 0)
    private val dec4 = LocalDate.of(2026, 12, 4) // a Friday

    @Test
    fun savedAsASnoozerWhoseFirstAndLastDateAreThatDay() {
        val form = EditForm.new(null, Category.Study, date = dec4).withStart(13, 0).withEnd(15, 0)
        val alarm = form.toNewAlarm()
        assertEquals(listOf(FRIDAY), alarm.daysOfWeek)
        assertEquals(dec4, alarm.fromDate)
        assertEquals(dec4, alarm.untilDate)
        assertEquals(dec4, alarm.oneDate)
        // Read back into the form, it's a one-date snoozer again.
        assertEquals(dec4, EditForm.from(alarm).oneDate)
        // Only that Friday is quiet.
        assertTrue(AlarmTime.isActiveAt(alarm, LocalDateTime.of(2026, 12, 4, 14, 0)))
        assertFalse(AlarmTime.isActiveAt(alarm, LocalDateTime.of(2026, 12, 11, 14, 0)))
        // An imported class with dates isn't one, and neither is a plain weekly snoozer.
        assertNull(alarm.copy(untilDate = LocalDate.of(2026, 12, 18)).oneDate)
        assertNull(alarm.copy(fromDate = null, untilDate = null).oneDate)
    }

    @Test
    fun switchingModes_keepsTheOtherChoice() {
        val weekly = EditForm.new(MONDAY)
        val one = weekly.withOneDate(dec4)
        assertTrue(one.isOneDate)
        assertEquals(listOf(MONDAY), one.repeatWeekly().days)       // back to its weekdays
        assertFalse(one.repeatWeekly().isOneDate)
        // With no weekdays left, going back to weekly takes the date's weekday.
        assertEquals(listOf(FRIDAY), one.copy(days = emptyList()).repeatWeekly().days)
    }

    @Test
    fun savedState_roundTrips_andOldTextStillReads() {
        val f = EditForm.new(null, Category.Work, date = dec4).withName("Dentist")
        assertEquals(f, EditForm.decode(f.encode()))
        // Nine parts: written before one-date snoozers existed.
        assertEquals(EditForm(8, 0, 9, 0, listOf(MONDAY), Category.Work, "x", null, null),
            EditForm.decode("8:0:9:0:1:Work:x::"))
        assertNull(EditForm.decode("8:0:9:0:1:Work:x:::not-a-date"))
    }

    @Test
    fun cantBeSetInThePast() {
        val form = EditForm.new(null, Category.Work, date = dec4)
        fun err(f: EditForm, isNew: Boolean = true, original: EditForm = f) = EditUi.describe(f, original, isNew, now = now).error
        assertNull(err(form))
        assertEquals("Pick a date from today on.", err(form.withOneDate(LocalDate.of(2026, 11, 24))))
        // Today: fine until the window ends; an overnight window ends tomorrow.
        val today = form.withOneDate(now.toLocalDate())
        assertNull(err(today.withStart(9, 0).withEnd(11, 0)))
        assertEquals("That time has already passed today.", err(today.withStart(8, 0).withEnd(9, 30)))
        assertNull(err(today.withStart(22, 0).withEnd(2, 0)))
        // A saved one-date snoozer whose day is over can still be renamed.
        val old = form.withOneDate(LocalDate.of(2026, 11, 20))
        assertNull(err(old.withName("Old"), isNew = false, original = old))
        // ...but not moved to another past day.
        assertEquals("Pick a date from today on.", err(old.withOneDate(LocalDate.of(2026, 11, 19)), isNew = false, original = old))
    }

    @Test
    fun summaryNamesTheDay() {
        val form = EditForm.new(null, Category.Study, date = dec4).withStart(13, 0).withEnd(15, 0)
        val state = EditUi.describe(form, form, isNew = true, now = now)
        assertEquals("Quiet for 2h on Fri 4 Dec", state.summary)
        assertEquals("Only on Friday 4 December. It won't repeat.", state.oneDateText)
        assertNull(state.dates)
        assertEquals("Quiet for 2h today",
            EditUi.describe(form.withOneDate(now.toLocalDate()), form, isNew = true, now = now).summary)
    }

    @Test
    fun weekPagesShowItOnlyInItsWeek_monthHasNoEndMark() {
        val dentist = Alarm(id = 3, startHour = 13, startMinute = 0, endHour = 15, endMinute = 0,
            daysOfWeek = listOf(FRIDAY), work = "Work", name = "Dentist", fromDate = dec4, untilDate = dec4)
        val weekly = Alarm(id = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0,
            daysOfWeek = listOf(FRIDAY, WEDNESDAY), work = "Work")
        // This week (23 - 29 Nov): not on Friday's page.
        assertEquals(listOf(1), WeekUi.build(listOf(dentist, weekly), now).days[4].cards.map { it.id })
        // The week of 4 Dec: there, with its date.
        val thatWeek = WeekUi.build(listOf(dentist, weekly), LocalDateTime.of(2026, 12, 1, 12, 0)).days[4]
        assertEquals(listOf(1, 3), thatWeek.cards.map { it.id })
        assertEquals("Only on Fri 4 Dec", thatWeek.cards.single { it.id == 3 }.dateNote)
        // Month: marked on 4 Dec only, with no "last class" mark or note.
        val dec = MonthUi.build(listOf(dentist), now, YearMonth.of(2026, 12), dec4)
        val cell = dec.weeks.flatten().single { it.date == dec4 }
        assertTrue(cell.work)
        assertFalse(cell.endsHere)
        assertTrue(dec.selected.notes.isEmpty())
        assertFalse(dec.weeks.flatten().single { it.date == LocalDate.of(2026, 12, 11) }.work)
    }

    @Test
    fun aNewSnoozerForToday_startsAtTheNextHourOnceTheUsualWindowIsOver() {
        val late = LocalDateTime.of(2026, 11, 25, 22, 4)
        // Work's usual 09-17 is over at 22:04: 23:00 for the same 8h (into tomorrow).
        val f = EditForm.new(null, Category.Work, date = late.toLocalDate()).notInThePast(late)
        assertEquals(listOf(23, 0, 7, 0), listOf(f.startHour, f.startMinute, f.endHour, f.endMinute))
        assertEquals(late.toLocalDate(), f.oneDate)
        assertNull(EditUi.describe(f, f, isNew = true, now = late).error)
        // Still ahead, another day, or overnight: unchanged.
        val morning = LocalDateTime.of(2026, 11, 25, 8, 0)
        val g = EditForm.new(null, Category.Work, date = morning.toLocalDate())
        assertEquals(g, g.notInThePast(morning))
        val h = EditForm.new(null, Category.Work, date = dec4)
        assertEquals(h, h.notInThePast(late))
        // After 23:00 the next hour is tomorrow, and the date moves with it.
        val veryLate = LocalDateTime.of(2026, 11, 25, 23, 30)
        val k = EditForm.new(null, Category.Study, date = veryLate.toLocalDate()).notInThePast(veryLate)
        assertEquals(LocalDate.of(2026, 11, 26), k.oneDate)
        assertEquals(listOf(java.time.DayOfWeek.THURSDAY), k.days)
        assertEquals(0, k.startHour)
    }
}
