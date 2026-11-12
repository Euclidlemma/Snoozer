package com.example.snoozer.receiver

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Mutes the ringer and later restores whatever mode the user had before (normal or
 * vibrate), instead of always forcing normal. The pre-mute mode is stored once, when
 * the first of possibly several overlapping windows starts, and cleared on restore.
 */
object RingerController {
    private const val TAG = "RingerController"
    private const val PREFS = "ringer_state"
    private const val KEY_SAVED_MODE = "saved_mode"

    fun mute(context: Context) {
        if (!hasDndAccess(context)) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SAVED_MODE)) {
            prefs.edit().putInt(KEY_SAVED_MODE, audio.ringerMode).apply()
        }
        setMode(audio, AudioManager.RINGER_MODE_SILENT)
    }

    /**
     * Restores the pre-mute ringer mode. Does nothing if Snoozer didn't mute the phone
     * (no saved mode), so a phone the user silenced by hand is left alone.
     */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SAVED_MODE)) return
        if (!hasDndAccess(context)) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = prefs.getInt(KEY_SAVED_MODE, AudioManager.RINGER_MODE_NORMAL)
        prefs.edit().remove(KEY_SAVED_MODE).apply()
        setMode(audio, mode)
    }

    // Changing to/from silent needs Do Not Disturb access, which the user can revoke at
    // any time. Without it AudioManager throws SecurityException.
    private fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "Do Not Disturb access not granted; leaving ringer unchanged")
            return false
        }
        return true
    }

    private fun setMode(audio: AudioManager, mode: Int) {
        try {
            audio.ringerMode = mode
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not change ringer mode", e)
        }
    }
}
