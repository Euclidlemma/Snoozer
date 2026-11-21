package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.EditAlarm.EditUi
import com.example.snoozer.EditAlarm.RepeatPreset
import com.example.snoozer.alarm.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY

class EditFormTest {

    private val base = EditForm(9, 0, 10, 30, listOf(MONDAY), "Work")

    @Test
    fun toggleDay_keepsMondayFirstOrder() {
        val f = base.toggleDay(FRIDAY).toggleDay(WEDNESDAY)
        assertEquals(listOf(MONDAY, WEDNESDAY, FRIDAY), f.days)
        assertEquals(listOf(WEDNESDAY, FRIDAY), f.toggleDay(MONDAY).days)
    }

    @Test
    fun preset_setsDaysAndIsRecognised() {
        val f = base.withPreset(RepeatPreset.Weekends)
        assertEquals(listOf(SATURDAY, SUNDAY), f.days)
        assertEquals(RepeatPreset.Weekends, EditUi.describe(f, base, isNew = false).preset)
        assertNull(EditUi.describe(base, base, isNew = false).preset)
    }

    @Test
    fun summary_sameDayAndOvernight() {
        assertEquals("Quiet for 1h 30m", EditUi.describe(base, base, false).summary)
        val night = base.withStart(22, 0).withEnd(7, 0)
        assertEquals("Quiet for 9h, until 07:00 the next day", EditUi.describe(night, base, false).summary)
    }

    @Test
    fun errors_blockSaving() {
        val same = base.withEnd(9, 0)
        assertEquals("Start and end can't be the same time.", EditUi.describe(same, base, false).error)
        assertFalse(EditUi.describe(same, base, false).canSave)
        val noDays = base.toggleDay(MONDAY)
        assertEquals("Pick at least one day.", EditUi.describe(noDays, base, false).error)
        assertTrue(EditUi.describe(base, base, false).canSave)
    }

    @Test
    fun hasChanges_onlyWhenEditedOrNew() {
        assertFalse(EditUi.describe(base, base, isNew = false).hasChanges)
        assertTrue(EditUi.describe(base.withEnd(11, 0), base, isNew = false).hasChanges)
        assertTrue(EditUi.describe(base, base, isNew = true).hasChanges)
    }

    @Test
    fun applyTo_keepsIdAndOnOffState() {
        val saved = Alarm(id = 7, startHour = 1, startMinute = 0, endHour = 2, endMinute = 0,
            daysOfWeek = listOf(SUNDAY), work = "Study", isEnabled = false)
        val updated = base.applyTo(saved)
        assertEquals(7, updated.id)
        assertFalse(updated.isEnabled)
        assertEquals(listOf(MONDAY), updated.daysOfWeek)
        assertEquals("Work", updated.work)
    }

    @Test
    fun newForm_preselectsTheDayItCameFrom() {
        assertEquals(listOf(WEDNESDAY), EditForm.new(WEDNESDAY).days)
        assertEquals(emptyList<Any>(), EditForm.new(null).days)
    }
}
