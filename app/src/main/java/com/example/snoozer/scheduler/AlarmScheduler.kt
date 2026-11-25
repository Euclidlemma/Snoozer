package com.example.snoozer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.WindowScheduler
import com.example.snoozer.receiver.ReminderNotifier
import com.example.snoozer.receiver.AlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Sets the system alarms for each window: per selected day, a reminder shortly before the
 * start, the mute at the start and the unmute at the end. Each is a separate PendingIntent
 * (request code from id, day and kind), so they can be replaced or cancelled one by one.
 */
class AlarmScheduler @Inject constructor(@ApplicationContext private val context: Context) : WindowScheduler {

    /** The three alarms a window's day gets. The name is the receiver's "action" extra. */
    enum class Kind { REMIND, MUTE, UNMUTE }

    /**
     * Schedules the reminder, mute and unmute for every selected day. Switched-off alarms are
     * skipped here, so callers (boot, weekly reschedule, save) never revive one by accident.
     */
    override fun scheduleAlarm(alarm: Alarm) {
        if (!alarm.isEnabled) return
        alarm.daysOfWeek.forEach { scheduleAlarmWithDay(alarm, it) }
    }

    /** Schedules the next reminder, mute and unmute of the window starting on [day]. */
    override fun scheduleAlarmWithDay(alarm: Alarm, day: DayOfWeek) {
        if (!alarm.isEnabled) return
        // No reminder when nobody would see it: notifications off, or both channels off.
        // AlarmActions.rescheduleAll() adds them once notifications are allowed.
        if (ReminderNotifier.canNotify(context)) schedule(alarm, day, Kind.REMIND) else cancel(alarm, day, Kind.REMIND)
        schedule(alarm, day, Kind.MUTE)
        schedule(alarm, day, Kind.UNMUTE)
    }

    override fun cancelAlarm(alarm: Alarm) {
        for (day in alarm.daysOfWeek) {
            Kind.entries.forEach { cancel(alarm, day, it) }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(alarm: Alarm, day: DayOfWeek, kind: Kind) {
        // `day` is always the window's start day. An overnight window unmutes the day after,
        // and a window starting just after midnight reminds the day before.
        val reminder = AlarmTime.reminderTime(alarm, day)
        val trigger = when (kind) {
            Kind.REMIND -> AlarmTime.nextTrigger(reminder.day, reminder.hour, reminder.minute, ZonedDateTime.now())
            Kind.MUTE -> AlarmTime.nextTrigger(day, alarm.startHour, alarm.startMinute, ZonedDateTime.now())
            Kind.UNMUTE -> AlarmTime.nextTrigger(AlarmTime.endDay(alarm, day), alarm.endHour, alarm.endMinute, ZonedDateTime.now())
        }
        val triggerMillis = trigger.toInstant().toEpochMilli()
        val pendingIntent = pendingIntent(alarm, day, kind)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // On Android 12+ the user can deny or revoke exact alarms (denied by default on 14+).
        // Calling setExact* without it throws SecurityException, so fall back to an inexact
        // alarm instead of crashing. BootReceiver reschedules exactly once it is granted.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (kind == Kind.REMIND) {
            // Reminders never use the "allow while idle" alarms. Android rations those per app
            // while the phone is idle (inexact ones about once every 9 minutes), and a 08:55
            // reminder would then hold back the 09:00 mute. In deep idle a reminder may come
            // late instead; AlarmActions drops late ones.
            try {
                if (canExact) alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                else alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            return
        }
        try {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not schedule exact alarm", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun cancel(alarm: Alarm, day: DayOfWeek, kind: Kind) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(alarm, day, kind))
    }

    /**
     * The same request code and target give the same PendingIntent for scheduling and
     * cancelling; extras don't affect matching, and FLAG_UPDATE_CURRENT refreshes them.
     */
    private fun pendingIntent(alarm: Alarm, day: DayOfWeek, kind: Kind): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("action", kind.name)
            putExtra("alarmId", alarm.id)
            putExtra("dayOfWeek", day.value)
        }
        return PendingIntent.getBroadcast(
            context,
            "${alarm.id}_${day.value}_${kind.name}".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
