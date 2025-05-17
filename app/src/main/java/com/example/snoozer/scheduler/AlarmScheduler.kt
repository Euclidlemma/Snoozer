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
import com.example.snoozer.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.util.Calendar
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
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, if (day.value == 7) 1 else day.value + 1)
            set(Calendar.HOUR_OF_DAY, if (isStart) alarm.startHour else alarm.endHour)
            set(Calendar.MINUTE, if (isStart) alarm.startMinute else alarm.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(Calendar.getInstance())) add(Calendar.WEEK_OF_YEAR, 1)
        }

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
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
        Log.d("Generated reqcode in schedule", requestCode.toString())
        Log.d("AlarmScheduler", "Scheduling alarm for: $action at ${calendar.time}")

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

