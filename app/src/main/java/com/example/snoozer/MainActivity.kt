package com.example.snoozer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snoozer.EditAlarm.EditAlarmScreen
import com.example.snoozer.dayPickers.DayPickerMain
import com.example.snoozer.dayPickers.DayPickerViewModel
import com.example.snoozer.Route.Routes
import com.example.snoozer.alarm.AlarmViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val alarmViewModel: AlarmViewModel = hiltViewModel()

            NavHost(navController = navController, startDestination = "MainScreen") {
                composable(Routes.Home.route) {
                    MainScreen(navController = navController)
                }

                composable(Routes.DayPicker.route) {
                    DayPickerMain(navController = navController, alarmViewModel = alarmViewModel)
                }

                composable(Routes.EditAlarm.route) {
                    EditAlarmScreen(navController, alarmViewModel)
                }
            }
        }
    }
}

/*@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SnoozerTheme {
        Greeting("Android")
    }
}*/