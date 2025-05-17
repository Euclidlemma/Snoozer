package com.example.snoozer.Route

sealed class Routes(val route: String) {
    data object Home : Routes("MainScreen")
    data object DayPicker : Routes("DayPicker")
    data object EditAlarm : Routes("editAlarm")
}