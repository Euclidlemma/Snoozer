package com.example.snoozer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Receives a window's mute and unmute alarms. The work itself lives in AlarmActions, under the
 * same lock as the app's edits, so an alarm firing mid-save or mid-delete can't act on old data.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action")
        val alarmId = intent.getIntExtra("alarmId", -1)
        val dayValue = intent.getIntExtra("dayOfWeek", -1)
        Log.d("AlarmReceiver", "Alarm triggered: action=$action id=$alarmId day=$dayValue")
        if (dayValue !in 1..7 || alarmId == -1) return
        if (action != "REMIND" && action != "MUTE" && action != "UNMUTE") return
        // The start day of the window, even for an overnight window's unmute.
        val day = DayOfWeek.of(dayValue)

        val actions = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        ).alarmActions()

        // goAsync() keeps the process alive until the database work finishes; a bare
        // coroutine could be killed as soon as onReceive returns.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "REMIND" -> actions.onReminderFired(alarmId)
                    "MUTE" -> actions.onMuteFired(alarmId)
                    else -> actions.onUnmuteFired(alarmId, day)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error handling $action for $alarmId", e)
            } finally {
                pending.finish()
            }
        }
    }
}
