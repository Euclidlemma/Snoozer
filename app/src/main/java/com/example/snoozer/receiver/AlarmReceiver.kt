package com.example.snoozer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class AlarmReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm triggered with action: ${intent.getStringExtra("action")}")
        val action = intent.getStringExtra("action")
        val alarmId = intent.getIntExtra("alarmId", -1)
        val dayValue = intent.getIntExtra("dayOfWeek", -1)
        Log.d("alarm id in receiver", alarmId.toString())
        Log.d("dayval", dayValue.toString())
        if (dayValue == -1 || alarmId == -1) return
        val day = DayOfWeek.of(dayValue)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager


        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ReceiverEntryPoint::class.java
        )
        val repository = entryPoint.alarmRepository()
        val scheduler = entryPoint.alarmScheduler()


        when (action) {
            "MUTE" -> audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            "UNMUTE" -> audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }

        // Reschedule this alarm for next week
        if (action == "UNMUTE") {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarm = repository.getAlarmById(alarmId)
                    if (alarm != null) {
                        scheduler.scheduleAlarmWithDay(alarm, day)
                        Log.d("AlarmReceiver", "Rescheduled alarm $alarmId for next $day")
                    } else {
                        Log.w("AlarmReceiver", "Alarm not found for ID: $alarmId")
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error rescheduling alarm: ${e.message}")
                }
            }
        }
    }
}
