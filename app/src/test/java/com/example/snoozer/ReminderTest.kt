package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.ReminderText
import com.example.snoozer.alarm.ReminderTime
import com.example.snoozer.util.NotificationAsk
import com.example.snoozer.util.notificationAsk
import com.example.snoozer.util.refusedForGood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime

class ReminderTest {

    private fun alarm(sh: Int, sm: Int, eh: Int, em: Int, work: String = "Work") =
        Alarm(id = 1, startHour = sh, startMinute = sm, endHour = eh, endMinute = em,
            daysOfWeek = listOf(MONDAY), work = work)

    @Test
    fun reminderTime_fiveMinutesBeforeTheStart() {
        assertEquals(ReminderTime(MONDAY, 8, 55), AlarmTime.reminderTime(alarm(9, 0, 17, 0), MONDAY))
        assertEquals(ReminderTime(MONDAY, 18, 59), AlarmTime.reminderTime(alarm(19, 4, 21, 0), MONDAY))
        assertEquals(ReminderTime(MONDAY, 0, 0), AlarmTime.reminderTime(alarm(0, 5, 1, 0), MONDAY))
    }

    @Test
    fun reminderTime_justAfterMidnight_remindsTheDayBefore() {
        assertEquals(ReminderTime(SUNDAY, 23, 57), AlarmTime.reminderTime(alarm(0, 2, 1, 0), MONDAY))
        assertEquals(ReminderTime(SUNDAY, 23, 55), AlarmTime.reminderTime(alarm(0, 0, 1, 0), MONDAY))
    }

    @Test
    fun reminderText_perCategory() {
        assertEquals(
            ReminderText("Study starts in 5 minutes", "Your phone goes fully silent at 19:00 until 21:00."),
            ReminderText.of(alarm(19, 0, 21, 0, work = "Study")),
        )
        assertEquals(
            ReminderText("Work starts at 09:00", "Your phone goes on vibrate until 17:00."),
            ReminderText.of(alarm(9, 0, 17, 0)),
        )
        // Overnight windows say the end is tomorrow.
        assertEquals(
            "Your phone goes fully silent at 22:00 until 07:00 tomorrow.",
            ReminderText.of(alarm(22, 0, 7, 0, work = "Study")).body,
        )
    }


    @Test
    fun notificationAsk_settingsOnlyWhenTheDialogCanNoLongerHelp() {
        assertEquals(NotificationAsk.Ask, notificationAsk(permissionGranted = false, refusedForGood = false))
        assertEquals(NotificationAsk.OpenSettings, notificationAsk(permissionGranted = false, refusedForGood = true))
        // Allowed but switched off in Settings: back to Settings.
        assertEquals(NotificationAsk.OpenSettings, notificationAsk(permissionGranted = true, refusedForGood = false))
    }

    @Test
    fun refusedForGood_onlyAfterTheSecondNo() {
        // First "Don't allow": the rationale flag turns on, so Android will ask again.
        assertFalse(refusedForGood(granted = false, rationaleBefore = false, rationaleAfter = true))
        // Dialog closed without choosing: the flag doesn't change.
        assertFalse(refusedForGood(granted = false, rationaleBefore = false, rationaleAfter = false))
        assertFalse(refusedForGood(granted = false, rationaleBefore = true, rationaleAfter = true))
        // Second "Don't allow": the flag turns off again; only Settings can allow it now.
        assertTrue(refusedForGood(granted = false, rationaleBefore = true, rationaleAfter = false))
        assertFalse(refusedForGood(granted = true, rationaleBefore = true, rationaleAfter = false))
    }

    @Test
    fun minutesUntilStart_nextOccurrenceOfThatStartDay() {
        val a = alarm(9, 0, 10, 0)
        assertEquals(5, AlarmTime.minutesUntilStart(a, MONDAY, LocalDateTime.of(2026, 11, 16, 8, 55)))   // Monday
        // Just after midnight, reminded the evening before.
        val b = alarm(0, 2, 1, 0)
        assertEquals(5, AlarmTime.minutesUntilStart(b, MONDAY, LocalDateTime.of(2026, 11, 15, 23, 57)))  // Sunday
        // Already started: the next one is a week away.
        assertEquals(7 * 24 * 60 - 2, AlarmTime.minutesUntilStart(a, MONDAY, LocalDateTime.of(2026, 11, 16, 9, 2)))
    }

    @Test
    fun reminderText_usesTheMinutesLeft() {
        assertEquals("Study starts in 4 minutes", ReminderText.of(alarm(19, 0, 21, 0, work = "Study"), 4).title)
        assertEquals("Study starts in 1 minute", ReminderText.of(alarm(19, 0, 21, 0, work = "Study"), 1).title)
    }
}
