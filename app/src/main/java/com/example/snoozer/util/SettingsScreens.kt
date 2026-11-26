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

/**
 * Opens Snoozer's app info page, where the battery setting lives (Battery, then Unrestricted on
 * Samsung). Android has no public screen for just this app's battery setting, and asking for the
 * exemption directly (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) is restricted on Google Play.
 */
fun Context.openBatterySettings() =
    openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

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
 * Ask with Android's dialog unless only Settings can help: the permission was refused for
 * good ([refusedForGood]), or it is granted but notifications were switched off in Settings.
 */
fun notificationAsk(permissionGranted: Boolean, refusedForGood: Boolean): NotificationAsk =
    if (permissionGranted || refusedForGood) NotificationAsk.OpenSettings else NotificationAsk.Ask

/**
 * Whether a "no" from the dialog was final. Android shows the dialog until the second refusal;
 * after the first, the rationale flag turns on, and after the second it turns off again. Closing
 * the dialog without choosing (back, tapping outside) also reports "no" but leaves the flag as
 * it was, so it isn't mistaken for a final refusal.
 */
fun refusedForGood(granted: Boolean, rationaleBefore: Boolean, rationaleAfter: Boolean): Boolean =
    !granted && rationaleBefore && !rationaleAfter
