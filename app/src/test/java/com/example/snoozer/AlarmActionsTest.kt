package com.example.snoozer

import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmDao
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.alarm.Logger
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.QuietStretch
import com.example.snoozer.alarm.QuietStretchDao
import com.example.snoozer.alarm.RoomQuietLog
import com.example.snoozer.alarm.Reminders
import com.example.snoozer.alarm.Ringer
import com.example.snoozer.alarm.WindowScheduler
import com.example.snoozer.util.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.LocalDateTime

/** The ringer and reminder rules in AlarmActions, driven through fakes of the phone. */
class AlarmActionsTest {

    private class FakeDao : AlarmDao {
        val rows = MutableStateFlow<List<Alarm>>(emptyList())
        private var nextId = 1
        override fun observeAll(): Flow<List<Alarm>> = rows
        override suspend fun insertAlarm(alarm: Alarm): Long {
            val id = if (alarm.id == 0) nextId++ else alarm.id
            rows.value = rows.value.filter { it.id != id } + alarm.copy(id = id)
            return id.toLong()
        }
        override suspend fun updateAlarm(alarm: Alarm) { rows.value = rows.value.map { if (it.id == alarm.id) alarm else it } }
        override suspend fun deleteAlarm(alarm: Alarm) { rows.value = rows.value.filter { it.id != alarm.id } }
        override suspend fun getAllAlarmsSuspend(): List<Alarm> = rows.value
        override suspend fun getAlarmById(alarmID: Int): Alarm? = rows.value.firstOrNull { it.id == alarmID }
    }

    private class FakeScheduler : WindowScheduler {
        val scheduled = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        override fun scheduleAlarm(alarm: Alarm) { scheduled += alarm.id }
        override fun scheduleAlarmWithDay(alarm: Alarm, day: DayOfWeek) { scheduled += alarm.id }
        override fun cancelAlarm(alarm: Alarm) { cancelled += alarm.id }
        var wiped = false
        override fun isScheduled(alarm: Alarm) = !wiped && alarm.id in scheduled
    }

    /** Records every ringer change: a level, or "restore". */
    private class FakeRinger(var allowed: Boolean = true) : Ringer {
        val calls = mutableListOf<String>()
        var turnedUp = false
        override fun isLouderThan(level: QuietLevel) = turnedUp
        override fun quiet(level: QuietLevel) { calls += level.name }
        override fun restore() { calls += "restore" }
        override fun canChange() = allowed
    }

    private class FakeReminders(var visible: Boolean = true) : Reminders {
        val shown = mutableListOf<Pair<Int, Int>>()   // alarm id to minutes until start
        val cancelled = mutableListOf<Int>()
        override fun canShow() = visible
        override fun show(alarm: Alarm, minutesUntilStart: Int) { shown += alarm.id to minutesUntilStart }
        override fun cancel(alarmId: Int) { cancelled += alarmId }
    }

    /** The real log's rules over an in-memory table. */
    private class FakeStretchDao : QuietStretchDao {
        val rows = mutableListOf<QuietStretch>()
        override suspend fun insert(stretch: QuietStretch): Long { val s = stretch.copy(id = rows.size + 1L); rows += s; return s.id }
        override suspend fun update(stretch: QuietStretch) { rows.replaceAll { if (it.id == stretch.id) stretch else it } }
        override suspend fun open(): QuietStretch? = rows.lastOrNull { it.endMillis == null }
        override fun observeSince(fromMillis: Long): Flow<List<QuietStretch>> = flowOf(rows.toList())
        override suspend fun deleteEndedBefore(millis: Long) { rows.removeAll { it.endMillis != null && it.endMillis!! < millis } }
    }

    private class FixedTime(var at: LocalDateTime) : TimeSource {
        override fun now() = at
        override val minuteTicks: Flow<LocalDateTime> get() = flowOf(at)
    }

