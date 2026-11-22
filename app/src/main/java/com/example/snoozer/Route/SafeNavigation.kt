package com.example.snoozer.Route

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

/**
 * Navigates only while [from] (the screen's back stack entry) is the resumed, top screen.
 * A second tap during the transition finds it no longer resumed, so a quick double tap
 * opens one screen instead of two stacked copies.
 */
fun NavController.navigateFrom(from: LifecycleOwner, route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (from.lifecycle.currentState == Lifecycle.State.RESUMED) navigate(route, builder)
}

/**
 * Leaves the edit screen. Pops up to and including the edit destination only, so calling it
 * twice (double-tapped Back, or Back racing a save) can't also pop the week screen.
 */
fun NavController.leaveEditScreen() {
    popBackStack(Routes.EditAlarm.route, inclusive = true)
}
