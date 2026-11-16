package com.example.snoozer.EditAlarm

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.snoozer.R
import com.example.snoozer.alarm.AlarmViewModel
import com.example.snoozer.ui.doodle.DoodleAlertDialog
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleChip
import com.example.snoozer.ui.doodle.DoodleDivider
import com.example.snoozer.ui.theme.SnoozerTheme
import java.time.DayOfWeek


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EditAlarmScreen(navController: NavController, alarmViewModel: AlarmViewModel) {
    val selectedAlarm by alarmViewModel.selectedAlarm.observeAsState()

    var selectedDays by rememberSaveable {
        mutableStateOf(selectedAlarm?.daysOfWeek?.toList() ?: emptyList())
    }

    var showDialog by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    var startHour by rememberSaveable { mutableIntStateOf(selectedAlarm?.startHour ?: 8) }
    var startMinute by rememberSaveable { mutableIntStateOf(selectedAlarm?.startMinute ?: 0) }
    var endHour by rememberSaveable { mutableIntStateOf(selectedAlarm?.endHour ?: 9) }
    var endMinute by rememberSaveable { mutableIntStateOf(selectedAlarm?.endMinute ?: 0) }
    var work by rememberSaveable { mutableStateOf(selectedAlarm?.work ?: "Work") }

    val currentContext = LocalContext.current

    EditScreen(
        onBackClick = { showDialog = true },
        onSave = {
            if (selectedDays.isEmpty()) {
                Toast.makeText(currentContext, "Pick at least one day", Toast.LENGTH_LONG).show()
            } else if (startHour == endHour && startMinute == endMinute) {
                // An end time earlier than the start is allowed: it means the window runs
                // overnight into the next day (e.g. 22:00 ~ 07:00).
                Toast.makeText(currentContext, "Start and end time can't be the same", Toast.LENGTH_LONG).show()
            }
            else {
                if (selectedAlarm == null) alarmViewModel.insertAlarm(
                    startHour,
                    startMinute,
                    endHour,
                    endMinute,
                    selectedDays,
                    work
                )
                else alarmViewModel.updateAlarm(
                    startHour,
                    startMinute,
                    endHour,
                    endMinute,
                    selectedDays,
                    work
                )
                Toast.makeText(currentContext, "Snoozer saved", Toast.LENGTH_SHORT).show()
                // Leave the screen so a second tap can't insert a duplicate.
                navController.popBackStack()
            }
        },
        onDelete = { if (selectedAlarm != null) showDelete = true },
        selectedDays = selectedDays,
        onDaysChanged = { selectedDays = it },
        startHour = startHour,
        startMinute = startMinute,
        endHour = endHour,
        endMinute = endMinute,
        onTimeChangedStart = { h, m ->
            startHour = h
            startMinute = m
        },
        onTimeChangedEnd = { h, m ->
            endHour = h
            endMinute = m
        },
        isEditing = selectedAlarm != null,
        onOptionSelected = { chosenItem ->
            work = chosenItem
        },
        selectedOption = work
    )

    // Confirm leaving with unsaved changes
    if (showDialog) {
        DoodleAlertDialog(
            onDismissRequest = { showDialog = false },
            title = "Leave without saving?",
            text = "Your changes to this snoozer won't be saved.",
            confirmLabel = "Leave",
            onConfirm = {
                showDialog = false
                navController.popBackStack()
            },
            dismissLabel = "Keep editing",
        )
    }

    if (showDelete) {
        DoodleAlertDialog(
            onDismissRequest = { showDelete = false },
            title = "Delete this snoozer?",
            text = "Its mute schedule will be removed. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                showDelete = false
                selectedAlarm?.let { alarmViewModel.deleteAlarm(it) }
                navController.popBackStack()
            },
            dismissLabel = "Cancel",
            destructive = true,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EditScreen(
    onBackClick: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
    selectedDays: List<DayOfWeek>, onDaysChanged: (List<DayOfWeek>) -> Unit, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int,
    onTimeChangedStart: (Int, Int) -> Unit, onTimeChangedEnd: (Int, Int) -> Unit, isEditing : Boolean,
    onOptionSelected :(String) -> Unit, selectedOption: String) {
    Scaffold { paddingValues ->
        // Top bar, scrollable middle, bottom bar stacked in a column, so the middle can
        // never slide under the buttons on small screens or with large font sizes.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TopBar(onBackClick = onBackClick)

            MiddleContent(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                selectedDays = selectedDays,
                onDaysChanged = onDaysChanged,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                onTimeChangedStart = onTimeChangedStart,
                onTimeChangedEnd = onTimeChangedEnd,
                onOptionSelected = onOptionSelected,
                selectedOption = selectedOption
            )

            BottomBar(
                onSave = onSave,
                onDelete = onDelete,
                isEditing = isEditing
            )
        }
    }
}

@Composable
fun TopBar(modifier: Modifier = Modifier, onBackClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        DoodleButton(onClick = onBackClick, seed = 41) {
            // Decorative: the "Back" label already names the action for screen readers.
            Icon(painterResource(R.drawable.ic_doodle_back), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Back")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MiddleContent(
    modifier: Modifier = Modifier, selectedDays: List<DayOfWeek>,
    onDaysChanged: (List<DayOfWeek>) -> Unit, startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int, onTimeChangedStart: (Int, Int) -> Unit, onTimeChangedEnd: (Int, Int) -> Unit,
    selectedOption: String,
    onOptionSelected :(String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Days", style = MaterialTheme.typography.titleLarge)
        MultiDayPicker(
            selectedDays = selectedDays,
            onSelectionChanged = onDaysChanged
        )

        DoodleDivider(seed = 51)

        Text("Time", style = MaterialTheme.typography.titleLarge)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TimePickerSection(label = "Start", hour = startHour, minute = startMinute, onTimeChanged = onTimeChangedStart, seed = 52)
            TimePickerSection(label = "End", hour = endHour, minute = endMinute, onTimeChanged = onTimeChangedEnd, seed = 53)
        }

        DoodleDivider(seed = 54)

        Text("Type", style = MaterialTheme.typography.titleLarge)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WorkStudyDropdown(selectedOption = selectedOption, onOptionSelected = onOptionSelected)
        }
    }
}

@Composable
fun WorkStudyDropdown(selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Work", "Study")
    val category = SnoozerTheme.colors.forCategory(selectedOption)

    Box {
        // The button wears the selected category's colored-pencil fill.
        DoodleButton(
            onClick = { expanded = true },
            containerColor = category.container,
            contentColor = category.content,
            hatchColor = category.hatch,
            seed = 55,
        ) {
            Text(selectedOption)
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_doodle_expand), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiDayPicker(
    selectedDays: List<DayOfWeek>,
    onSelectionChanged: (List<DayOfWeek>) -> Unit
) {
    // FlowRow wraps the seven chips onto two lines on narrow phones instead of scrolling sideways.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selectedDays
            DoodleChip(
                selected = isSelected,
                onClick = {
                    val updatedDays = selectedDays.toMutableList().apply {
                        if (isSelected) remove(day) else add(day)
                    }
                    onSelectionChanged(updatedDays)
                },
                label = day.name.take(1) + day.name.substring(1, 3).lowercase(),
                seed = 60 + day.value,
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TimePickerSection(
    label: String,
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    seed: Int = 0,
) {
    val context = LocalContext.current
    val time = String.format("%02d:%02d", hour, minute)

    DoodleButton(
        modifier = Modifier.fillMaxWidth(0.8f),
        seed = seed,
        onClick = {
            TimePickerDialog(
                context,
                { _, h, m -> onTimeChanged(h, m) },
                hour,
                minute,
                true
            ).show()
        },
    ) {
        Icon(painterResource(R.drawable.ic_doodle_clock), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.headlineMedium)
    }
}



@Composable
fun BottomBar(modifier: Modifier = Modifier, onSave: () -> Unit, onDelete: () -> Unit, isEditing: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DoodleButton(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            hatchColor = colors.primary,
            seed = 71,
        ) {
            Text("Save")
        }

        if (isEditing) {
            DoodleButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                hatchColor = colors.error,
                seed = 72,
            ) {
                Icon(painterResource(R.drawable.ic_doodle_delete), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        } else {
            // Keep Save the same width whether or not Delete is shown.
            Spacer(Modifier.weight(1f))
        }
    }
}



@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF2A2D2F, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ButtonPreview () {
    SnoozerTheme {
        EditScreen(
            onBackClick = { },
            onSave = {  },
            onDelete = {  },
            selectedDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            onDaysChanged = {},
            startHour = 8,
            startMinute = 10,
            endHour = 10,
            endMinute = 8,
            onTimeChangedStart = { h, n -> h+n},
            onTimeChangedEnd = { h, n -> h+n},
            isEditing = true,
            onOptionSelected = {},
            selectedOption = "Work"
        )
    }
}
