package com.example.snoozer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.snoozer.EditAlarm.EditAlarmScreen
import com.example.snoozer.Route.Routes
import com.example.snoozer.dayPickers.DayPickerMain
import com.example.snoozer.home.HomeScreen
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.onboarding.FirstLaunch
import com.example.snoozer.onboarding.OnboardingScreen
import com.example.snoozer.onboarding.OnboardingStore
import com.example.snoozer.onboarding.decideFirstLaunch
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.util.ScheduleStamp
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboarding: OnboardingStore
    @Inject lateinit var actions: AlarmActions
    @Inject lateinit var scheduleStamp: ScheduleStamp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Picked once, before anything is drawn, so Home never flashes ahead of onboarding.
        val startRoute = startRoute()
        // Snoozers restored from a backup arrive without their system alarms (Android sends no
        // broadcast for a restore). The stamp is missing then, so re-create them all, once, off
        // the main thread. Normal opens skip it.
        if (savedInstanceState == null && scheduleStamp.needsFullReschedule()) {
            lifecycleScope.launch(Dispatchers.IO) {
                actions.rescheduleAll()
                scheduleStamp.markDone()
            }
        }
        setContent {
            val navController = rememberNavController()

            // One theme for every screen. The Surface gives all screens the theme's
            // background and default text color, so nothing falls back to black-on-dark.
            SnoozerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Each screen gets its own ViewModel (hiltViewModel() inside the screen),
                    // scoped to its back stack entry.
                    NavHost(navController = navController, startDestination = startRoute) {
                        composable(Routes.Home.route) {
                            HomeScreen(navController = navController)
                        }

                        // First run: when done, Home replaces the pages (Back won't return to them).
                        composable(
                            Routes.Onboarding.route,
                            arguments = listOf(navArgument(Routes.Onboarding.ARG_MODE) {
                                defaultValue = Routes.Onboarding.MODE_FIRST_RUN
                            }),
                        ) {
                            OnboardingScreen(onExit = {
                                navController.navigate(Routes.Home.route) {
                                    popUpTo(Routes.Onboarding.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            })
                        }

                        // The same pages for help: when done, back to whoever opened them.
                        composable(
                            Routes.HowItWorks.route,
                            arguments = listOf(navArgument(Routes.Onboarding.ARG_MODE) {
                                defaultValue = Routes.Onboarding.MODE_HELP
                            }),
                        ) {
                            OnboardingScreen(onExit = {
                                navController.popBackStack(Routes.HowItWorks.route, inclusive = true)
                            })
                        }

                        composable(Routes.DayPicker.route) {
                            DayPickerMain(navController = navController)
                        }

                        composable(
                            Routes.EditAlarm.route,
                            arguments = listOf(
                                // -1 = no value: a new snoozer / no starting day.
                                navArgument(Routes.EditAlarm.ARG_ID) { type = NavType.IntType; defaultValue = -1 },
                                navArgument(Routes.EditAlarm.ARG_DAY) { type = NavType.IntType; defaultValue = -1 },
                            ),
                        ) {
                            EditAlarmScreen(navController)
                        }
                    }
                }
            }
        }
    }

    /** Onboarding for new people (and until a started one is finished), Home otherwise. See [decideFirstLaunch]. */
    private fun startRoute(): String = when (decideFirstLaunch(onboarding)) {
        FirstLaunch.ShowOnboarding -> Routes.Onboarding.route
        FirstLaunch.MarkSeenQuietly, FirstLaunch.GoHome -> Routes.Home.route
    }
}
