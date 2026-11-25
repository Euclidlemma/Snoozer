package com.example.snoozer.alarm

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

val Category.explanation: CategoryExplanation
    get() {
        val levelLines = when (quietLevel) {
            QuietLevel.Vibrate -> listOf(
                ExplainLine(ExplainIcon.Vibrate, "Your phone vibrates instead of ringing."),
                ExplainLine(ExplainIcon.Call, "Calls and messages still buzz."),
            )
            QuietLevel.Silent -> listOf(
                ExplainLine(ExplainIcon.Silent, "Fully silent: no ring, no vibration."),
                ExplainLine(ExplainIcon.Alarm, "Alarms still ring."),
            )
        }
        val reminder = if (hasQuietReminder) {
            ExplainLine(ExplainIcon.Notify, "A quiet heads-up before it starts.")
        } else {
            ExplainLine(ExplainIcon.Notify,
                "A heads-up ${AlarmTime.REMINDER_LEAD_MINUTES} minutes before it starts.")
        }
        val summary = when (quietLevel) {
            QuietLevel.Vibrate -> "Vibrate. Calls still buzz."
            QuietLevel.Silent -> "Fully silent. Only alarms ring."
        }
        return CategoryExplanation(this, "During ${label}", summary, levelLines + reminder)
    }
