package com.example.snoozer.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.snoozer.alarm.Category
import com.example.snoozer.receiver.ReminderNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Which special permissions Snoozer has right now. */
data class Permissions(
    /** Do Not Disturb access: required, without it Snoozer can't change the ringer at all. */
    val dnd: Boolean,
    /** Exact alarms: recommended. Without it the scheduler falls back to inexact alarms. */
    val exactAlarms: Boolean,
    /** Exact alarms only need granting on Android 12+; older versions always allow them. */
    val exactAlarmsApplies: Boolean,
    /** Notifications: optional, only for the reminders. Allowed and not turned off. */
    val notifications: Boolean = true,
    /** Notifications only need granting on Android 13+; older versions allow them by default. */
    val notificationsApply: Boolean = false,
    /** Categories whose own reminder channel is off in Android's settings (Setup can't turn it back on). */
    val reminderChannelsOff: Set<Category> = emptySet(),
    /**
     * Snoozer is exempt from battery optimization ("Unrestricted" on Samsung). Recommended:
     * some phones put apps they think are unused to sleep, which can hold their alarms back.
     */
    val batteryUnrestricted: Boolean = true,
    /**
     * Exempt from Android pausing apps that go unused for months (Android 11+). A paused
     * (hibernated) app is force-stopped, which cancels every alarm, so Snoozer would stop muting.
     */
    val keptWhenUnused: Boolean = true,
    /** That pausing only exists on Android 11+. */
    val keptWhenUnusedApplies: Boolean = false,
)

/** Reads permission state; an interface so ViewModels can be tested with fixed answers. */
interface PermissionChecker {
    fun check(): Permissions
}

class SystemPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionChecker {
    override fun check(): Permissions {
        val policy = context.getSystemService(NotificationManager::class.java)
        val needsExactGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val exact = if (needsExactGrant) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true
        return Permissions(
            dnd = policy.isNotificationPolicyAccessGranted,
            exactAlarms = exact,
            exactAlarmsApplies = needsExactGrant,
            notifications = ReminderNotifier.canNotify(context),
            notificationsApply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            reminderChannelsOff = ReminderNotifier.channelsOff(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            keptWhenUnused = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || context.packageManager.isAutoRevokeWhitelisted,
            keptWhenUnusedApplies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {
    @Binds
    abstract fun bindPermissionChecker(impl: SystemPermissionChecker): PermissionChecker
}
