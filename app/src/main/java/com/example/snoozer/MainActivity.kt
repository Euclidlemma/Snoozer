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
import com.example.snoozer.ui.theme.SnoozerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                    NavHost(navController = navController, startDestination = Routes.Home.route) {
                        composable(Routes.Home.route) {
                            MainScreen(navController = navController)
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
}
