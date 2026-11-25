package com.example.snoozer.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
)

/** Reads permission state; an interface so ViewModels can be tested with fixed answers. */
interface PermissionChecker {
    fun check(): Permissions
}

class SystemPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionChecker {
    override fun check(): Permissions {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val needsExactGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val exact = if (needsExactGrant) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else true
        return Permissions(
            dnd = notifications.isNotificationPolicyAccessGranted,
            exactAlarms = exact,
            exactAlarmsApplies = needsExactGrant,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {
    @Binds
    abstract fun bindPermissionChecker(impl: SystemPermissionChecker): PermissionChecker
}
