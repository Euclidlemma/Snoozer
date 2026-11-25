package com.example.snoozer.alarm

import android.media.AudioManager

/**
 * How quiet a running window makes the phone. Declared from loudest to quietest, so a
 * later entry always wins when windows overlap ([quietest]).
 *
 * [ringerMode] is the AudioManager mode it sets. These are compile-time constants (vibrate
 * is 1, silent is 0), so this file stays usable in plain unit tests.
 */
enum class QuietLevel(val label: String, val ringerMode: Int) {
    Vibrate("Vibrate", AudioManager.RINGER_MODE_VIBRATE),
    Silent("Silent", AudioManager.RINGER_MODE_SILENT);

    /**
     * The ringer mode to set, given the mode the user had before Snoozer stepped in. Never
     * louder than the user's own choice: a phone silenced by hand stays silent during a
     * Vibrate window. Ringer modes order silent (0) < vibrate (1) < normal (2).
     */
    fun ringerModeFor(userMode: Int): Int = minOf(ringerMode, userMode)

    companion object {
        /** The quietest of [levels], or null when there are none (nothing is running). */
        fun quietest(levels: Iterable<QuietLevel>): QuietLevel? = levels.maxOrNull()
    }
}

/** Work keeps calls noticeable on vibrate; Study goes fully silent. */
val Category.quietLevel: QuietLevel
    get() = when (this) {
        Category.Work -> QuietLevel.Vibrate
        Category.Study -> QuietLevel.Silent
    }
