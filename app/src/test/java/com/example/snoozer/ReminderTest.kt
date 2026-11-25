package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.ReminderText
import com.example.snoozer.alarm.ReminderTime
import com.example.snoozer.util.NotificationAsk
import com.example.snoozer.util.notificationAsk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY

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
    fun notificationAsk_asksWhileADialogCanAppear() {
        // First time: ask.
        assertEquals(NotificationAsk.Ask, notificationAsk(permissionGranted = false, askedBefore = false, showRationale = false))
        // Refused once: Android still shows the dialog again.
        assertEquals(NotificationAsk.Ask, notificationAsk(permissionGranted = false, askedBefore = true, showRationale = true))
        // Refused for good: only Settings can allow it now.
        assertEquals(NotificationAsk.OpenSettings, notificationAsk(permissionGranted = false, askedBefore = true, showRationale = false))
        // Allowed but switched off in Settings: back to Settings.
        assertEquals(NotificationAsk.OpenSettings, notificationAsk(permissionGranted = true, askedBefore = true, showRationale = false))
    }
}
