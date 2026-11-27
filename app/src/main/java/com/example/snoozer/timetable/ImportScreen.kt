package com.example.snoozer.timetable

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import androidx.compose.ui.res.stringResource
import com.example.snoozer.util.rememberStrings
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.quietLevel
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleCard
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.illustration
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/**
 * The import preview: the classes found in a calendar file, each with a checkbox, one
 * Work / Study choice for all of them, and "Add 6 classes" at the bottom. Stateless.
 */
@Composable
fun ImportContent(
    state: ImportUiState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onCategory: (Category) -> Unit,
    onAdd: () -> Unit,
    onPickAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // On the unfolded Fold the list stays phone-width, centered.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            ImportTopBar(onBack)
            when (state) {
                ImportUiState.Loading -> Message(Modifier.weight(1f)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.import_reading), style = SnoozerTheme.type.body)
                }
                is ImportUiState.Failed -> Message(Modifier.weight(1f)) {
                    Text(state.message, style = SnoozerTheme.type.body, textAlign = TextAlign.Center)
                    DoodleButton(onClick = onPickAnother, seed = 701) { Text(stringResource(R.string.import_pick_another)) }
                }
                is ImportUiState.Ready -> if (state.rows.isEmpty()) {
                    Message(Modifier.weight(1f)) {
                        Text(stringResource(R.string.import_no_weekly), style = SnoozerTheme.type.title, textAlign = TextAlign.Center)
                        if (state.skipped != null) {
                            Text(state.skipped, style = SnoozerTheme.type.body, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DoodleButton(onClick = onPickAnother, seed = 702) { Text(stringResource(R.string.import_pick_another)) }
                    }
                } else {
                    ReadyList(state, onToggle, onCategory, Modifier.weight(1f))
                    AddBar(state, onAdd)
                }
            }
        }
    }
}

@Composable
private fun ImportTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        DoodleButton(onClick = onBack, seed = 703, wobble = DoodleDefaults.WobbleLight) {
            Icon(painterResource(R.drawable.ic_doodle_back), contentDescription = null)
            Spacer(Modifier.width(Spacing.XS))
            Text(stringResource(R.string.back))
        }
        Text(stringResource(R.string.import_timetable), style = SnoozerTheme.type.title, modifier = Modifier.weight(1f).semantics { heading() })
    }
}

@Composable
private fun Message(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun ReadyList(
    state: ImportUiState.Ready,
    onToggle: (String) -> Unit,
    onCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        item(key = "found") {
            Text(state.foundLine(rememberStrings()), style = SnoozerTheme.type.headline, modifier = Modifier.semantics { heading() })
        }
        item(key = "kind") { KindPicker(state.category, onCategory) }
        items(state.rows, key = { it.key }) { row ->
            ClassRow(row, state.category, onToggle = { onToggle(row.key) })
        }
        if (state.skipped != null) {
            item(key = "skipped") {
                Text(
                    state.skipped,
                    style = SnoozerTheme.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.S),
                )
            }
        }
    }
}

