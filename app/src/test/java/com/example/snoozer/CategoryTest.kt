package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.category
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.MONDAY

class CategoryTest {

    @Test
    fun from_readsTheStoredLabels() {
        assertEquals(Category.Work, Category.from("Work"))
        assertEquals(Category.Study, Category.from("Study"))
    }

    @Test
    fun from_unknownTextFallsBackToWork() {
        assertEquals(Category.Work, Category.from(""))
        assertEquals(Category.Work, Category.from("study"))   // labels are exact, as stored
    }

    @Test
    fun stored_roundTrips() {
        Category.entries.forEach { assertEquals(it, Category.from(it.stored)) }
    }

    @Test
    fun alarm_exposesItsCategory() {
        val alarm = Alarm(startHour = 9, startMinute = 0, endHour = 10, endMinute = 0,
            daysOfWeek = listOf(MONDAY), work = "Study")
        assertEquals(Category.Study, alarm.category)
    }
}
