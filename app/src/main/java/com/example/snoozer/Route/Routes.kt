package com.example.snoozer.Route

import java.time.DayOfWeek

sealed class Routes(val route: String) {
    data object Home : Routes("MainScreen")

    /**
     * The "how Snoozer works" pages. Two routes for the same screen: [Onboarding] is the
     * one-time first run (the start screen on a fresh install), [HowItWorks] opens them for
     * help. Each route's default [ARG_MODE] tells the screen which one it is.
     */
    data object Onboarding : Routes("onboarding") {
        const val ARG_MODE = "mode"
        const val MODE_FIRST_RUN = "firstRun"
        const val MODE_HELP = "help"
    }
    data object HowItWorks : Routes("howItWorks")

    /** Last week's quiet time, for Work and for Study. */
    data object WeeklyReport : Routes("weeklyReport")

    data object DayPicker : Routes("DayPicker")

    /** The import preview for a calendar file; [ARG_URI] is the file's content:// address. */
    data object ImportTimetable : Routes("importTimetable?uri={uri}") {
        const val ARG_URI = "uri"

        fun of(uri: String) = "importTimetable?uri=" + android.net.Uri.encode(uri)
    }

    /** Edit an existing snoozer by id, or create one (optionally starting on a given day). */
    data object EditAlarm : Routes("editAlarm?id={id}&day={day}") {
        const val ARG_ID = "id"
        const val ARG_DAY = "day"

        fun edit(id: Int) = "editAlarm?id=$id"
        fun create(day: DayOfWeek? = null) = if (day == null) "editAlarm" else "editAlarm?day=${day.value}"
    }
}
