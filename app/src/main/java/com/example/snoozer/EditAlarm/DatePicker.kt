package com.example.snoozer.EditAlarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A month calendar for picking the one date of a snoozer. Days before [today] are faded and
 * can't be picked, and months before this one can't be opened; [selected] is filled yellow and
 * today is circled. The month shown starts at the selected date's and survives rotation.
 */
@Composable
fun OneDateCalendar(selected: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val thisMonth = YearMonth.from(today)
    var shownText by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: today).toString()) }
    val shown = YearMonth.parse(shownText).let { if (it < thisMonth) thisMonth else it }
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CalendarArrow(forward = false, enabled = shown > thisMonth) { shownText = shown.minusMonths(1).toString() }
            Text(
                "${shown.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${shown.year}",
                style = SnoozerTheme.type.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CalendarArrow(forward = true, enabled = true) { shownText = shown.plusMonths(1).toString() }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = SnoozerTheme.type.caption,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val first = shown.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = shown.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        Column(Modifier.selectableGroup()) {
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        DateCell(
                            date = date,
                            inMonth = YearMonth.from(date) == shown,
                            isToday = date == today,
                            isSelected = date == selected,
                            enabled = !date.isBefore(today),
                            onPick = { onPick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    DoodleButton(
        onClick = onClick,
        enabled = enabled,
        seed = if (forward) 951 else 950,
        wobble = DoodleDefaults.WobbleLight,
        containerColor = Color.Transparent,
        borderColor = Color.Transparent,
        modifier = Modifier.semantics { contentDescription = if (forward) "Next month" else "Previous month" },
    ) {
        Icon(
            painterResource(R.drawable.ic_doodle_expand),
            contentDescription = null,
            modifier = Modifier.size(22.dp).rotate(if (forward) -90f else 90f),
        )
    }
}

@Composable
private fun DateCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.dayOfMonth} " +
        date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + if (isToday) ", today" else ""
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onPick)
            // Said out loud too: a day that can't be picked is announced as unavailable.
            .semantics {
                contentDescription = label
                if (!enabled) disabled()
            }
            .alpha(if (!enabled) 0.3f else if (inMonth) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clearAndSetSemantics {}
                .then(
                    if (isSelected) Modifier.doodleFill(SnoozerTheme.colors.bell, cornerRadius = DoodleDefaults.Round, wobble = DoodleDefaults.WobbleLight, seed = date.dayOfMonth)
                    else Modifier
                )
                .then(
                    if (isToday || isSelected) Modifier.sketchyBorder(scheme.onSurface, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                        wobble = DoodleDefaults.WobbleLight, seed = date.dayOfMonth + 40)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = SnoozerTheme.type.label,
                // Dark ink on the yellow pick in both themes.
                color = if (isSelected) Color(0xFF2E2B26) else scheme.onSurface,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
