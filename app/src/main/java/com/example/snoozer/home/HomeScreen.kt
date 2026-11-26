package com.example.snoozer.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.R
import com.example.snoozer.Route.Routes
import com.example.snoozer.Route.navigateFrom
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.Illustrations
import com.example.snoozer.ui.doodle.DoodleSection
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.highlighter
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import com.example.snoozer.util.Permissions
import com.example.snoozer.util.openDndSettings
import com.example.snoozer.util.openExactAlarmSettings
import com.example.snoozer.util.openBatterySettings
import com.example.snoozer.util.openUnusedAppSettings
import com.example.snoozer.util.openNotificationSettings
import com.example.snoozer.util.notificationAsk
import com.example.snoozer.util.refusedForGood
import com.example.snoozer.util.NotificationAsk

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permissions are granted in the system Settings app, which sends no callback.
    // Re-checking on every resume updates the checklist the moment the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // The notification permission is a normal runtime dialog (Android 13+), asked only when the
    // user taps Allow. Saying no changes nothing else; the row just stays unticked.
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // Android's "show rationale" flag just before the dialog opened; with the flag after it, it
    // tells a final refusal apart from a first "no" or a dialog closed without choosing.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rationaleAfter = context.findActivity()
            ?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
        prefs.edit().putBoolean(KEY_REFUSED_NOTIFICATIONS, refusedForGood(granted, rationaleBefore, rationaleAfter)).apply()
        viewModel.refreshPermissions()
    }

    val lifecycleOwner = LocalLifecycleOwner.current // this screen's back stack entry
    val current = state ?: return // first database read; the screen is blank for a moment
    HomeContent(
        state = current,
        onAllowDnd = context::openDndSettings,
        onAllowExactAlarms = context::openExactAlarmSettings,
        onAllowBattery = context::openBatterySettings,
        onKeepWhenUnused = context::openUnusedAppSettings,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = Manifest.permission.POST_NOTIFICATIONS
                val action = notificationAsk(
                    permissionGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
                    refusedForGood = prefs.getBoolean(KEY_REFUSED_NOTIFICATIONS, false),
                )
                when (action) {
                    NotificationAsk.Ask -> {
                        rationaleBefore = context.findActivity()?.shouldShowRequestPermissionRationale(permission) == true
                        askNotifications.launch(permission)
                    }
                    NotificationAsk.OpenSettings -> context.openNotificationSettings()
                }
            } else {
                context.openNotificationSettings()
            }
        },
        onOpenWeek = {
            navController.navigateFrom(lifecycleOwner, Routes.DayPicker.route) {
                popUpTo(Routes.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
    )
}

private const val PREFS = "permission_asks"
private const val KEY_REFUSED_NOTIFICATIONS = "refused_notifications"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Stateless home screen: header, today's status, the setup checklist and "Open my week". */
@Composable
fun HomeContent(
    state: HomeUiState,
    onAllowDnd: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onOpenWeek: () -> Unit,
    onAllowNotifications: () -> Unit = {},
    onAllowBattery: () -> Unit = {},
    onKeepWhenUnused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            MascotHeader(Modifier.fillMaxWidth().padding(top = Spacing.M))
            StatusLine(state.statusLine, Modifier.align(Alignment.CenterHorizontally))
            DoodleSection(title = "Setup", seed = 120) {
                PermissionRow(
                    title = "Do Not Disturb access",
                    description = "Required. Lets Snoozer mute and unmute your phone.",
                    granted = state.permissions.dnd,
                    onAllow = onAllowDnd,
                    seed = 121,
                )
                if (state.permissions.exactAlarmsApplies) {
                    PermissionRow(
                        title = "Exact alarms",
                        description = "Recommended. Without it, muting can start up to an hour late.",
                        granted = state.permissions.exactAlarms,
                        onAllow = onAllowExactAlarms,
                        seed = 124,
                    )
                }
                if (state.permissions.notificationsApply) {
                    PermissionRow(
                        title = "Notifications",
                        description = "Optional. Reminds you 5 minutes before quiet time starts.",
                        granted = state.permissions.notifications,
                        onAllow = onAllowNotifications,
                        seed = 127,
                    )
                }
                PermissionRow(
                    title = "Battery: Unrestricted",
                    description = "Recommended. Keeps your phone from putting Snoozer to sleep and delaying it. " +
                        "Opens app info: tap Battery, then Unrestricted.",
                    granted = state.permissions.batteryUnrestricted,
                    onAllow = onAllowBattery,
                    seed = 130,
                )
                if (state.permissions.keptWhenUnusedApplies) {
                    PermissionRow(
                        title = "Keep running when unused",
                        description = "Recommended. Phones pause apps you haven't opened for a few months, which would stop Snoozer. " +
                            "Turn off \"Manage app if unused\" (on some phones: \"Pause app activity if unused\").",
                        granted = state.permissions.keptWhenUnused,
                        onAllow = onKeepWhenUnused,
                        seed = 133,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            if (!state.ready) {
                Text(
                    "Allow Do Not Disturb access to continue.",
                    style = SnoozerTheme.type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            // The screen's one main action, so it keeps the full wobble.
            DoodleButton(
                onClick = onOpenWeek,
                enabled = state.ready,
                modifier = Modifier.fillMaxWidth(),
                hatchColor = MaterialTheme.colorScheme.primary,
                seed = 130,
            ) {
                Text("Open my week")
            }
        }
    }
}

/**
 * Snoozer's mascot, the bell-hat guard with crossed arms, in the yellow circle, with the
 * app name and a one-line explanation under it. The drawing is decorative: the name and
 * tagline are what screen readers announce.
 */
@Composable
private fun MascotHeader(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        DoodleIllustration(
            image = Illustrations.Guard,
            size = MascotSize,
            round = true,
            backing = SnoozerTheme.colors.bell,
            outline = MaterialTheme.colorScheme.onBackground,
            // The figure has its own margins; at full scale it fills the circle without touching it.
            imageScale = 1f,
            seed = 11,
        )
        Spacer(Modifier.height(Spacing.S))
        Text(
            "SNOOZER",
            style = MaterialTheme.typography.displaySmall,
            letterSpacing = 2.sp,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "On guard, so your phone stays quiet.",
            style = SnoozerTheme.type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val MascotSize = 196.dp

/** Today's status from the same source as the week screen's card, as one highlighted line. */
@Composable
private fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Icon(painterResource(R.drawable.ic_doodle_clock), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            text,
            style = SnoozerTheme.type.body,
            modifier = Modifier.highlighter(SnoozerTheme.colors.highlighter).padding(horizontal = Spacing.XS),
        )
    }
}

/**
 * One checklist line: a pencil circle (ticked once granted), what the permission is for,
 * and an "Allow" button while it is still missing.
 */
@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onAllow: () -> Unit, seed: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .doodleFill(if (granted) scheme.secondaryContainer else Color.Transparent,
                    cornerRadius = DoodleDefaults.Round, wobble = DoodleDefaults.WobbleLight, seed = seed)
                .sketchyBorder(if (granted) scheme.onSurface else scheme.outline, width = 1.5.dp,
                    cornerRadius = DoodleDefaults.Round, wobble = DoodleDefaults.WobbleLight, seed = seed)
                .semantics { contentDescription = if (granted) "Allowed" else "Not allowed yet" },
            contentAlignment = Alignment.Center,
        ) {
            if (granted) Icon(painterResource(R.drawable.ic_doodle_check), contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = SnoozerTheme.type.label)
            Text(description, style = SnoozerTheme.type.caption, color = scheme.onSurfaceVariant)
        }
        if (!granted) {
            DoodleButton(onClick = onAllow, seed = seed + 1, wobble = DoodleDefaults.WobbleLight) { Text("Allow") }
        }
    }
}

@Preview(name = "Home - ready", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Home - ready, dark", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeReadyPreview() {
    SnoozerTheme {
        HomeContent(
            state = HomeUiState("Next quiet time: 22:00 today", Permissions(dnd = true, exactAlarms = true, exactAlarmsApplies = true)),
            onAllowDnd = {}, onAllowExactAlarms = {}, onOpenWeek = {},
        )
    }
}

@Preview(name = "Home - first run", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
// A small phone: the middle scrolls, and "Open my week" stays pinned at the bottom.
@Preview(name = "Home - first run, small phone", widthDp = 320, heightDp = 568, showBackground = true,
    backgroundColor = 0xFFFAF6EC)
@Composable
private fun HomeFirstRunPreview() {
    SnoozerTheme {
        HomeContent(
            state = HomeUiState("No quiet times planned yet", Permissions(dnd = false, exactAlarms = false, exactAlarmsApplies = true, notifications = false, notificationsApply = true, batteryUnrestricted = false, keptWhenUnused = false, keptWhenUnusedApplies = true)),
            onAllowDnd = {}, onAllowExactAlarms = {}, onOpenWeek = {},
        )
    }
}
