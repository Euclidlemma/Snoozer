package com.example.snoozer.alarm

import android.media.AudioManager
import androidx.annotation.StringRes
import com.example.snoozer.R

/**
 * How quiet a running window makes the phone. Declared from loudest to quietest, so a
 * later entry always wins when windows overlap ([quietest]).
 *
 * [ringerMode] is the AudioManager mode it sets. These are compile-time constants (vibrate
 * is 1, silent is 0), so this file stays usable in plain unit tests.
 *
 * [labelRes] and [descriptionRes] are the words in the person's language; [label] and
 * [description] are the English ones, still used by screens that haven't moved over yet.
 */
enum class QuietLevel(
    val label: String,
    val description: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val ringerMode: Int,
) {
    Vibrate("Vibrate", "Phone on vibrate", R.string.level_vibrate, R.string.level_vibrate_description, AudioManager.RINGER_MODE_VIBRATE),
    Silent("Silent", "Phone fully silent", R.string.level_silent, R.string.level_silent_description, AudioManager.RINGER_MODE_SILENT);

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
