package com.example.snoozer.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.snoozer.MainActivity
import com.example.snoozer.R
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.ReminderText
import com.example.snoozer.alarm.category

/**
 * Posts the "starts in 5 minutes" reminders. One channel per category, so people can turn
 * either off in Android's settings: Study is a normal notice, Work a quiet one (no sound).
 */
object ReminderNotifier {
    private const val CHANNEL_STUDY = "study_reminders"
    private const val CHANNEL_WORK = "work_reminders"

    /** Whether a reminder can be shown: notifications allowed (Android 13+ asks) and not turned off. */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // canNotify() is checked first; the permission lint can't see that.
    @SuppressLint("MissingPermission")
    fun show(context: Context, alarm: Alarm) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val text = ReminderText.of(alarm)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelFor(alarm.category))
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (alarm.category == Category.Study) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notice per window: a newer reminder for the same window replaces the old one.
        NotificationManagerCompat.from(context).notify(alarm.id, notification)
    }

    private fun channelFor(category: Category) = when (category) {
        Category.Study -> CHANNEL_STUDY
        Category.Work -> CHANNEL_WORK
    }

    /** Creating a channel that exists is a no-op, and keeps any changes the user made to it. */
    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STUDY, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up 5 minutes before a Study quiet time starts."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, "Work reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A quiet notice before a Work quiet time starts."
            }
        )
    }
}
