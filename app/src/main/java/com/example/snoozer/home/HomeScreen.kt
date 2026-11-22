package com.example.snoozer.home

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.example.snoozer.ui.doodle.DoodleSection
import com.example.snoozer.ui.doodle.SketchyShape
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.highlighter
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import com.example.snoozer.util.Permissions
import com.example.snoozer.util.openDndSettings
import com.example.snoozer.util.openExactAlarmSettings

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Permissions are granted in the system Settings app, which sends no callback.
    // Re-checking on every resume updates the checklist the moment the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val lifecycleOwner = LocalLifecycleOwner.current // this screen's back stack entry
    val current = state ?: return // first database read; the screen is blank for a moment
    HomeContent(
        state = current,
        onAllowDnd = context::openDndSettings,
        onAllowExactAlarms = context::openExactAlarmSettings,
        onOpenWeek = {
            navController.navigateFrom(lifecycleOwner, Routes.DayPicker.route) {
                popUpTo(Routes.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        },
    )
}

/** Stateless home screen: header, today's status, the setup checklist and "Open my week". */
@Composable
fun HomeContent(
    state: HomeUiState,
    onAllowDnd: () -> Unit,
    onAllowExactAlarms: () -> Unit,
    onOpenWeek: () -> Unit,
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
            RainbowRow(Modifier.padding(top = Spacing.M))
            Header()
            StatusLine(state.statusLine)
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
                        description = "Recommended. Without it, muting can start a few minutes late.",
                        granted = state.permissions.exactAlarms,
                        onAllow = onAllowExactAlarms,
                        seed = 124,
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

// Heights (dp) of the decorative bars, left to right; colors come from the theme.
private val StickHeights = listOf(68, 56, 72, 64, 60, 76, 52)

/** Seven colored-pencil bars standing on one baseline, one per weekday. Decorative. */
@Composable
private fun RainbowRow(modifier: Modifier = Modifier) {
    val colors = SnoozerTheme.colors.rainbow
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.L),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.Bottom,
    ) {
        StickHeights.forEachIndexed { i, height ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .doodleFill(colors[i % colors.size], cornerRadius = 8.dp, wobble = 1.dp, seed = i)
                    .sketchyBorder(ink, width = 1.5.dp, cornerRadius = 8.dp, wobble = 1.dp, seed = i)
            )
        }
    }
}

/** The bell in its yellow circle, the app name and a one-line explanation. */
@Composable
private fun Header() {
    val bellShape = remember { SketchyShape(cornerRadius = DoodleDefaults.Round, seed = 11) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        // The border goes before the clip so its stroke isn't cut off.
        Box(
            modifier = Modifier
                .size(72.dp)
                .sketchyBorder(MaterialTheme.colorScheme.onBackground, cornerRadius = DoodleDefaults.Round, seed = 11)
                .clip(bellShape)
                .doodleFill(SnoozerTheme.colors.bell, cornerRadius = DoodleDefaults.Round, seed = 11),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.bell_jar_1096279_1280), contentDescription = null, modifier = Modifier.size(44.dp))
        }
        Column {
            Text("SNOOZER", style = SnoozerTheme.type.headline)
            Text(
                "Mutes your phone on a weekly schedule.",
                style = SnoozerTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Today's status from the same source as the week screen's card, as one highlighted line. */
@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
@Composable
private fun HomeFirstRunPreview() {
    SnoozerTheme {
        HomeContent(
            state = HomeUiState("No quiet times planned yet", Permissions(dnd = false, exactAlarms = false, exactAlarmsApplies = true)),
            onAllowDnd = {}, onAllowExactAlarms = {}, onOpenWeek = {},
        )
    }
}
