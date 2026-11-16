package com.example.snoozer

import android.content.res.Configuration
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.snoozer.Route.Routes
import com.example.snoozer.ui.doodle.DoodleAlertDialog
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.SketchyShape
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
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

    // The theme and background come from MainActivity.
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

// Heights (dp) of the decorative bars, left to right; colors come from the theme.
private val StickHeights = listOf(170, 140, 180, 160, 150, 190, 130)

@Composable
fun RainbowRow(modifier:Modifier = Modifier) {
    val colors = SnoozerTheme.colors.rainbow
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.weight(0.5f))
        StickHeights.forEachIndexed { i, height ->
            Sticks(modifier = Modifier.weight(1f), color = colors[i % colors.size], height = height, seed = i)
            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

/** One decorative bar, drawn like a colored-pencil block with a pencil outline. */
@Composable
fun Sticks(modifier: Modifier, color: Color, height: Int, seed: Int = 0) {
    val radius = 8.dp
    Box(modifier = modifier
        .height(height.dp)
        .doodleFill(color, cornerRadius = radius, wobble = 1.dp, seed = seed)
        .sketchyBorder(MaterialTheme.colorScheme.onBackground, width = 1.5.dp, cornerRadius = radius, wobble = 1.dp, seed = seed))
}

@Composable
fun AppName(modifier:Modifier = Modifier, degrees: Float, startAnimation: Boolean, onClick: () -> Unit) {
    val bellShape = remember { SketchyShape(cornerRadius = DoodleDefaults.Round, seed = 11) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "SNOOZER", style = MaterialTheme.typography.displaySmall)
        // Wobbly yellow circle. The border is applied before the clip so its stroke isn't cut off;
        // the clip keeps the fill and the touch ripple inside the hand-drawn outline.
        Box(
            modifier = Modifier
                .size(104.dp)
                .sketchyBorder(MaterialTheme.colorScheme.onBackground, cornerRadius = DoodleDefaults.Round, seed = 11)
                .clip(bellShape)
                .doodleFill(SnoozerTheme.colors.bell, cornerRadius = DoodleDefaults.Round, seed = 11)
                .clickable(onClickLabel = "Open schedules", role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.bell_jar_1096279_1280),
                contentDescription = "Snoozer bell",
                modifier = Modifier
                    .size(64.dp)
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

    DoodleAlertDialog(
        onDismissRequest = onDismiss,
        title = "Permission required",
        text = "To mute and unmute your phone on schedule, Snoozer needs Do Not Disturb access.",
        confirmLabel = "Open settings",
        onConfirm = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            onDismiss()
        },
        dismissLabel = "Cancel",
    )
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun ExactAlarmPermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    DoodleAlertDialog(
        onDismissRequest = onDismiss,
        title = "Permission required",
        text = "To mute your phone at exactly the right time, Snoozer needs permission to schedule exact alarms.",
        confirmLabel = "Open settings",
        onConfirm = {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            onDismiss()
        },
        dismissLabel = "Cancel",
    )
}




@RequiresApi(Build.VERSION_CODES.S)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF2A2D2F, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MainScreen_preview(){
    SnoozerTheme {
        MainScreen(rememberNavController())
    }
}