/** "Add them as": Study (silent) or Work (vibrate), for every class in the list. */
@Composable
private fun KindPicker(selected: Category, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.import_add_as), style = SnoozerTheme.type.label)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            // Study first: a timetable is usually classes.
            listOf(Category.Study, Category.Work).forEachIndexed { i, category ->
                val isSelected = category == selected
                val colors = SnoozerTheme.colors.forCategory(category)
                Row(
                    Modifier
                        .weight(1f)
                        .doodleFill(if (isSelected) colors.container else MaterialTheme.colorScheme.surface,
                            if (isSelected) colors.hatch else null, wobble = DoodleDefaults.WobbleLight, seed = 710 + i)
                        .sketchyBorder(if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            width = if (isSelected) 2.5.dp else 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 710 + i)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(category) })
                        .padding(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    DoodleIllustration(category.illustration, size = 44.dp, dimmed = !isSelected, seed = 712 + i)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(category.labelRes), style = SnoozerTheme.type.label,
                            color = if (isSelected) colors.content else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(category.quietLevel.labelRes), style = SnoozerTheme.type.caption,
                            color = if (isSelected) colors.content else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One class: a checkbox, its name, days and times, and its dates. The whole row toggles. */
@Composable
private fun ClassRow(row: ImportRowUi, category: Category, onToggle: () -> Unit) {
    val colors = SnoozerTheme.colors.forCategory(category)
    val ink = MaterialTheme.colorScheme.onSurface
    val included = row.checked && !row.alreadyAdded
    DoodleCard(
        colors = if (included) colors else colors.copy(container = MaterialTheme.colorScheme.surface, hatch = Color.Transparent,
            content = MaterialTheme.colorScheme.onSurfaceVariant),
        seed = row.key.hashCode(),
        wobble = DoodleDefaults.WobbleLight,
        modifier = Modifier.fillMaxWidth().alpha(if (row.alreadyAdded) 0.6f else 1f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = included, enabled = !row.alreadyAdded, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            DoodleCheckBox(checked = included, ink = ink, seed = row.key.hashCode() + 1)
            Column(Modifier.weight(1f)) {
                Text(row.name, style = SnoozerTheme.type.label)
                Text(stringResource(if (row.endsNextDay) R.string.import_row_line_next_day else R.string.import_row_line, row.days, row.time),
                    style = SnoozerTheme.type.body)
                val note = if (row.alreadyAdded) stringResource(R.string.import_already_added) else row.dates
                if (note != null) Text(note, style = SnoozerTheme.type.caption)
            }
        }
    }
}

/** A drawn square with a pencil tick. Decorative: the row says checked or not. */
@Composable
private fun DoodleCheckBox(checked: Boolean, ink: Color, seed: Int) {
    Box(
        Modifier.size(28.dp)
            .clearAndSetSemantics {}
            .doodleFill(if (checked) SnoozerTheme.colors.bell else Color.Transparent, cornerRadius = 6.dp, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .sketchyBorder(ink, width = 2.dp, cornerRadius = 6.dp, wobble = DoodleDefaults.WobbleLight, seed = seed),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(painterResource(R.drawable.ic_doodle_check), contentDescription = null, tint = Color(0xFF2E2B26),
                modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AddBar(state: ImportUiState.Ready, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.M)) {
        if (state.limitNote != null) {
            Text(state.limitNote, style = SnoozerTheme.type.body, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.S))
        }
        DoodleButton(
            onClick = onAdd,
            enabled = state.addCount > 0 && !state.saving && state.limitNote == null,
            modifier = Modifier.fillMaxWidth(),
            containerColor = SnoozerTheme.colors.bell,
            contentColor = Color(0xFF2E2B26),
            hatchColor = MaterialTheme.colorScheme.primary,
            seed = 720,
        ) { Text(state.addLabel(rememberStrings())) }
    }
}

// ---- Previews -----------------------------------------------------------------------------

private val PreviewRows = listOf(
    ImportRowUi("a", "MATH 101 · Lecture", "09:30 – 10:20", false, "Mon, Wed, Fri", "14 Sep – 11 Dec", checked = true, alreadyAdded = false),
    ImportRowUi("b", "CS 135 · Lab", "14:30 – 16:20", false, "Tue", "15 Sep – 8 Dec", checked = true, alreadyAdded = false),
    ImportRowUi("c", "ENGL 109", "18:30 – 21:20", false, "Thu", "from 17 Sep", checked = false, alreadyAdded = false),
    ImportRowUi("d", "PHYS 121 · Tutorial", "08:30 – 09:20", false, "Fri", "18 Sep – 11 Dec", checked = true, alreadyAdded = true),
)

@Preview(name = "Import - ready", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Import - ready, dark", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImportReadyPreview() {
    SnoozerTheme {
        ImportContent(
            ImportUiState.Ready(PreviewRows, Category.Study, skipped = "3 skipped: 2 all-day, 1 not weekly"),
            onBack = {}, onToggle = {}, onCategory = {}, onAdd = {}, onPickAnother = {},
        )
    }
}

@Preview(name = "Import - nothing found", widthDp = 360, heightDp = 500, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun ImportEmptyPreview() {
    SnoozerTheme {
        ImportContent(
            ImportUiState.Ready(emptyList(), Category.Study, skipped = "4 skipped: 3 one-off events, 1 all-day"),
            onBack = {}, onToggle = {}, onCategory = {}, onAdd = {}, onPickAnother = {},
        )
    }
}

@Preview(name = "Import - failed", widthDp = 360, heightDp = 500, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun ImportFailedPreview() {
    SnoozerTheme {
        ImportContent(
            ImportUiState.Failed("Couldn't read that file. Is it a calendar (.ics) file?"),
            onBack = {}, onToggle = {}, onCategory = {}, onAdd = {}, onPickAnother = {},
        )
    }
}