    private val dao = FakeDao()
    private val scheduler = FakeScheduler()
    private val ringer = FakeRinger()
    private val reminders = FakeReminders()
    // 2026-11-16 is a Monday.
    private val time = FixedTime(LocalDateTime.of(2026, 11, 16, 11, 0))
    private val warnings = mutableListOf<String>()
    private val stretches = FakeStretchDao()
    private val actions = AlarmActions(AlarmRepository(dao), scheduler, ringer, reminders, time, Logger { warnings += it }, RoomQuietLog(stretches))

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 16, hour, minute)
    private fun alarm(id: Int, sh: Int, eh: Int, category: String, enabled: Boolean = true) =
        Alarm(id = id, startHour = sh, startMinute = 0, endHour = eh, endMinute = 0,
            daysOfWeek = listOf(MONDAY), work = category, isEnabled = enabled)

    private fun saved(vararg alarms: Alarm) { dao.rows.value = alarms.toList() }

    @Test
    fun create_runningWindow_quietsAtItsLevel() = runBlocking {
        actions.create(alarm(0, 10, 12, "Study"))
        assertEquals(listOf("Silent"), ringer.calls)
    }

    @Test
    fun create_futureWindow_leavesTheRinger() = runBlocking {
        actions.create(alarm(0, 14, 15, "Study"))
        assertEquals(emptyList<String>(), ringer.calls)
    }

    @Test
    fun update_runningCategoryChange_appliesTheNewLevel() = runBlocking {
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        actions.update(work, work.copy(work = "Study"))
        assertEquals(listOf("Silent"), ringer.calls)
    }

    @Test
    fun update_runningSameLevel_leavesTheRinger() = runBlocking {
        // The user may have unmuted by hand mid-window; moving the end time keeps that.
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        actions.update(work, work.copy(endHour = 18))
        assertEquals(emptyList<String>(), ringer.calls)
    }

    @Test
    fun update_endMovedBeforeNow_handsBackTheRinger() = runBlocking {
        val study = alarm(1, 9, 17, "Study")
        saved(study)
        actions.update(study, study.copy(endHour = 10))
        assertEquals(listOf("restore"), ringer.calls)
    }

    @Test
    fun unmute_studyEndsInsideWork_goesToVibrate() = runBlocking {
        saved(alarm(1, 9, 17, "Work"), alarm(2, 10, 12, "Study"))
        time.at = at(12)
        actions.onUnmuteFired(2, MONDAY)
        assertEquals(listOf("Vibrate"), ringer.calls)
        assertTrue(2 in scheduler.scheduled)   // set up again for next week
    }

    @Test
    fun unmute_lastWindowEnds_restores() = runBlocking {
        saved(alarm(1, 9, 11, "Study"))
        actions.onUnmuteFired(1, MONDAY)   // at 11:00, the window's end
        assertEquals(listOf("restore"), ringer.calls)
    }

    @Test
    fun mute_forDeletedOrSwitchedOffWindow_doesNothing() = runBlocking {
        saved(alarm(1, 11, 12, "Study", enabled = false))
        actions.onMuteFired(1)
        actions.onMuteFired(99)
        assertEquals(emptyList<String>(), ringer.calls)
    }

    @Test
    fun mute_quietsAndClearsTheReminder() = runBlocking {
        saved(alarm(1, 11, 12, "Study"))
        actions.onMuteFired(1)
        assertEquals(listOf("Silent"), ringer.calls)
        assertEquals(listOf(1), reminders.cancelled)
    }

    @Test
    fun reminder_shownWhenOnTime() = runBlocking {
        saved(alarm(1, 11, 12, "Study"))
        time.at = at(10, 55)
        actions.onReminderFired(1, MONDAY)
        assertEquals(listOf(1 to 5), reminders.shown)
    }

    @Test
    fun reminder_earlyInexactDelivery_saysTheRealMinutes() = runBlocking {
        // Without exact alarms the reminder may come anywhere in 10:50-11:00.
        saved(alarm(1, 11, 12, "Study"))
        time.at = at(10, 50)
        actions.onReminderFired(1, MONDAY)
        assertEquals(listOf(1 to 10), reminders.shown)
    }

    @Test
    fun reminder_droppedWhenLateOrNotWanted() = runBlocking {
        saved(alarm(1, 11, 12, "Study"), alarm(2, 11, 12, "Work", enabled = false))
        time.at = at(11, 2)                 // arrived after the start (deep idle)
        actions.onReminderFired(1, MONDAY)
        time.at = at(10, 55)
        actions.onReminderFired(2, MONDAY)  // switched off
        reminders.visible = false
        actions.onReminderFired(1, MONDAY)  // notifications off
        reminders.visible = true
        ringer.allowed = false
        actions.onReminderFired(1, MONDAY)  // can't actually quiet the phone
        assertEquals(emptyList<Pair<Int, Int>>(), reminders.shown)
    }

    @Test
    fun deleteAndSwitchOff_clearShownReminders() = runBlocking {
        val a = alarm(1, 14, 15, "Study")
        val b = alarm(2, 14, 15, "Work")
        saved(a, b)
        actions.delete(a)
        actions.setEnabled(2, false)
        assertEquals(listOf(1, 2), reminders.cancelled)
    }

    // ---- The quiet log behind the weekly report ----

    private fun local(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()

    @Test
    fun quietLog_followsTheRinger_throughAHandover() = runBlocking {
        // Work 09-17 with Study 10-12 inside it.
        val work = alarm(1, 9, 17, "Work")
        val study = alarm(2, 10, 12, "Study")
        saved(work, study)
        time.at = at(9); actions.onMuteFired(1)
        time.at = at(10); actions.onMuteFired(2)
        time.at = at(12); actions.onUnmuteFired(2, MONDAY)
        time.at = at(17); actions.onUnmuteFired(1, MONDAY)
        assertEquals(
            listOf(Triple("Work", at(9), at(10)), Triple("Study", at(10), at(12)), Triple("Work", at(12), at(17))),
            stretches.rows.map { Triple(it.category, local(it.startMillis), local(it.endMillis!!)) },
        )
        assertTrue(stretches.rows.none { it.louder || it.missed })
    }

    @Test
    fun quietLog_sameLevelAgain_continuesTheStretch() = runBlocking {
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        time.at = at(9); actions.onMuteFired(1)
        // An edit while it runs re-applies the same level: still one stretch.
        time.at = at(11); actions.update(work, work.copy(work = "Study")); actions.update(work.copy(work = "Study"), work)
        assertEquals(listOf("Work", "Study", "Work"), stretches.rows.map { it.category })
        time.at = at(12); actions.onMuteFired(1)
        assertEquals(3, stretches.rows.size)
    }

    @Test
    fun quietLog_marksTurnedUp_andMissedAccess() = runBlocking {
        val study = alarm(1, 9, 11, "Study")
        saved(study)
        time.at = at(9); actions.onMuteFired(1)
        ringer.turnedUp = true // the user turned the sound back on mid-window
        time.at = at(11); actions.onUnmuteFired(1, MONDAY)
        assertTrue(stretches.rows.single().louder)
        // Without Do Not Disturb access the stretch is logged as missed, not as quiet time.
        stretches.rows.clear(); ringer.turnedUp = false; ringer.allowed = false
        time.at = at(9); actions.onMuteFired(1)
        assertTrue(stretches.rows.single().missed)
    }

    @Test
    fun rescheduleAll_afterAMissedUnmute_catchesUpTheRingerAndTheLog() = runBlocking {
        // Study 09-11 muted at 09:00; the phone was off at 11:00, so its unmute never fired.
        val study = alarm(1, 9, 11, "Study")
        saved(study)
        time.at = at(9); actions.onMuteFired(1)
        ringer.calls.clear()
        time.at = at(13); actions.rescheduleAll()
        assertEquals(listOf("restore"), ringer.calls)
        assertEquals(at(13), local(stretches.rows.single().endMillis!!))
    }

    @Test
    fun rescheduleAll_whenItAgrees_leavesTheRingerAlone() = runBlocking {
        // Mid-window, the user turned the sound up by hand; an app update must not undo that.
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        time.at = at(9); actions.onMuteFired(1)
        ringer.calls.clear()
        time.at = at(12); actions.rescheduleAll()
        assertEquals(emptyList<String>(), ringer.calls)
        assertEquals(null, stretches.rows.single().endMillis)
    }

    @Test
    fun catchUp_afterAccessIsGivenMidWindow_quietsThePhoneAtOnce() = runBlocking {
        // The snoozer started at 09:00 before Do Not Disturb access was given, so its mute
        // couldn't change anything. Access given at 09:04 in Settings, then Home opens.
        val study = alarm(1, 9, 11, "Study")
        saved(study)
        ringer.allowed = false
        time.at = at(9); actions.onMuteFired(1)
        assertTrue(stretches.rows.single().missed)
        ringer.calls.clear() // the fake records the try; the real ringer can't change without access
        ringer.allowed = true
        time.at = at(9, 4); actions.catchUp()
        assertEquals(listOf("Silent"), ringer.calls)
        // The log now has the real quiet time from 09:04, after the missed minutes.
        assertEquals(listOf(true, false), stretches.rows.map { it.missed })
        assertEquals(at(9, 4), local(stretches.rows.last().startMillis))
    }

    @Test
    fun catchUp_withoutAccessOrWhenItAgrees_changesNothing() = runBlocking {
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        ringer.allowed = false
        time.at = at(9); actions.onMuteFired(1)
        time.at = at(10); actions.catchUp() // still no access: nothing to do yet
        ringer.allowed = true
        time.at = at(11); actions.catchUp()
        ringer.calls.clear()
        // Quiet as it should be, then turned up by hand: opening the app again keeps that.
        ringer.turnedUp = true
        time.at = at(12); actions.catchUp()
        assertEquals(emptyList<String>(), ringer.calls)
    }

    @Test
    fun ensureScheduled_resetsAlarmsWipedByAForceStop_andLeavesThemOtherwise() = runBlocking {
        val work = alarm(1, 9, 17, "Work")
        saved(work)
        actions.rescheduleAll()
        scheduler.scheduled.clear(); scheduler.scheduled += 1
        // Nothing missing: nothing re-set.
        assertEquals(false, actions.ensureScheduled())
        // A force stop (or hibernation) wiped them: all set again.
        scheduler.wiped = true; scheduler.scheduled.clear()
        assertEquals(true, actions.ensureScheduled())
        assertEquals(listOf(1), scheduler.scheduled)
    }

    @Test
    fun ensureScheduled_ignoresASnoozerWithNoDays() = runBlocking {
        // Old data can hold one; it has nothing to schedule, so it must not look wiped on every open.
        saved(alarm(2, 9, 17, "Work").copy(daysOfWeek = emptyList()))
        assertEquals(false, actions.ensureScheduled())
    }
}
