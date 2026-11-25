package com.example.snoozer.alarm

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.example.snoozer.receiver.ReminderNotifier
import com.example.snoozer.receiver.RingerController
import com.example.snoozer.scheduler.AlarmScheduler
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Changes to saved windows that also have to touch the system: scheduled alarms and the
 * ringer. Shared by every ViewModel that edits alarms, so the rules live in one place.
 *
 * A singleton with one lock, so two changes (a quick off-then-on toggle, or a toggle while
 * a save is running) run one after the other instead of mixing their reads and writes.
 */
@Singleton
class AlarmActions @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val timeSource: TimeSource,
    @ApplicationContext private val appContext: Context,
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
        // Touch the ringer only when the edit starts or ends the running state: a start moved
        // earlier quiets now; an end moved earlier hands over to whatever else is running (or
        // gives the ringer back, since its pending unmute was just cancelled). If it was running
        // and still is, leave the ringer alone: the user may have unmuted on purpose mid-window,
        // and editing the category or end time shouldn't override that.
        if (wasRunning != isRunning(new)) applyQuiet()
    }

    suspend fun delete(alarm: Alarm) = lock.withLock {
        repository.deleteAlarm(alarm)
        scheduler.cancelAlarm(alarm)
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
        if (enabled) scheduler.scheduleAlarm(updated) else scheduler.cancelAlarm(alarm)
        // Running now, counting the window as switched on (whichever way it is going).
        if (isRunning(alarm.copy(isEnabled = true))) applyQuiet()
    }

    // Alarm broadcasts. They take the same lock as the edits above, so a mute or unmute that
    // fires while the user is saving or deleting sees the finished change, never half of it.

    /**
     * A window's reminder fired. Shown only if the window is still saved and on, notifications
     * are allowed, and Snoozer can actually quiet the phone (otherwise "your phone goes
     * silent" would be wrong). The next week's reminder is set when this occurrence's unmute
     * reschedules the window.
     */
    suspend fun onReminderFired(alarmId: Int) = lock.withLock {
        val alarm = repository.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) return@withLock
        val policy = appContext.getSystemService(NotificationManager::class.java)
        if (!policy.isNotificationPolicyAccessGranted) return@withLock
        ReminderNotifier.show(appContext, alarm)
    }

    /** A window's mute alarm fired. Quiets the phone only if that window is still saved and on. */
    suspend fun onMuteFired(alarmId: Int) = lock.withLock {
        val alarm = repository.getAlarmById(alarmId)
        if (alarm == null || !alarm.isEnabled) {
            // A leftover alarm for a deleted or switched-off window: don't silence the phone.
            Log.w(TAG, "Mute for missing or switched-off snoozer $alarmId ignored")
            return@withLock
        }
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
        else Log.w(TAG, "Unmute for missing snoozer $alarmId; not rescheduled")
    }

    /** Re-creates every window's alarms (after a reboot, an update or a clock change). */
    suspend fun rescheduleAll() = lock.withLock {
        repository.getAllAlarmsDirect().forEach { scheduler.scheduleAlarm(it) }
    }

    private fun isRunning(alarm: Alarm): Boolean =
        alarm.isEnabled && AlarmTime.isActiveAt(alarm, timeSource.now())

    /**
     * The one place that decides the ringer: the quietest level among the saved windows
     * running now ([excludingId] aside), or the user's own mode back when none is. Called
     * under [lock] by every change above, so they all follow the same rules.
     */
    private suspend fun applyQuiet(excludingId: Int? = null) {
        val level = AlarmTime.quietLevelAt(repository.getAllAlarmsDirect(), timeSource.now(), excludingId)
        if (level == null) RingerController.restore(appContext) else RingerController.mute(appContext, level)
    }

    private companion object {
        const val TAG = "AlarmActions"
    }
}
