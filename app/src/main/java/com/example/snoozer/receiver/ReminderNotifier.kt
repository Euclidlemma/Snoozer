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
import com.example.snoozer.alarm.hasQuietReminder
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.util.AndroidStrings

/**
 * Posts the "starts in 5 minutes" reminders. One channel per category, so people can turn
 * either off in Android's settings: Study is a normal notice, Work a quiet one (no sound).
 */
object ReminderNotifier {
    private const val CHANNEL_STUDY = "study_reminders"
    private const val CHANNEL_WORK = "work_reminders"

    /**
     * Whether a reminder would be seen: notifications allowed (Android 13+ asks), not turned off
     * for the app, and at least one of the two reminder channels still on. A channel that
     * doesn't exist yet (nothing posted so far) counts as on, its default.
     */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return Category.entries.any { channelOn(manager, it) }
    }

    /** The categories whose reminder channel the user turned off in Android's settings. */
    fun channelsOff(context: Context): Set<Category> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return Category.entries.filterNot { channelOn(manager, it) }.toSet()
    }

    private fun channelOn(manager: NotificationManager, category: Category): Boolean {
        val channel = manager.getNotificationChannel(channelFor(category))
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Removes a shown reminder for this window (deleted, switched off, edited or started). */
    fun cancel(context: Context, alarmId: Int) {
        NotificationManagerCompat.from(context).cancel(alarmId)
    }

    // canNotify() is checked first; the permission lint can't see that.
    @SuppressLint("MissingPermission")
    fun show(context: Context, alarm: Alarm, minutesUntilStart: Int) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val text = ReminderText.of(alarm, AndroidStrings(context), minutesUntilStart)
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
                if (!alarm.category.hasQuietReminder) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            // Gone by the time quiet time starts (the mute alarm also clears it), so a stale
            // "starts in 5 minutes" never lingers in the shade.
            .setTimeoutAfter(minutesUntilStart * 60_000L)
            .build()
        // One notice per window: a newer reminder for the same window replaces the old one.
        NotificationManagerCompat.from(context).notify(alarm.id, notification)
    }

    private fun channelFor(category: Category) = when (category) {
        Category.Study -> CHANNEL_STUDY
        Category.Work -> CHANNEL_WORK
    }

    /**
     * Creating a channel that exists keeps any changes the user made to it, but takes the new
     * name and description. The app calls this when it starts, so the channels in Android's
     * settings follow a language change.
     */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val res = context.resources
        val lead = AlarmTime.REMINDER_LEAD_MINUTES
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STUDY, res.getString(R.string.channel_study_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = res.getQuantityString(R.plurals.channel_study_description, lead, lead)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WORK, res.getString(R.string.channel_work_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = res.getString(R.string.channel_work_description)
            }
        )
    }
}
