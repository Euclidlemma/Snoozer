package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.quietLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime

class QuietLevelTest {

    // AudioManager.RINGER_MODE_* values.
    private val silent = 0
    private val vibrate = 1
    private val normal = 2

    private fun alarm(id: Int, sh: Int, eh: Int, category: Category, vararg days: DayOfWeek, enabled: Boolean = true) =
        Alarm(id = id, startHour = sh, startMinute = 0, endHour = eh, endMinute = 0,
            daysOfWeek = days.toList(), work = category.stored, isEnabled = enabled)

    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute)

    @Test
    fun eachCategoryHasItsLevel() {
        assertEquals(QuietLevel.Vibrate, Category.Work.quietLevel)
        assertEquals(QuietLevel.Silent, Category.Study.quietLevel)
    }

    @Test
    fun ringerMode_neverLouderThanTheUsersOwn() {
        assertEquals(vibrate, QuietLevel.Vibrate.ringerModeFor(normal))
        assertEquals(vibrate, QuietLevel.Vibrate.ringerModeFor(vibrate))
        assertEquals(silent, QuietLevel.Vibrate.ringerModeFor(silent))   // silenced by hand stays silent
        assertEquals(silent, QuietLevel.Silent.ringerModeFor(normal))
        assertEquals(silent, QuietLevel.Silent.ringerModeFor(vibrate))
    }

    @Test
    fun quietest_silentBeatsVibrate() {
        assertEquals(QuietLevel.Silent, QuietLevel.quietest(listOf(QuietLevel.Vibrate, QuietLevel.Silent)))
        assertEquals(QuietLevel.Vibrate, QuietLevel.quietest(listOf(QuietLevel.Vibrate)))
        assertNull(QuietLevel.quietest(emptyList()))
    }

    @Test
    fun levelAt_overlappingWindowsUseTheQuietest() {
        val work = alarm(1, 9, 17, Category.Work, MONDAY)
        val study = alarm(2, 10, 12, Category.Study, MONDAY)
        val alarms = listOf(work, study)
        assertEquals(QuietLevel.Vibrate, AlarmTime.quietLevelAt(alarms, at(16, 9, 30)))
        assertEquals(QuietLevel.Silent, AlarmTime.quietLevelAt(alarms, at(16, 11)))
        // Study ends at 12:00; Work keeps going, so the phone goes back to vibrate.
        assertEquals(QuietLevel.Vibrate, AlarmTime.quietLevelAt(alarms, at(16, 12)))
        assertNull(AlarmTime.quietLevelAt(alarms, at(16, 17)))
    }

    @Test
    fun levelAt_skipsSwitchedOffAndExcludedWindows() {
        val work = alarm(1, 9, 17, Category.Work, MONDAY)
        val studyOff = alarm(2, 10, 12, Category.Study, MONDAY, enabled = false)
        assertEquals(QuietLevel.Vibrate, AlarmTime.quietLevelAt(listOf(work, studyOff), at(16, 11)))
        // The window whose unmute is firing is left out.
        val study = alarm(3, 10, 12, Category.Study, MONDAY)
        assertEquals(QuietLevel.Vibrate, AlarmTime.quietLevelAt(listOf(work, study), at(16, 11), excludingId = 3))
        assertNull(AlarmTime.quietLevelAt(listOf(study), at(16, 11), excludingId = 3))
    }

    @Test
    fun levelAt_countsLastNightsOvernightWindow() {
        val night = alarm(1, 22, 7, Category.Study, SUNDAY)
        assertEquals(QuietLevel.Silent, AlarmTime.quietLevelAt(listOf(night), at(16, 6)))   // Monday 06:00
        assertNull(AlarmTime.quietLevelAt(listOf(night), at(16, 7)))
    }
}
