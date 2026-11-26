package com.example.snoozer.dayPickers

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.highlighter
import com.example.snoozer.ui.doodle.rememberMotionEnabled
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.abs

/**
 * The month calendar: ‹ December 2026 ›, a grid of days with a Work and a Study mark on days
 * something starts, a flag on a class's last day, and the picked day's cards under it.
 * Swipe the grid (or tap the arrows) to change month. Stateless.
 */
@Composable
fun MonthContent(
    state: MonthUiState,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onCardClick: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = rememberMotionEnabled()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // Bottom padding keeps the last card clear of the floating + button.
        contentPadding = PaddingValues(start = Spacing.M, end = Spacing.M, top = Spacing.S, bottom = 104.dp),
    ) {
        item(key = "header") {
            MonthHeader(state.title, onPrev = { onMonth(state.month.minusMonths(1)) }, onNext = { onMonth(state.month.plusMonths(1)) })
        }
        item(key = "grid") {
            // Slides one way or the other with the month; a plain swap when animations are off.
            AnimatedContent(
                targetState = state,
                contentKey = { it.month },
                transitionSpec = {
                    val forward = targetState.month > initialState.month
                    if (!motion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else slideInHorizontally(tween(250)) { if (forward) it else -it } togetherWith
                        slideOutHorizontally(tween(250)) { if (forward) -it else it }
                },
                label = "month",
            ) { s ->
                MonthGrid(s, onSelect = onSelect, onSwipe = { forward -> onMonth(if (forward) s.month.plusMonths(1) else s.month.minusMonths(1)) })
            }
        }
        item(key = "legend") { Legend(Modifier.padding(top = Spacing.S, bottom = Spacing.M)) }
        item(key = "dayTitle") {
            Column(Modifier.padding(bottom = Spacing.S).semantics(mergeDescendants = true) { heading() }) {
                Text(state.selected.title, style = SnoozerTheme.type.title)
                state.selected.notes.forEach { note ->
                    Text(note, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val cards = state.selected.cards
        if (cards.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing mutes your phone that day.",
                    style = SnoozerTheme.type.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(cards, key = { _, c -> "card-${c.id}" }) { index, card ->
                TimelineItem(
                    card = card,
                    isFirst = index == 0,
                    isLast = index == cards.lastIndex,
                    onClick = { onCardClick(card.id) },
                    onToggle = { onToggle(card.id, it) },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(title: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthArrow(forward = false, onClick = onPrev)
        Text(
            title,
            style = SnoozerTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        MonthArrow(forward = true, onClick = onNext)
    }
}

@Composable
private fun MonthArrow(forward: Boolean, onClick: () -> Unit) {
    DoodleButton(
        onClick = onClick,
        seed = if (forward) 731 else 730,
        wobble = DoodleDefaults.WobbleLight,
        containerColor = Color.Transparent,
        borderColor = Color.Transparent,
        modifier = Modifier.semantics { contentDescription = if (forward) "Next month" else "Previous month" },
    ) {
        // The expand chevron points down; turned to point left or right.
        Icon(
            painterResource(R.drawable.ic_doodle_expand),
            contentDescription = null,
            modifier = Modifier.size(24.dp).rotate(if (forward) -90f else 90f),
        )
    }
}

@Composable
private fun MonthGrid(state: MonthUiState, onSelect: (LocalDate) -> Unit, onSwipe: (forward: Boolean) -> Unit) {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val dragged = remember { mutableFloatStateOf(0f) }
    val swipe by rememberUpdatedState(onSwipe)
    Column(
        Modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { dragged.floatValue += it },
                orientation = Orientation.Horizontal,
                onDragStarted = { dragged.floatValue = 0f },
                onDragStopped = {
                    // A firm swipe left goes to next month, right to the previous one.
                    if (abs(dragged.floatValue) > threshold) swipe(dragged.floatValue < 0)
                    dragged.floatValue = 0f
                },
            ),
    ) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            state.dayLetters.forEach { letter ->
                Text(
                    letter,
                    style = SnoozerTheme.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell -> DayCell(cell, onClick = { onSelect(cell.date) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(cell: DayCellUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val work = SnoozerTheme.colors.forCategory(Category.Work)
    val study = SnoozerTheme.colors.forCategory(Category.Study)
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = cell.isSelected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = cell.description + if (cell.isToday) ", today" else "" }
            .padding(vertical = Spacing.XS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clearAndSetSemantics {}
                .then(
                    if (cell.isToday) Modifier.sketchyBorder(
                        scheme.onBackground, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                        wobble = DoodleDefaults.WobbleLight, seed = cell.date.dayOfMonth,
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                cell.number,
                style = SnoozerTheme.type.label,
                color = when {
                    !cell.inMonth -> scheme.outline
                    cell.isSelected || cell.isToday -> scheme.onBackground
                    else -> scheme.onSurfaceVariant
                },
                modifier = Modifier
                    .then(if (cell.isSelected) Modifier.highlighter(SnoozerTheme.colors.highlighter) else Modifier)
                    .padding(horizontal = 4.dp),
            )
        }
        // Work and Study marks; a pink tick under them on a class's last day.
        Row(Modifier.height(8.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (cell.work) Mark(work.container, scheme.onSurface)
            if (cell.study) Mark(study.container, scheme.onSurface)
        }
        Box(
            Modifier.size(width = 14.dp, height = 3.dp).clearAndSetSemantics {}
                .then(if (cell.endsHere) Modifier.doodleFill(EndColor, cornerRadius = 2.dp, wobble = 0.dp) else Modifier)
        )
    }
}

@Composable
private fun Mark(fill: Color, ink: Color) {
    Box(
        Modifier.size(8.dp)
            .doodleFill(fill, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
            .sketchyBorder(ink, width = 1.dp, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
    )
}

/** What the marks mean, under the grid. Read out once as a sentence. */
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Mark(SnoozerTheme.colors.forCategory(Category.Work).container, scheme.onSurface)
            Text("Work", style = SnoozerTheme.type.caption)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Mark(SnoozerTheme.colors.forCategory(Category.Study).container, scheme.onSurface)
            Text("Study", style = SnoozerTheme.type.caption)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Box(Modifier.size(width = 14.dp, height = 3.dp).doodleFill(EndColor, cornerRadius = 2.dp, wobble = 0.dp))
            Text("Last class", style = SnoozerTheme.type.caption)
        }
    }
}

// The same pink as the onboarding "now" line and the "+" ring.
private val EndColor = Color(0xFFC2185B)

// ---- Previews -----------------------------------------------------------------------------

private val PreviewNow: LocalDateTime = LocalDateTime.of(2026, 11, 18, 9, 20)
private val PreviewAlarms = listOf(
    Alarm(1, 9, 0, 17, 0, DayOfWeek.entries.take(5), work = "Work"),
    Alarm(2, 10, 30, 11, 20, listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), work = "Study",
        name = "MATH 101 · Lecture", fromDate = LocalDate.of(2026, 9, 8), untilDate = LocalDate.of(2026, 12, 4)),
    Alarm(3, 14, 30, 16, 20, listOf(DayOfWeek.TUESDAY), work = "Study",
        name = "CS 135 · Lab", fromDate = LocalDate.of(2026, 9, 8), untilDate = LocalDate.of(2026, 12, 1)),
)

@Preview(name = "Month - light", widthDp = 360, heightDp = 860, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Month - dark", widthDp = 360, heightDp = 860, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthPreview() {
    SnoozerTheme {
        MonthContent(
            MonthUi.build(PreviewAlarms, PreviewNow, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 18)),
            onMonth = {}, onSelect = {}, onCardClick = {}, onToggle = { _, _ -> },
        )
    }
}

@Preview(name = "Month - term end", widthDp = 360, heightDp = 860, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun MonthTermEndPreview() {
    SnoozerTheme {
        MonthContent(
            MonthUi.build(PreviewAlarms, PreviewNow, YearMonth.of(2026, 12), LocalDate.of(2026, 12, 4)),
            onMonth = {}, onSelect = {}, onCardClick = {}, onToggle = { _, _ -> },
        )
    }
}
