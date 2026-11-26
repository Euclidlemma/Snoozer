package com.example.snoozer.alarm

import java.time.DayOfWeek

// The parts of the phone AlarmActions touches, as interfaces, so its rules can be unit-tested
// with fakes. The real implementations (AlarmScheduler, SystemRinger, SystemReminders) are
// bound in SystemHooksModule.

/** Sets and clears the system alarms for a window (reminder, mute, unmute per day). */
interface WindowScheduler {
    fun scheduleAlarm(alarm: Alarm)
    fun scheduleAlarmWithDay(alarm: Alarm, day: DayOfWeek)
    fun cancelAlarm(alarm: Alarm)
}

/** The phone's ringer. */
interface Ringer {
    /** Quiets the phone to [level], never louder than the user's own mode. */
    fun quiet(level: QuietLevel)

    /** Gives back the mode the user had before Snoozer stepped in (if Snoozer changed it). */
    fun restore()

    /** Whether Snoozer may change the ringer at all (Do Not Disturb access). */
    fun canChange(): Boolean

    /**
     * Whether the ringer is louder now than Snoozer set it for [level]: someone turned the
     * sound back up by hand. False when Snoozer hasn't changed the ringer.
     */
    fun isLouderThan(level: QuietLevel): Boolean
}

/** The "starts in 5 minutes" notices. */
interface Reminders {
    /** Whether a reminder would actually be seen: notifications allowed and a channel on. */
    fun canShow(): Boolean

    /** Shows the reminder for [alarm], which starts in [minutesUntilStart] minutes. */
    fun show(alarm: Alarm, minutesUntilStart: Int)

    /** Removes a shown reminder for this window, if there is one. */
    fun cancel(alarmId: Int)
}

/** Diagnostics, so AlarmActions doesn't call android.util.Log directly (not available in unit tests). */
fun interface Logger {
    fun warn(message: String)
}
