package com.example.snoozer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.snoozer.alarm.AlarmTime
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action")
        val alarmId = intent.getIntExtra("alarmId", -1)
        val dayValue = intent.getIntExtra("dayOfWeek", -1)
        Log.d("AlarmReceiver", "Alarm triggered: action=$action id=$alarmId day=$dayValue")
        if (dayValue !in 1..7 || alarmId == -1) return
        // The start day of the window, even for an overnight window's unmute.
        val day = DayOfWeek.of(dayValue)

        when (action) {
            "MUTE" -> RingerController.mute(context)
            "UNMUTE" -> handleUnmute(context, alarmId, day)
        }
    }

    private fun handleUnmute(context: Context, alarmId: Int, day: DayOfWeek) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        )
        val repository = entryPoint.alarmRepository()
        val scheduler = entryPoint.alarmScheduler()

        // goAsync() keeps the process alive until the database work finishes; a bare
        // coroutine could be killed as soon as onReceive returns.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = repository.getAllAlarmsDirect()

                // Don't unmute while another window is still running (e.g. 9-11 and 10-10:30).
                // That window's own unmute will restore the ringer later.
                val now = LocalDateTime.now()
                val otherActive = alarms.any {
                    it.id != alarmId && it.isEnabled && AlarmTime.isActiveAt(it, now)
                }
                if (otherActive) {
                    Log.d("AlarmReceiver", "Another window is active; staying muted")
                } else {
                    RingerController.restore(context)
                }

                // Reschedule this window for next week.
                val alarm = alarms.firstOrNull { it.id == alarmId }
                if (alarm != null) {
                    scheduler.scheduleAlarmWithDay(alarm, day)
                    Log.d("AlarmReceiver", "Rescheduled alarm $alarmId for next $day")
                } else {
                    Log.w("AlarmReceiver", "Alarm not found for ID: $alarmId")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error handling unmute", e)
            } finally {
                pending.finish()
            }
        }
    }
}
