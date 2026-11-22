package com.example.snoozer.EditAlarm

import android.app.TimePickerDialog
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.Route.leaveEditScreen
import com.example.snoozer.R
import com.example.snoozer.ui.doodle.DoodleAlertDialog
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleChip
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleSection
import com.example.snoozer.ui.doodle.SketchyShape
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EditAlarmScreen(navController: NavController, viewModel: EditAlarmViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Saved, deleted or missing: say so and leave.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            event.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            navController.leaveEditScreen()
        }
    }

    val current = state
    val onBack = { if (current?.hasChanges == true) confirmLeave = true else navController.leaveEditScreen() }
    // The system back gesture asks too, not just the on-screen button.
    BackHandler(enabled = current?.hasChanges == true) { confirmLeave = true }

    Scaffold { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding)) // loading the saved snoozer
        } else {
            EditContent(
                state = current,
                onBack = { onBack() },
                onDelete = { confirmDelete = true },
                onCategory = viewModel::onCategory,
                onStartChange = viewModel::onStartChange,
                onEndChange = viewModel::onEndChange,
                onToggleDay = viewModel::onToggleDay,
                onPreset = viewModel::onPreset,
                onSave = viewModel::save,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmLeave) {
        DoodleAlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = "Leave without saving?",
            text = "Your changes to this snoozer won't be saved.",
            confirmLabel = "Leave",
            onConfirm = {
                confirmLeave = false
                navController.leaveEditScreen()
            },
            dismissLabel = "Keep editing",
        )
    }

    if (confirmDelete) {
        DoodleAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = "Delete this snoozer?",
            text = "Its mute schedule will be removed. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.delete() // the screen leaves once the delete has finished
            },
            dismissLabel = "Cancel",
            destructive = true,
        )
    }
}

/** Stateless edit screen: top bar, the Work/Study toggle, When and Repeat cards, and Save. */
@Composable
fun EditContent(
    state: EditUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onCategory: (String) -> Unit,
    onStartChange: (Int, Int) -> Unit,
    onEndChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onPreset: (RepeatPreset) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Top bar, scrollable middle and save bar stacked in a column, so the middle can
    // never slide under the Save button on small screens or with large font sizes.
    Column(modifier.fillMaxSize()) {
        EditTopBar(isNew = state.isNew, onBack = onBack, onDelete = onDelete)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            CategoryToggle(selected = state.category, onSelect = onCategory)
            WhenCard(state = state, onStartChange = onStartChange, onEndChange = onEndChange)
            RepeatCard(days = state.days, preset = state.preset, onToggleDay = onToggleDay, onPreset = onPreset)
        }
        SaveBar(isNew = state.isNew, error = state.error, onSave = onSave)
    }
}

@Composable
private fun EditTopBar(isNew: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        DoodleButton(onClick = onBack, seed = 41, wobble = DoodleDefaults.WobbleLight) {
            // Decorative: the "Back" label already names the action for screen readers.
            Icon(painterResource(R.drawable.ic_doodle_back), contentDescription = null)
            Spacer(Modifier.width(Spacing.XS))
            Text("Back")
        }
        Text(
            text = if (isNew) "New snoozer" else "Edit snoozer",
            style = SnoozerTheme.type.title,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (!isNew) {
            // Quiet and out of the way: deleting is rare and asks for confirmation.
            DoodleButton(
                onClick = onDelete,
                seed = 42,
                wobble = DoodleDefaults.WobbleLight,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.error,
                borderColor = MaterialTheme.colorScheme.error,
            ) {
                Icon(painterResource(R.drawable.ic_doodle_delete), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.XS))
                Text("Delete")
            }
        }
    }
}

