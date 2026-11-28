package com.example.snoozer.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.snoozer.alarm.AlarmActions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReceiverEntryPoint {
    /** The one shared instance (a singleton), so receivers and screens use the same lock. */
    fun alarmActions(): AlarmActions
}


/**
 * Reschedules every saved alarm when the system may have cleared or shifted them:
 * reboot, app update, clock/time-zone change, or exact-alarm permission granted.
 * Rescheduling is idempotent: the same request codes replace existing PendingIntents.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        // In a foreground service, so a catch-up of the ringer isn't ignored on Android 17.
        if (QuietService.start(context, QuietService.Job.Reschedule)) return

        val actions = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        ).alarmActions()

        // goAsync() keeps the process alive until the database read finishes.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                actions.rescheduleAll()
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error rescheduling alarms after ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
