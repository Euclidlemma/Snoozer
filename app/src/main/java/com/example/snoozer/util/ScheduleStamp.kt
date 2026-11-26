package com.example.snoozer.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Remembers that this install has set up its system alarms at least once. Kept in its own
 * file, schedule_state.xml, which the backup rules leave out: snoozers restored from a backup
 * arrive with no alarms and no stamp, so the first open after a restore re-creates them all.
 * Normal opens skip that work (boot, updates and clock changes have their own reschedule).
 */
class ScheduleStamp @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun needsFullReschedule(): Boolean = !prefs.getBoolean(KEY_DONE, false)

    fun markDone() {
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private companion object {
        const val PREFS = "schedule_state" // schedule_state.xml, excluded from backup
        const val KEY_DONE = "scheduled"
    }
}
