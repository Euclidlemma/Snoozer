package com.example.snoozer.util

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Logger
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.Reminders
import com.example.snoozer.alarm.Ringer
import com.example.snoozer.alarm.WindowScheduler
import com.example.snoozer.receiver.ReminderNotifier
import com.example.snoozer.receiver.RingerController
import com.example.snoozer.scheduler.AlarmScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class SystemRinger @Inject constructor(@ApplicationContext private val context: Context) : Ringer {
    override fun quiet(level: QuietLevel) = RingerController.mute(context, level)
    override fun restore() = RingerController.restore(context)
    override fun canChange(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
}

class SystemReminders @Inject constructor(@ApplicationContext private val context: Context) : Reminders {
    override fun canShow(): Boolean = ReminderNotifier.canNotify(context)
    override fun show(alarm: Alarm, minutesUntilStart: Int) = ReminderNotifier.show(context, alarm, minutesUntilStart)
    override fun cancel(alarmId: Int) = ReminderNotifier.cancel(context, alarmId)
}

class AndroidLogger @Inject constructor() : Logger {
    override fun warn(message: String) {
        Log.w("AlarmActions", message)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SystemHooksModule {
    @Binds abstract fun bindScheduler(impl: AlarmScheduler): WindowScheduler
    @Binds abstract fun bindRinger(impl: SystemRinger): Ringer
    @Binds abstract fun bindReminders(impl: SystemReminders): Reminders
    @Binds abstract fun bindLogger(impl: AndroidLogger): Logger
}
