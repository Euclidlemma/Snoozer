package com.example.snoozer.alarm

import android.content.Context
import com.example.snoozer.receiver.RingerController
import com.example.snoozer.scheduler.AlarmScheduler
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        muteIfActive(saved)
    }

    /** Replaces [old] with [new] (same id), moving its scheduled alarms to the new times. */
    suspend fun update(old: Alarm, new: Alarm) = lock.withLock {
        repository.updateAlarm(new)
        scheduler.cancelAlarm(old)
        scheduler.scheduleAlarm(new)
        releaseRingerIfIdle(old)
        muteIfActive(new)
    }

    suspend fun delete(alarm: Alarm) = lock.withLock {
        repository.deleteAlarm(alarm)
        scheduler.cancelAlarm(alarm)
        releaseRingerIfIdle(alarm)
    }

    /**
     * Switches a window on or off without deleting it. Reads the saved row first, so a
     * second tap always starts from what the first one wrote. Switching off cancels its
     * alarms and, if it is muting the phone right now, gives the ringer back.
     */
    suspend fun setEnabled(id: Int, enabled: Boolean) = lock.withLock {
        val alarm = repository.getAlarmById(id) ?: return@withLock
        if (alarm.isEnabled == enabled) return@withLock
        val updated = alarm.copy(isEnabled = enabled)
        repository.updateAlarm(updated)
        if (enabled) {
            scheduler.scheduleAlarm(updated)
            muteIfActive(updated)
        } else {
            scheduler.cancelAlarm(alarm)
            releaseRingerIfIdle(alarm)
        }
    }

    /**
     * The scheduler only sets the *next* mute, so a window that has already started (say
     * 09:00-11:00 saved at 10:00) would first mute next week. Mute now instead; its unmute
     * for today is already scheduled. Does nothing for a switched-off window.
     */
    private fun muteIfActive(alarm: Alarm) {
        if (alarm.isEnabled && AlarmTime.isActiveAt(alarm, timeSource.now())) {
            RingerController.mute(appContext)
        }
    }

    /**
     * Deleting, editing or switching off a window while it is muting the phone cancels its
     * pending unmute, which would leave the phone silent. If [changed] (the old version) was
     * active and no saved, enabled window still covers now, restore the ringer right away.
     */
    private suspend fun releaseRingerIfIdle(changed: Alarm) {
        val now = timeSource.now()
        if (!AlarmTime.isActiveAt(changed, now)) return
        val stillActive = repository.getAllAlarmsDirect().any {
            it.isEnabled && AlarmTime.isActiveAt(it, now)
        }
        if (!stillActive) RingerController.restore(appContext)
    }
}