/** Work / Study as two side-by-side choices; the chosen one wears its category's pencil fill. */
@Composable
private fun CategoryToggle(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        listOf(EditForm.WORK, EditForm.STUDY).forEachIndexed { index, category ->
            val isSelected = category == selected
            val colors = SnoozerTheme.colors.forCategory(category)
            val seed = 80 + index
            val shape = remember(seed) { SketchyShape(wobble = DoodleDefaults.WobbleLight, seed = seed) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .doodleFill(
                        if (isSelected) colors.container else Color.Transparent,
                        if (isSelected) colors.hatch else null,
                        wobble = DoodleDefaults.WobbleLight, seed = seed,
                    )
                    .sketchyBorder(
                        if (isSelected) scheme.onSurface else scheme.outline,
                        width = if (isSelected) 2.dp else 1.5.dp,
                        wobble = DoodleDefaults.WobbleLight, seed = seed,
                    )
                    .clip(shape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(category) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    category,
                    style = SnoozerTheme.type.label,
                    color = if (isSelected) colors.content else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Start and end side by side, with a live line saying how long the phone will be quiet. */
@Composable
private fun WhenCard(state: EditUiState, onStartChange: (Int, Int) -> Unit, onEndChange: (Int, Int) -> Unit) {
    DoodleSection(title = "When", seed = 90) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            TimeField("Start", state.start, onStartChange, seed = 91, modifier = Modifier.weight(1f))
            Text("–", style = SnoozerTheme.type.headline)
            TimeField("End", state.end, onEndChange, seed = 92, modifier = Modifier.weight(1f))
        }
        if (state.summary != null) {
            Text(state.summary, style = SnoozerTheme.type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tappable time ("Start 09:00") that opens the system time picker. */
@Composable
private fun TimeField(label: String, time: String, onChange: (Int, Int) -> Unit, seed: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = remember(seed) { SketchyShape(wobble = DoodleDefaults.WobbleLight, seed = seed) }
    Column(
        modifier = modifier
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .clip(shape)
            .clickable(onClickLabel = "Change $label time", role = Role.Button) {
                val (hour, minute) = time.split(":").map { it.toInt() }
                TimePickerDialog(context, { _, h, m -> onChange(h, m) }, hour, minute, true).show()
            }
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
    ) {
        Text(label, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_doodle_clock), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.S))
            Text(time, style = SnoozerTheme.type.headline)
        }
    }
}

/** The seven day chips plus quick presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatCard(
    days: List<DayOfWeek>,
    preset: RepeatPreset?,
    onToggleDay: (DayOfWeek) -> Unit,
    onPreset: (RepeatPreset) -> Unit,
) {
    DoodleSection(title = "Repeat", seed = 93) {
        // FlowRow wraps the chips onto two lines on narrow phones instead of scrolling sideways.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            DayOfWeek.entries.forEach { day ->
                DoodleChip(
                    selected = day in days,
                    onClick = { onToggleDay(day) },
                    label = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    seed = 60 + day.value,
                    wobble = DoodleDefaults.WobbleLight,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            RepeatPreset.entries.forEachIndexed { index, p ->
                DoodleChip(
                    selected = p == preset,
                    onClick = { onPreset(p) },
                    label = p.label,
                    seed = 70 + index,
                    wobble = DoodleDefaults.WobbleLight,
                )
            }
        }
    }
}

/** Full-width Save, with the reason it's disabled written right above it. */
@Composable
private fun SaveBar(isNew: Boolean, error: String?, onSave: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (error != null) {
            Text(error, style = SnoozerTheme.type.body, color = MaterialTheme.colorScheme.error)
        }
        DoodleButton(
            onClick = onSave,
            enabled = error == null,
            modifier = Modifier.fillMaxWidth(),
            hatchColor = MaterialTheme.colorScheme.primary,
            seed = 71,
        ) {
            Text(if (isNew) "Save snoozer" else "Save changes")
        }
    }
}

@Preview(name = "Edit - light", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Edit - dark", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditContentPreview() {
    val original = EditForm(22, 0, 7, 0, RepeatPreset.Weekdays.days.sorted(), EditForm.STUDY)
    SnoozerTheme {
        EditContent(
            state = EditUi.describe(original, original, isNew = false),
            onBack = {}, onDelete = {}, onCategory = {}, onStartChange = { _, _ -> }, onEndChange = { _, _ -> },
            onToggleDay = {}, onPreset = {}, onSave = {},
        )
    }
}

@Preview(name = "New, can't save yet", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun NewSnoozerPreview() {
    val form = EditForm.new(null)
    SnoozerTheme {
        EditContent(
            state = EditUi.describe(form, form, isNew = true),
            onBack = {}, onDelete = {}, onCategory = {}, onStartChange = { _, _ -> }, onEndChange = { _, _ -> },
            onToggleDay = {}, onPreset = {}, onSave = {},
        )
    }
}
