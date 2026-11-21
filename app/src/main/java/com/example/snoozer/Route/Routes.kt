package com.example.snoozer.Route

import java.time.DayOfWeek

sealed class Routes(val route: String) {
    data object Home : Routes("MainScreen")
    data object DayPicker : Routes("DayPicker")

    /** Edit an existing snoozer by id, or create one (optionally starting on a given day). */
    data object EditAlarm : Routes("editAlarm?id={id}&day={day}") {
        const val ARG_ID = "id"
        const val ARG_DAY = "day"

        fun edit(id: Int) = "editAlarm?id=$id"
        fun create(day: DayOfWeek? = null) = if (day == null) "editAlarm" else "editAlarm?day=${day.value}"
    }
}
