package com.example.snoozer.alarm

import com.example.snoozer.R
import com.example.snoozer.util.Strings

/** The doodle icon for one line of an explanation. The UI maps each to its drawable. */
enum class ExplainIcon { Vibrate, Silent, Alarm, Call, Notify }

/** One line of "what happens", with its icon. */
data class ExplainLine(val icon: ExplainIcon, val text: String)

/**
 * What a category does, in words, for the onboarding pages and the edit screen's
 * "What happens" panel. Built from the same [QuietLevel] and reminder channel the app uses,
 * so the explanation can't drift from the behavior.
 */
data class CategoryExplanation(
    val category: Category,
    /** "During Work". */
    val title: String,
    /** One short line for small spaces: "Vibrate. Calls still buzz." */
    val summary: String,
    /** The full list, quiet level first, reminder last. */
    val lines: List<ExplainLine>,
)

/** Study reminders are a normal notice with sound; Work reminders are quiet (no sound). */
val Category.hasQuietReminder: Boolean
    get() = this == Category.Work

/** What this category does, assuming reminders can be shown. */
fun Category.explanation(strings: Strings): CategoryExplanation = explain(strings, remindersOn = true)

/**
 * What this category does. With [remindersOn] false (notifications off), the reminder line says
 * so instead of promising a heads-up that will never arrive, and says where to turn them on:
 * Home's Setup when [setupHasNotifications] (Android 13+), otherwise Android's settings.
 */
fun Category.explain(strings: Strings, remindersOn: Boolean, setupHasNotifications: Boolean = true): CategoryExplanation {
    val levelLines = when (quietLevel) {
        QuietLevel.Vibrate -> listOf(
            ExplainLine(ExplainIcon.Vibrate, strings.get(R.string.explain_vibrate)),
            ExplainLine(ExplainIcon.Call, strings.get(R.string.explain_calls_buzz)),
        )
        QuietLevel.Silent -> listOf(
            ExplainLine(ExplainIcon.Silent, strings.get(R.string.explain_silent)),
            ExplainLine(ExplainIcon.Alarm, strings.get(R.string.explain_alarms_ring)),
        )
    }
    val reminder = if (!remindersOn) {
        // Setup only has a Notifications row on Android 13+; before that it's a system setting.
        ExplainLine(ExplainIcon.Notify,
            strings.get(if (setupHasNotifications) R.string.explain_heads_up_off_setup else R.string.explain_heads_up_off_android))
    } else if (hasQuietReminder) {
        ExplainLine(ExplainIcon.Notify, strings.get(R.string.explain_heads_up_quiet))
    } else {
        val lead = AlarmTime.REMINDER_LEAD_MINUTES
        ExplainLine(ExplainIcon.Notify, strings.plural(R.plurals.explain_heads_up_minutes, lead, lead))
    }
    val summary = when (quietLevel) {
        QuietLevel.Vibrate -> strings.get(R.string.explain_summary_vibrate)
        QuietLevel.Silent -> strings.get(R.string.explain_summary_silent)
    }
    return CategoryExplanation(this, strings.get(R.string.explain_title, strings.get(labelRes)), summary, levelLines + reminder)
}
