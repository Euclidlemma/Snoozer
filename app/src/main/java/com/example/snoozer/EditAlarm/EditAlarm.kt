package com.example.snoozer.EditAlarm

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.snoozer.alarm.AlarmViewModel
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
    var work by remember { mutableStateOf("Work") }

    val currentContext = LocalContext.current

    EditScreen(
        onBackClick = { showDialog = true },
        onSave = {
            if (selectedDays.isEmpty()) {
                Toast.makeText(currentContext, "Day must be selected", Toast.LENGTH_LONG).show()
            } else if (startHour >= endHour) {
                Toast.makeText(currentContext, "Ending Hour must be later then the Start hour", Toast.LENGTH_LONG).show()
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
                Toast.makeText(currentContext, "Snoozer Saved", Toast.LENGTH_SHORT).show()
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

    // alert dialog pressing back button
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            text = {
                Text(text = "your change may not be saved. Do you want to proceed?")
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                        selectedAlarm?.let { alarmViewModel.deleteAlarm(it) }
                        navController.popBackStack()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDelete = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            text = {
                Text(text = "are you sure you want to delete this snoozer?")
            }
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
    Scaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TopBar(
                modifier = Modifier.align(Alignment.TopStart),
                onBackClick = onBackClick
            )

            MiddleContent(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 50.dp, bottom = 50.dp),
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
                modifier = Modifier.align(Alignment.BottomCenter),
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
            .wrapContentHeight()
    ) {
        Button(
            onClick = onBackClick,
            modifier = Modifier.wrapContentSize(),
            colors = ButtonDefaults.buttonColors(Color(0xFFD3D3D3))
        ) {
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
    Column(modifier = modifier.fillMaxSize()) {
        Column {
            Text("Select Days:", modifier = Modifier.padding(10.dp))
            MultiDayPicker(
                selectedDays = selectedDays,
                onSelectionChanged = onDaysChanged
            )
        }
        HorizontalDivider(modifier = Modifier.padding(40.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            TimePickerSection(label = "Start Time", hour = startHour, minute = startMinute, onTimeChanged = onTimeChangedStart)

            TimePickerSection(label = "End Time", hour = endHour, minute = endMinute, onTimeChanged = onTimeChangedEnd)
        }

        HorizontalDivider(modifier = Modifier.padding(40.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            WorkStudyDropdown(selectedOption = selectedOption, onOptionSelected = onOptionSelected)
        }
    }
}

@Composable
fun WorkStudyDropdown(selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("Work", "Study")

    Box {
        Button(onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = if (selectedOption== "Work") Color(0xFF90D5FF) else Color(0xFFDAB1DA)))
        {
            Text(selectedOption)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun MultiDayPicker(
    selectedDays: List<DayOfWeek>,
    onSelectionChanged: (List<DayOfWeek>) -> Unit
) {
    val allDays = DayOfWeek.entries.toTypedArray()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(allDays) { day ->
            val isSelected = day in selectedDays
            FilterChip(
                modifier = Modifier.padding(10.dp),
                selected = isSelected,
                onClick = {
                    val updatedDays = selectedDays.toMutableList().apply {
                        if (isSelected) remove(day) else add(day)
                    }
                    onSelectionChanged(updatedDays)
                },
                label = { Text(day.name.take(3)) }
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
    onTimeChanged: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val time = String.format("%02d:%02d", hour, minute)

    Button(modifier = Modifier.padding(20.dp),
        onClick = {
        val picker = TimePickerDialog(
            context,
            { _, h, m -> onTimeChanged(h, m) },
            hour,
            minute,
            true
        )
        picker.show()
    }) {
        Text("$label: $time")
    }
}



@Composable
fun BottomBar(modifier: Modifier = Modifier, onSave: () -> Unit, onDelete: () -> Unit, isEditing: Boolean) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(Color(0xFFD3D3D3))
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isEditing) {
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(Color.Red)
            ) {
                Text("Delete")
            }
        }
    }
}




@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun ButtonPreview () {
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
        isEditing = false,
        onOptionSelected = {},
        selectedOption = "Work"
    )
}
