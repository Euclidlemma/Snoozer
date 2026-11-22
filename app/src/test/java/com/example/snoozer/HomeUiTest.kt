package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.home.HomeUi
import com.example.snoozer.util.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.LocalDateTime

class HomeUiTest {

    private val all = Permissions(dnd = true, exactAlarms = true, exactAlarmsApplies = true)
    private val a = Alarm(id = 1, startHour = 9, startMinute = 0, endHour = 10, endMinute = 30,
        daysOfWeek = listOf(MONDAY), work = "Work")

    // 2026-11-16 is a Monday.
    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 11, 16, hour, minute)

    @Test
    fun statusLine_allThreeCases() {
        assertEquals("Next quiet time: 09:00 today", HomeUi.build(listOf(a), at(8, 0), all).statusLine)
        assertEquals("Quiet now, until 10:30", HomeUi.build(listOf(a), at(9, 15), all).statusLine)
        assertEquals("No quiet times planned yet", HomeUi.build(emptyList(), at(8, 0), all).statusLine)
    }

    @Test
    fun ready_needsDndOnly() {
        assertTrue(HomeUi.build(emptyList(), at(8, 0), all.copy(exactAlarms = false)).ready)
        assertFalse(HomeUi.build(emptyList(), at(8, 0), all.copy(dnd = false)).ready)
    }
}
