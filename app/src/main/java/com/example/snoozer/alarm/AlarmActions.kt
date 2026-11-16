package com.example.snoozer.alarm

import android.content.Context
import com.example.snoozer.receiver.RingerController
import com.example.snoozer.scheduler.AlarmScheduler
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Changes to saved windows that also have to touch the system: scheduled alarms and the
 * ringer. Shared by every ViewModel that edits alarms, so the rules live in one place.
 */
class AlarmActions @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val timeSource: TimeSource,
    @ApplicationContext private val appContext: Context,
) {
    /**
     * Switches a window on or off without deleting it. Switching off cancels its alarms and,
     * if it is muting the phone right now, gives the ringer back.
     */
    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) {
        if (alarm.isEnabled == enabled) return
        val updated = alarm.copy(isEnabled = enabled)
        repository.updateAlarm(updated)
        if (enabled) {
            scheduler.scheduleAlarm(updated)
        } else {
            scheduler.cancelAlarm(alarm)
            releaseRingerIfIdle(alarm)
        }
    }

    /**
     * Deleting, editing or switching off a window while it is muting the phone cancels its
     * pending unmute, which would leave the phone silent. If [changed] (the old version) was
     * active and no saved, enabled window still covers now, restore the ringer right away.
     */
    suspend fun releaseRingerIfIdle(changed: Alarm) {
        val now = timeSource.now()
        if (!AlarmTime.isActiveAt(changed, now)) return
        val stillActive = repository.getAllAlarmsDirect().any {
            it.isEnabled && AlarmTime.isActiveAt(it, now)
        }
        if (!stillActive) RingerController.restore(appContext)
    }
}
