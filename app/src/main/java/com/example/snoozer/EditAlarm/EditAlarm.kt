package com.example.snoozer.EditAlarm

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.Route.Routes
import com.example.snoozer.Route.navigateFrom
import com.example.snoozer.Route.leaveEditScreen
import com.example.snoozer.R
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.quietLevel
import com.example.snoozer.ui.doodle.DoodleAlertDialog
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleChip
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.illustration
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
    val lifecycleOwner = LocalLifecycleOwner.current // this screen's back stack entry
    // Notifications may be allowed in Settings meanwhile; the panel's heads-up line follows.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }
    val context = LocalContext.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Saved, deleted or missing: say so and leave. A failed write only shows its message.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            event.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            if (event.leave) navController.leaveEditScreen()
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
                onName = viewModel::onName,
                onClearDates = viewModel::onClearDates,
                onSave = viewModel::save,
                // The edit screen stays underneath, so the half-filled form is still here on return.
                onHowItWorks = { navController.navigateFrom(lifecycleOwner, Routes.HowItWorks.route) },
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
    onCategory: (Category) -> Unit,
    onStartChange: (Int, Int) -> Unit,
    onEndChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onPreset: (RepeatPreset) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onHowItWorks: () -> Unit = {},
    onName: (String) -> Unit = {},
    onClearDates: () -> Unit = {},
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
            WhatHappensPanel(state.explanation, onHowItWorks = onHowItWorks)
            WhenCard(state = state, onStartChange = onStartChange, onEndChange = onEndChange)
            RepeatCard(days = state.days, preset = state.preset, onToggleDay = onToggleDay, onPreset = onPreset)
            NameCard(name = state.name, onName = onName)
            if (state.dates != null) DatesCard(dates = state.dates, onClear = onClearDates)
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

/**
 * Work / Study as two side-by-side picture cards. The chosen one wears its category's pencil
 * hatching, a firmer outline and a check mark; the other card's drawing is faded. The whole
 * card is one radio option for screen readers ("Study, selected"); the drawing is decorative.
 */
