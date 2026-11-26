package com.example.snoozer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
import com.example.snoozer.timetable.ImportTimetableScreen
import com.example.snoozer.report.WeeklyReportScreen
import com.example.snoozer.timetable.openImport
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
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                if (scheduleStamp.needsFullReschedule()) {
                    actions.rescheduleAll()
                    scheduleStamp.markDone()
                } else {
                    // A force stop (Settings, or Android hibernating an app not opened for months)
                    // wipes the alarms and nothing tells the app; check, and re-set them if so.
                    actions.ensureScheduled()
                }
            }
        }
        // Opened with a calendar file ("Open with Snoozer" from Files, mail or the browser): show
        // its import preview over the start screen. Only on a fresh start; after a rotation or
        // process death the back stack already has it.
        val openedFile: Uri? = if (savedInstanceState == null) calendarFileIn(intent) else null
        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) { openedFile?.let(navController::openImport) }

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

                        composable(Routes.WeeklyReport.route) {
                            WeeklyReportScreen(navController)
                        }

                        // A calendar file's classes, before they're added.
                        composable(
                            Routes.ImportTimetable.route,
                            arguments = listOf(navArgument(Routes.ImportTimetable.ARG_URI) {
                                type = NavType.StringType; defaultValue = ""
                            }),
                        ) {
                            ImportTimetableScreen(navController)
                        }

                        composable(
                            Routes.EditAlarm.route,
                            arguments = listOf(
                                // -1 = no value: a new snoozer / no starting day.
                                navArgument(Routes.EditAlarm.ARG_ID) { type = NavType.IntType; defaultValue = -1 },
                                navArgument(Routes.EditAlarm.ARG_DAY) { type = NavType.IntType; defaultValue = -1 },
                                navArgument(Routes.EditAlarm.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
                            ),
                        ) {
                            EditAlarmScreen(navController)
                        }
                    }
                }
            }
        }
    }

    /**
     * The calendar file this activity was opened with, if any. Not when reopened from Recents:
     * Android then replays the original intent, and the file was already handled (its read
     * access has usually lapsed too).
     */
    private fun calendarFileIn(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW && it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?.data?.takeIf { it.scheme == "content" }

    /** Onboarding for new people (and until a started one is finished), Home otherwise. See [decideFirstLaunch]. */
    private fun startRoute(): String = when (decideFirstLaunch(onboarding)) {
        FirstLaunch.ShowOnboarding -> Routes.Onboarding.route
        FirstLaunch.MarkSeenQuietly, FirstLaunch.GoHome -> Routes.Home.route
    }
}
