package com.example.snoozer.alarm

import com.example.snoozer.util.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Changes to saved windows that also have to touch the system: scheduled alarms, the ringer
 * and reminder notices. Shared by every ViewModel and alarm receiver, so the rules live in
 * one place. The system parts come in as interfaces ([WindowScheduler], [Ringer], [Reminders])
 * so these rules are unit-tested with fakes.
 *
 * A singleton with one lock, so two changes (a quick off-then-on toggle, or a toggle while
 * a save is running, or an alarm firing mid-save) run one after the other.
 */
@Singleton
class AlarmActions @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: WindowScheduler,
    private val ringer: Ringer,
    private val reminders: Reminders,
    private val timeSource: TimeSource,
    private val logger: Logger,
    private val quietLog: QuietLog,
) {
    private val lock = Mutex()

    /** Saves a new window and schedules it. */
    suspend fun create(alarm: Alarm) = lock.withLock {
        val id = repository.insertAlarm(alarm)
        val saved = alarm.copy(id = id.toInt())
        scheduler.scheduleAlarm(saved)
        // The scheduler only sets the *next* mute, so a window that has already started
        // (say 09:00-11:00 saved at 10:00) would first mute next week. Quiet the phone now
        // instead; its unmute for today is already scheduled.
        if (isRunning(saved)) applyQuiet()
    }

    /** Replaces [old] with [new] (same id), moving its scheduled alarms to the new times. */
    suspend fun update(old: Alarm, new: Alarm) = lock.withLock {
        // Read before anything changes: was this window already quieting the phone?
        val wasRunning = isRunning(old)
        repository.updateAlarm(new)
        scheduler.cancelAlarm(old)
        scheduler.scheduleAlarm(new)
        reminders.cancel(old.id) // a shown notice may describe the old times
        val nowRunning = isRunning(new)
        // Touch the ringer when the edit starts or ends the running state (a start moved
        // earlier quiets now; an end moved earlier hands over, since its pending unmute was
        // just cancelled), or when it keeps running but its level changes (Work -> Study goes
        // silent at once). Any other edit of a running window leaves the ringer alone: the
        // user may have unmuted on purpose mid-window.
        val levelChanged = old.category.quietLevel != new.category.quietLevel
        if (wasRunning != nowRunning || (nowRunning && levelChanged)) applyQuiet()
    }

    /**
     * Saves several windows at once (a timetable import) and schedules them. The ringer is
     * decided once at the end, so a class running right now quiets the phone straight away.
     * Returns how many were saved.
     */
    suspend fun createAll(alarms: List<Alarm>): Int = lock.withLock {
        var anyRunning = false
        alarms.forEach { alarm ->
            val saved = alarm.copy(id = repository.insertAlarm(alarm).toInt())
            scheduler.scheduleAlarm(saved)
            if (isRunning(saved)) anyRunning = true
        }
        if (anyRunning) applyQuiet()
        alarms.size
    }

    /** Deletes several windows at once (ended classes), handing the ringer over once at the end. */
    suspend fun deleteAll(alarms: List<Alarm>) = lock.withLock {
        var anyRunning = false
        alarms.forEach { alarm ->
            if (isRunning(alarm)) anyRunning = true
            repository.deleteAlarm(alarm)
            scheduler.cancelAlarm(alarm)
            reminders.cancel(alarm.id)
        }
        if (anyRunning) applyQuiet()
    }

    suspend fun delete(alarm: Alarm) = lock.withLock {
        repository.deleteAlarm(alarm)
        scheduler.cancelAlarm(alarm)
        reminders.cancel(alarm.id)
        // Its pending unmute is gone, so hand over (or give the ringer back) right away.
        if (isRunning(alarm)) applyQuiet()
    }

    /**
     * Switches a window on or off without deleting it. Reads the saved row first, so a
     * second tap always starts from what the first one wrote. Switching on quiets the phone
     * if the window is running; switching off hands over or gives the ringer back.
     */
    suspend fun setEnabled(id: Int, enabled: Boolean) = lock.withLock {
        val alarm = repository.getAlarmById(id) ?: return@withLock
        if (alarm.isEnabled == enabled) return@withLock
        val updated = alarm.copy(isEnabled = enabled)
        repository.updateAlarm(updated)
        if (enabled) {
            scheduler.scheduleAlarm(updated)
        } else {
            scheduler.cancelAlarm(alarm)
            reminders.cancel(alarm.id)
        }
        // Running now, counting the window as switched on (whichever way it is going).
        if (isRunning(alarm.copy(isEnabled = true))) applyQuiet()
    }

    // Alarm broadcasts. They take the same lock as the edits above, so a mute or unmute that
    // fires while the user is saving or deleting sees the finished change, never half of it.

    /**
     * A window's reminder fired for the occurrence starting on [startDay]. Shown only if the
     * window is still saved and on, a notice would be seen, Snoozer can actually quiet the
     * phone, and it isn't late: reminders don't use the idle-time alarm budget (that is kept
     * for the mutes), so in deep idle one can arrive after the start, and then it's dropped.
     * The next week's reminder is set when this occurrence's unmute reschedules the window.
     */
    suspend fun onReminderFired(alarmId: Int, startDay: DayOfWeek) = lock.withLock {
        val alarm = repository.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) return@withLock
        if (!reminders.canShow() || !ringer.canChange()) return@withLock
        val minutes = AlarmTime.minutesUntilStart(alarm, startDay, timeSource.now())
        if (minutes !in 1..AlarmTime.REMINDER_EARLIEST_MINUTES + 1) {
            logger.warn("Reminder for $alarmId arrived $minutes min before its start; dropped")
            return@withLock
        }
        reminders.show(alarm, minutes)
    }

    /** A window's mute alarm fired. Quiets the phone only if that window is still saved and on. */
    suspend fun onMuteFired(alarmId: Int) = lock.withLock {
        val alarm = repository.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) {
            // A leftover alarm for a deleted or switched-off window: don't silence the phone.
            logger.warn("Mute for missing or switched-off snoozer $alarmId ignored")
            return@withLock
        }
        reminders.cancel(alarmId) // "starts in 5 minutes" is out of date once it has started
        applyQuiet()
    }

    /**
     * A window's unmute alarm fired for the occurrence that started on [day]. Hands over to
     * any window still running (Study ends, Work continues: silent becomes vibrate) or gives
     * the ringer back, then sets this window up for next week.
     */
    suspend fun onUnmuteFired(alarmId: Int, day: DayOfWeek) = lock.withLock {
        // Leave the ending window out: it is ending now, whatever the clock's last second says.
        applyQuiet(excludingId = alarmId)
        // Read under the lock, so a window deleted a moment ago isn't brought back.
        val alarm = repository.getAlarmById(alarmId)
        if (alarm != null) scheduler.scheduleAlarmWithDay(alarm, day)
        else logger.warn("Unmute for missing snoozer $alarmId; not rescheduled")
    }

    /**
     * Re-creates every window's alarms (after a reboot, an update or a clock change).
     */
    suspend fun rescheduleAll() = lock.withLock {
        repository.getAllAlarmsDirect().forEach { scheduler.scheduleAlarm(it) }
        // Alarms that should have fired meanwhile (the phone was off, or the clock jumped) never
        // will. If what runs now differs from what Snoozer last set (per the quiet log), catch the
        // ringer and the log up. When they agree, leave the ringer alone: the user may have
        // turned it up on purpose, and an update or clock change shouldn't undo that.
        val running = AlarmTime.quietLevelAt(repository.getAllAlarmsDirect(), timeSource.now())
        val logged = try {
            quietLog.open()?.categoryValue?.quietLevel
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            running // can't tell: change nothing
        }
        if (running != logged) applyQuiet()
    }

    private fun isRunning(alarm: Alarm): Boolean =
        alarm.isEnabled && AlarmTime.isActiveAt(alarm, timeSource.now())

    /**
     * The one place that decides the ringer: the quietest level among the saved windows
     * running now ([excludingId] aside), or the user's own mode back when none is. Called
     * under [lock] by every change above, so they all follow the same rules.
     */
    private suspend fun applyQuiet(excludingId: Int? = null) {
        val now = timeSource.now()
        val level = AlarmTime.quietLevelAt(repository.getAllAlarmsDirect(), now, excludingId)
        // Before touching the ringer: was the stretch now ending turned up by hand meanwhile?
        val louder = try {
            quietLog.open()?.takeIf { !it.missed }?.let { ringer.isLouderThan(it.categoryValue.quietLevel) } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        val canChange = ringer.canChange()
        if (level == null) ringer.restore() else ringer.quiet(level)
        // The weekly report's record. A failed write never stops the ringer change above.
        val at = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (level == null) quietLog.stop(at, louder)
            else quietLog.quiet(Category.forLevel(level), missed = !canChange, at = at, louder = louder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Quiet log not written: ${e.message}")
        }
    }
}
