package com.example.snoozer.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Opens the system screen where the user grants Snoozer Do Not Disturb access. */
fun Context.openDndSettings() = openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

/** Opens Snoozer's own exact-alarm toggle (Android 12+; older versions always allow it). */
fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // The package URI opens Snoozer's own toggle instead of the full app list.
        openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }
}

/** Some devices strip these Settings screens; fall back to Snoozer's app info page. */
private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}

/** Opens Snoozer's notification settings, where a permanently refused permission can be allowed. */
fun Context.openNotificationSettings() = openSettings(
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
)

/** What tapping Allow on the notifications row should do. */
enum class NotificationAsk { Ask, OpenSettings }

/**
 * Android shows the notification permission dialog at most twice; after a second "Don't allow"
 * (or "Don't ask again") a request returns at once with no dialog. So ask while a dialog can
 * still appear, and send the user to Settings otherwise. Also Settings when the permission is
 * granted but notifications were switched off there.
 */
fun notificationAsk(permissionGranted: Boolean, askedBefore: Boolean, showRationale: Boolean): NotificationAsk =
    when {
        permissionGranted -> NotificationAsk.OpenSettings
        !askedBefore || showRationale -> NotificationAsk.Ask
        else -> NotificationAsk.OpenSettings
    }
