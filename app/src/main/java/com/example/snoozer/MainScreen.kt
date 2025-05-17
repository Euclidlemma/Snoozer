package com.example.snoozer

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snoozer.Route.Routes
import com.example.snoozer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MainScreen(navController: NavController) {
    var startAnimation by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(-10f) }
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(startAnimation) {
        if (startAnimation) {
            // check for permission
            if (!isNotificationPolicyAccessGranted(context)) {
                showPermissionDialog = true
                startAnimation = false
                return@LaunchedEffect
            }

            if (!hasExactAlarmPermission(context)) {
                showExactAlarmDialog = true
                startAnimation = false
                return@LaunchedEffect
            }

            // start animation
            launch {
                rotation.animateTo(
                    targetValue = 10f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }

            delay(1000) // Duration of the animation
            startAnimation = false
            navController.navigate(Routes.DayPicker.route) {
                popUpTo(Routes.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    if (showPermissionDialog) {
        DndPermissionDialog(onDismiss = { showPermissionDialog = false })
    }

    if (showExactAlarmDialog) {
        ExactAlarmPermissionDialog(onDismiss = { showExactAlarmDialog = false })
    }

    SnoozerTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            RainbowRow(modifier = Modifier.weight(1f))
            AppName(modifier = Modifier.weight(1f), rotation.value, startAnimation) {
                startAnimation = true
            }
        }
    }
}

@Composable
fun RainbowRow(modifier:Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = red, height = 170)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = orange, height = 140)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = LightBlue, height = 180)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = beige, height = 160)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = Purple80, height = 150)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = Lime, height = 190)
        Spacer(modifier = Modifier.weight(0.5f))
        Sticks(modifier = Modifier.weight(1f), color = SlateBlue, height = 130)
        Spacer(modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun Sticks(modifier: Modifier, color: Color, height: Int) {
    Box(modifier = modifier
        .height(height.dp)
        .background(color = color))
}

@Composable
fun AppName(modifier:Modifier = Modifier, degrees: Float, startAnimation: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SNOOZER", style = MaterialTheme.typography.headlineLarge)
        Box(modifier = Modifier.wrapContentSize().background(color = Color.Yellow, shape = CircleShape)
            .clickable(onClick = onClick)) {
            Image(
                painter = painterResource(id = R.drawable.bell_jar_1096279_1280),
                contentDescription = "bell icon main",
                modifier = Modifier
                    .height(70.dp)
                    .width(70.dp)
                    .padding(10.dp)
                    .rotate(if (startAnimation) degrees else 0f)
            )
        }
    }
}

fun isNotificationPolicyAccessGranted(context: Context): Boolean {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return notificationManager.isNotificationPolicyAccessGranted
}

fun hasExactAlarmPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}


@Composable
fun DndPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = { Text("To mute or unmute your phone automatically, please allow Do Not Disturb access.") },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ExactAlarmPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = {
            Text("To schedule alarms that work reliably, the app needs permission to schedule exact alarms.")
        },
        confirmButton = {
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




@RequiresApi(Build.VERSION_CODES.S)
@Preview
@Composable
fun MainScreen_preview(){
    SnoozerTheme {
        MainScreen(rememberNavController())
    }
}