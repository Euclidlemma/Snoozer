package com.example.snoozer.timetable

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.Route.Routes

/**
 * Calendar files as Android's file picker knows them. Not every app labels .ics files
 * "text/calendar" (Drive and some downloads say octet-stream or plain text), so those are
 * offered too; the import screen then checks the file really is a calendar.
 */
private val CalendarTypes = arrayOf("text/calendar", "text/x-vcalendar", "application/ics", "application/octet-stream", "text/plain")

/**
 * Returns a function that opens Android's file picker for a calendar file and passes the picked
 * file to [onPicked]. Nothing is called if the user backs out. Needs no permission: the picker
 * grants access to just that one file.
 */
@Composable
fun rememberTimetablePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep read access past this moment, so the preview can read the file again if Android
            // restarts the app meanwhile. Released when the preview closes (ImportViewModel).
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Some providers don't offer it; the file is still readable for now.
            }
            picked(uri)
        }
    }
    return {
        try {
            launcher.launch(CalendarTypes)
        } catch (e: ActivityNotFoundException) {
            // A phone with no document picker (some locked-down devices).
            Toast.makeText(context, "No file picker on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Opens the import preview for [uri], from the week screen or the "open with" intent. */
fun NavController.openImport(uri: Uri) = navigate(Routes.ImportTimetable.of(uri.toString()))

/** The import preview, wired to [ImportViewModel]. */
@Composable
fun ImportTimetableScreen(navController: NavController, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pick another file: this preview is replaced, so Back doesn't return to the old one.
    val pickAnother = rememberTimetablePicker { uri ->
        navController.navigate(Routes.ImportTimetable.of(uri.toString())) {
            popUpTo(Routes.ImportTimetable.route) { inclusive = true }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            event.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            if (event.leave) {
                // Land on the week screen to see the classes; reuse it if it's already below.
                navController.navigate(Routes.DayPicker.route) {
                    popUpTo(Routes.ImportTimetable.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold { padding ->
        ImportContent(
            state = state,
            onBack = { navController.popBackStack(Routes.ImportTimetable.route, inclusive = true) },
            onToggle = viewModel::onToggle,
            onCategory = viewModel::onCategory,
            onAdd = viewModel::add,
            onPickAnother = pickAnother,
            modifier = Modifier.padding(padding),
        )
    }
}
