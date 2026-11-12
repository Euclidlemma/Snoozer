package com.example.snoozer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZonedDateTime
import javax.inject.Inject

class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleAlarm(alarm: Alarm) {
        for (day in alarm.daysOfWeek) {
            scheduleExactAlarm(alarm, day, true)  // Start time (mute)
            scheduleExactAlarm(alarm, day, false) // End time (unmute)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleAlarmWithDay(alarm:Alarm, day:DayOfWeek) {
        scheduleExactAlarm(alarm, day, true)
        scheduleExactAlarm(alarm, day, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun cancelAlarm(alarm: Alarm) {
        for (day in alarm.daysOfWeek) {
            cancelExactAlarm(alarm, day, true)  // Cancel mute
            cancelExactAlarm(alarm, day, false) // Cancel unmute
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun scheduleExactAlarm(alarm: Alarm, day: DayOfWeek, isStart: Boolean) {
        Log.d("Alarm Scheduler daycheck", day.value.toString())
        // `day` is always the window's start day; an overnight window unmutes the day after.
        val trigger = AlarmTime.nextTrigger(
            day = if (isStart) day else AlarmTime.endDay(alarm, day),
            hour = if (isStart) alarm.startHour else alarm.endHour,
            minute = if (isStart) alarm.startMinute else alarm.endMinute,
            now = ZonedDateTime.now()
        )
        val triggerMillis = trigger.toInstant().toEpochMilli()

        val action = if (isStart) "MUTE" else "UNMUTE"
        val requestCode = generateRequestCode(alarm.id, day, action)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("action", action)
            putExtra("alarmId", alarm.id)
            putExtra("dayOfWeek", day.value)
        }

        Log.d("ALARM ID", alarm.id.toString())
        Log.d(" Day Val", day.value.toString())

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // On Android 12+ the user can deny or revoke exact alarms (denied by default on 14+).
        // Calling setExact* without it throws SecurityException, so fall back to an inexact
        // alarm instead of crashing. BootReceiver reschedules exactly once it is granted.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                Log.w("AlarmScheduler", "Exact alarm permission missing; scheduling inexact alarm")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Could not schedule exact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
        Log.d("Generated reqcode in schedule", requestCode.toString())
        Log.d("AlarmScheduler", "Scheduling alarm for: $action at $trigger")

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun cancelExactAlarm(alarm: Alarm, day: DayOfWeek, isStart: Boolean) {
        val action = if (isStart) "MUTE" else "UNMUTE"
        val requestCode = generateRequestCode(alarm.id, day, action)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("action", action)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        Log.d("Generated req code in cancel", requestCode.toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun generateRequestCode(id: Int, day: DayOfWeek, action: String): Int {
        return "${id}_${day.value}_$action".hashCode()
    }
}