@Composable
private fun CategoryToggle(selected: Category, onSelect: (Category) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Category.entries.forEachIndexed { index, category ->
            CategoryCard(
                category = category,
                isSelected = category == selected,
                onSelect = { onSelect(category) },
                seed = 80 + index,
                // Same height for both cards, even if one label wraps at a large font size.
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    isSelected: Boolean,
    onSelect: () -> Unit,
    seed: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = SnoozerTheme.colors.forCategory(category)
    val shape = remember(seed) { SketchyShape(wobble = DoodleDefaults.WobbleLight, seed = seed) }
    Column(
        modifier = modifier
            .doodleFill(
                if (isSelected) colors.container else scheme.surface,
                if (isSelected) colors.hatch else null,
                wobble = DoodleDefaults.WobbleLight, seed = seed,
            )
            .sketchyBorder(
                if (isSelected) scheme.onSurface else scheme.outline,
                width = if (isSelected) 2.5.dp else 1.5.dp,
                wobble = DoodleDefaults.WobbleLight, seed = seed,
            )
            .clip(shape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        DoodleIllustration(
            image = category.illustration,
            size = CategoryArtSize,
            outline = if (isSelected) scheme.onSurface else null,
            dimmed = !isSelected,
            seed = seed + 10,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                category.label,
                style = SnoozerTheme.type.label,
                color = if (isSelected) colors.content else scheme.onSurfaceVariant,
            )
            if (isSelected) {
                Spacer(Modifier.width(Spacing.XS))
                // Decorative: the card already announces itself as selected.
                Icon(
                    painterResource(R.drawable.ic_doodle_check),
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // What choosing this category does, so the choice isn't only a color.
        Text(
            category.quietLevel.description,
            style = SnoozerTheme.type.caption,
            color = if (isSelected) colors.content else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val CategoryArtSize = 120.dp

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
    val shape = remember(seed) { SketchyShape(wobble = DoodleDefaults.WobbleLight, seed = seed) }
    // Saved across configuration changes (folding or unfolding, rotating, theme switch), so
    // the picker opens again after the activity is recreated instead of vanishing.
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        TimePicker(time = time, onPicked = onChange, onClose = { picking = false })
    }
    Column(
        modifier = modifier
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .clip(shape)
            .clickable(onClickLabel = "Change $label time", role = Role.Button) { picking = true }
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

/**
 * The system time picker, owned by composition: it is shown while this is composed and
 * dismissed when it leaves (including when the activity is torn down for a fold or rotation),
 * so its window is never leaked. Uses the phone's 12/24-hour setting.
 */
@Composable
private fun TimePicker(time: String, onPicked: (Int, Int) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnClose by rememberUpdatedState(onClose)
    DisposableEffect(context) {
        val (hour, minute) = time.split(":").map { it.toInt() }
        val dialog = TimePickerDialog(
            context,
            { _, h, m -> currentOnPicked(h, m) },
            hour, minute,
            DateFormat.is24HourFormat(context),
        )
        // Set, Cancel, back and tapping outside all end here.
        dialog.setOnDismissListener { currentOnClose() }
        dialog.show()
        onDispose {
            // Torn down by the system, not by the user: keep `picking` so it reopens.
            dialog.setOnDismissListener(null)
            dialog.dismiss()
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
    val original = EditForm(22, 0, 7, 0, RepeatPreset.Weekdays.days.sorted(), Category.Study)
    SnoozerTheme {
        EditContent(
            state = EditUi.describe(original, original, isNew = false),
            onBack = {}, onDelete = {}, onCategory = {}, onStartChange = { _, _ -> }, onEndChange = { _, _ -> },
            onToggleDay = {}, onPreset = {}, onSave = {},
        )
    }
}

@Preview(name = "New Study snoozer (starting times)", widthDp = 360, heightDp = 760, showBackground = true,
    backgroundColor = 0xFFFAF6EC)
@Composable
private fun NewStudySnoozerPreview() {
    val form = EditForm.new(null, Category.Study)   // 19:00-21:00 on weekdays
    SnoozerTheme {
        EditContent(
            state = EditUi.describe(form, form, isNew = true),
            onBack = {}, onDelete = {}, onCategory = {}, onStartChange = { _, _ -> }, onEndChange = { _, _ -> },
            onToggleDay = {}, onPreset = {}, onSave = {},
        )
    }
}

@Preview(name = "New, can't save yet", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun NewSnoozerPreview() {
    val form = EditForm.new(null).copy(days = emptyList())   // no day picked: Save explains why it's off
    SnoozerTheme {
        EditContent(
            state = EditUi.describe(form, form, isNew = true),
            onBack = {}, onDelete = {}, onCategory = {}, onStartChange = { _, _ -> }, onEndChange = { _, _ -> },
            onToggleDay = {}, onPreset = {}, onSave = {},
        )
    }
}

/** An optional name ("MATH 101"), shown on the week card and in the heads-up. */
@Composable
private fun NameCard(name: String, onName: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DoodleSection(title = "Name", seed = 95) {
        BasicTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            textStyle = SnoozerTheme.type.body.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Name, optional" },
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .sketchyBorder(scheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 96)
                        .padding(horizontal = Spacing.M, vertical = Spacing.S),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (name.isEmpty()) {
                        Text("Optional, e.g. MATH 101", style = SnoozerTheme.type.body, color = scheme.onSurfaceVariant)
                    }
                    field()
                }
            },
        )
    }
}

/** For a snoozer with dates (an imported class): when it runs, and a way to make it weekly for good. */
@Composable
private fun DatesCard(dates: String, onClear: () -> Unit) {
    DoodleSection(title = "Dates", seed = 97) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(painterResource(R.drawable.ic_doodle_calendar), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(dates, style = SnoozerTheme.type.headline)
        }
        Text(
            "Snoozer only quiets your phone between these dates.",
            style = SnoozerTheme.type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DoodleButton(onClick = onClear, seed = 98, wobble = DoodleDefaults.WobbleLight, containerColor = Color.Transparent) {
            Text("Repeat every week instead")
        }
    }
}
