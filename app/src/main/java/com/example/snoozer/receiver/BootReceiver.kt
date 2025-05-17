package com.example.snoozer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.snoozer.alarm.AlarmDatabase
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.scheduler.AlarmScheduler
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
    fun alarmRepository(): AlarmRepository
    fun alarmScheduler(): AlarmScheduler
}


class BootReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule all alarms here
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ReceiverEntryPoint::class.java
            )
            val repository = entryPoint.alarmRepository()
            val scheduler = entryPoint.alarmScheduler()

            // Reschedule alarms
            CoroutineScope(Dispatchers.IO).launch {
                val alarms = repository.getAllAlarmsDirect()
                alarms.forEach {
                    scheduler.scheduleAlarm(it)
                }
            }
        }
    }
}
