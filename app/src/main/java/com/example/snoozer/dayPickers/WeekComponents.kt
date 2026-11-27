package com.example.snoozer.dayPickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.util.pluralResource
import com.example.snoozer.util.rememberStrings
import java.time.DayOfWeek
import java.time.format.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleCard
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.illustration
import com.example.snoozer.ui.doodle.DoodleSwitch
import com.example.snoozer.ui.doodle.SketchyShape
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.highlighter
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.CategoryColors
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/**
 * The first thing on the screen: whether the phone is quiet now, or when it goes quiet next.
 * Yellow with the full wobble, since it is the one element that should stand out.
 */
@Composable
fun StatusCard(status: StatusUi, modifier: Modifier = Modifier, onAllowAccess: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val type = SnoozerTheme.type
    val yellow = CategoryColors(
        container = scheme.tertiary,
        content = scheme.onTertiary,
        hatch = scheme.onTertiary.copy(alpha = 0.3f),
    )
    DoodleCard(colors = yellow, seed = 41, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints {
            // On a narrow screen (a foldable's cover screen) the drawing beside the big time leaves
            // too little room, and "tomorrow" breaks mid-word, so the drawing goes above instead.
            val stacked = maxWidth < StatusStackBelow
            val art = @Composable {
                // While a window is running or coming up, its category's drawing (the study or work
                // character) leads the card; otherwise there's no category, so the clock stays.
                val category = when (status) {
                    is StatusUi.QuietNow -> status.category
                    is StatusUi.Next -> status.category
                    StatusUi.NothingScheduled, StatusUi.NoAccess -> null
                }
                if (category != null) {
                    DoodleIllustration(category.illustration, size = if (stacked) StatusArtSizeSmall else StatusArtSize, round = true, seed = 43)
                } else {
                    Icon(painterResource(R.drawable.ic_doodle_clock), contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
            val words = @Composable {
                Column {
                    when (status) {
                        is StatusUi.QuietNow -> {
                            Text(stringResource(R.string.status_quiet_now, stringResource(status.category.labelRes),
                                stringResource(status.level.labelRes)), style = type.caption)
                            Text(stringResource(R.string.status_until, status.until), style = type.headline)
                            Text(listOfNotNull(status.left, status.then).joinToString(" · "), style = type.caption)
                        }
                        is StatusUi.Next -> {
                            Text(stringResource(R.string.status_next), style = type.caption)
                            Text(stringResource(R.string.time_on_day, status.time, status.day), style = type.headline)
                            Text(stringResource(R.string.status_next_detail, stringResource(status.category.labelRes),
                                stringResource(status.level.labelRes), status.startsIn), style = type.caption)
                        }
                        StatusUi.NothingScheduled -> {
                            Text(stringResource(R.string.status_none_title), style = type.title)
                            Text(stringResource(R.string.status_none_body), style = type.caption)
                        }
                        // Without Do Not Disturb access nothing mutes, so say that instead of a schedule.
                        StatusUi.NoAccess -> {
                            Text(stringResource(R.string.status_paused_title), style = type.title)
                            Text(stringResource(R.string.status_paused_body), style = type.caption)
                            Spacer(Modifier.height(Spacing.S))
                            DoodleButton(onClick = onAllowAccess, seed = 42, wobble = DoodleDefaults.WobbleLight) {
                                Text(stringResource(R.string.allow_access))
                            }
                        }
                    }
                }
            }
            val merged = Modifier.padding(Spacing.M).semantics(mergeDescendants = true) {}
            if (stacked) {
                Column(merged, verticalArrangement = Arrangement.spacedBy(Spacing.S)) { art(); words() }
            } else {
                Row(
                    merged,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                ) { art(); words() }
            }
        }
    }
}

private val StatusArtSize = 76.dp
private val StatusArtSizeSmall = 56.dp

/** Below this card width the drawing sits above the words rather than beside them. */
private val StatusStackBelow = 250.dp

/**
 * The seven days as handwritten letters. Today is circled, the selected day gets a
 * highlighter swipe, and dots underneath show how many snoozers each day has (up to 3).
 */
@Composable
fun WeekStrip(days: List<DayUi>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val highlighter = SnoozerTheme.colors.highlighter
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.S)) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selected
            val count = day.cards.size
            val today = stringResource(R.string.list_today)
            val snoozers = stringResource(R.string.list_item, pluralResource(R.plurals.snoozer_count, count, count))
            val description = buildString {
                append(day.name)
                if (day.isToday) append(today)
                append(snoozers)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = description }
                    .padding(vertical = Spacing.XS),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clearAndSetSemantics {}
                        .then(
                            if (day.isToday) Modifier.sketchyBorder(
                                scheme.onBackground, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                                wobble = DoodleDefaults.WobbleLight, seed = 60 + index,
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.letter,
                        style = SnoozerTheme.type.label,
                        color = if (isSelected || day.isToday) scheme.onBackground else scheme.onSurfaceVariant,
                        modifier = Modifier
                            .then(if (isSelected) Modifier.highlighter(highlighter) else Modifier)
                            .padding(horizontal = 6.dp),
                    )
                }
                CountDots(count = count, color = if (isSelected) scheme.onBackground else scheme.outline)
            }
        }
    }
}

@Composable
private fun CountDots(count: Int, color: Color) {
    val shown = count.coerceAtMost(3)
    Canvas(Modifier.size(width = 22.dp, height = 6.dp).clearAndSetSemantics {}) {
        val radius = size.height / 2f
        val step = radius * 2f + 2.dp.toPx()
        val startX = size.width / 2f - (shown - 1) * step / 2f
        repeat(shown) { i -> drawCircle(color, radius, Offset(startX + i * step, size.height / 2f)) }
    }
}

/** "Monday · today" and the day's summary line. */
@Composable
fun DayHeader(day: DayUi, modifier: Modifier = Modifier) {
    val type = SnoozerTheme.type
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(day.name, style = type.title)
            if (day.isToday) {
                Text(stringResource(R.string.header_today), style = type.caption, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        if (day.summary != null) {
            Text(day.summary, style = type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One snoozer on the day's timeline: a dotted rail on the left joining the cards in time
 * order, and a category-colored card with the time range, duration, repeat days and an
 * on/off switch. Switched-off cards fade out but stay tappable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineItem(
    card: AlarmCardUi,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = SnoozerTheme.type
    val categoryColors = SnoozerTheme.colors.forCategory(card.category)
    // The switch shows what was tapped right away instead of waiting for the database to
    // send the new value back. Otherwise a quick off-then-on double tap computes both taps
    // from the old value and asks for "off" twice. Resets whenever the saved value changes.
    var checked by remember(card.enabled) { mutableStateOf(card.enabled) }
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineRail(active = card.activeNow, drawTop = !isFirst, drawBottom = !isLast)
        DoodleCard(
            colors = categoryColors,
            onClick = onClick,
            seed = card.id,
            wobble = DoodleDefaults.WobbleLight,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Spacing.M)
                .alpha(if (card.enabled && !card.ended) 1f else 0.55f),
        ) {
            Column(Modifier.padding(start = Spacing.M, end = Spacing.S, top = Spacing.S, bottom = Spacing.M)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Tag(stringResource(R.string.tag_category_level, stringResource(card.category.labelRes),
                        stringResource(card.level.labelRes)), seed = card.id * 3)
                    if (card.activeNow) Tag(stringResource(R.string.tag_quiet_now), seed = card.id * 3 + 1, highlighted = true)
                    Spacer(Modifier.weight(1f))
                    DoodleSwitch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            onToggle(it)
                        },
                        contentDescription = stringResource(R.string.card_switch,
                            listOfNotNull(card.name, stringResource(card.category.labelRes)).joinToString(", "), card.start, card.end),
                        seed = card.id * 5,
                    )
                }
                // The category's drawing beside the times: Work's or Study's, the same art as the
                // edit screen's cards. Decorative: the tag above already says which it is.
                Row(
                    modifier = Modifier.padding(top = Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CardArtGap),
                ) {
                    DoodleIllustration(
                        image = card.category.illustration,
                        size = CardArtSize,
                        seed = card.id * 7,
                    )
                    Column(Modifier.weight(1f)) {
                        // An imported class says which one it is.
                        if (card.name != null) Text(card.name, style = type.label, maxLines = 2)
                        Row(verticalAlignment = Alignment.Top) {
                            Text(stringResource(R.string.time_range, card.start, card.end), style = type.headline)
                            // Overnight windows end the next day; say so instead of looking like a typo.
                            if (card.endsNextDay) {
                                val nextDay = stringResource(R.string.next_day)
                                Text(
                                    "+1",
                                    style = type.caption,
                                    modifier = Modifier.padding(start = Spacing.XS).semantics { contentDescription = nextDay },
                                )
                            }
                        }
                        // Wraps the repeat letters under the duration when they don't fit (Korean day letters on a cover screen).
                        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Text(card.duration, style = type.caption, modifier = Modifier.align(Alignment.CenterVertically))
                            Text("·", style = type.caption, modifier = Modifier.align(Alignment.CenterVertically))
                            RepeatLetters(card.repeatsOn, card.repeatText)
                        }
                        if (card.dateNote != null) {
                            Text(card.dateNote, style = type.caption, modifier = Modifier.padding(top = Spacing.XS))
                        }
                    }
                }
            }
        }
    }
}

/** A dot for each card, joined by a dashed pencil line. The dot is filled while the window is active. */
@Composable
private fun TimelineRail(active: Boolean, drawTop: Boolean, drawBottom: Boolean) {
    val line = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.width(Spacing.L).fillMaxHeight().clearAndSetSemantics {}) {
        val x = size.width / 2f
        val dotY = 30.dp.toPx()
        val radius = 5.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val width = 1.5.dp.toPx()
        if (drawTop) drawLine(line, Offset(x, 0f), Offset(x, dotY - radius), width, StrokeCap.Round, dashes)
        if (drawBottom) drawLine(line, Offset(x, dotY + radius), Offset(x, size.height), width, StrokeCap.Round, dashes)
        if (active) drawCircle(fill, radius, Offset(x, dotY))
        drawCircle(ink, radius, Offset(x, dotY), style = Stroke(width))
    }
}

/** A small outlined label inside a card ("Work", "quiet now"). */
@Composable
private fun Tag(text: String, seed: Int, highlighted: Boolean = false) {
    val radius = 8.dp
    Text(
        text = text,
        style = SnoozerTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .then(
                if (highlighted) Modifier.doodleFill(SnoozerTheme.colors.highlighter, cornerRadius = radius,
                    wobble = DoodleDefaults.WobbleLight, seed = seed) else Modifier
            )
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, width = 1.dp, cornerRadius = radius,
                wobble = DoodleDefaults.WobbleLight, seed = seed)
            .padding(horizontal = Spacing.S, vertical = 2.dp),
    )
}

/** "M T W T F S S" with the repeat days in full ink and the rest faint. */
@Composable
private fun RepeatLetters(repeatsOn: List<Boolean>, description: String) {
    val strings = rememberStrings()
    val repeats = stringResource(R.string.repeats, description)
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = repeats },
    ) {
        DayOfWeek.entries.map { AlarmTime.dayName(it, TextStyle.NARROW, strings) }.forEachIndexed { i, letter ->
            Text(
                letter,
                style = SnoozerTheme.type.caption.copy(
                    fontWeight = if (repeatsOn[i]) FontWeight.Bold else FontWeight.Normal,
                ),
                modifier = Modifier.alpha(if (repeatsOn[i]) 1f else 0.35f),
            )
        }
    }
}

/** Shown on a day with no snoozers, so the page invites you to add one instead of looking broken. */
@Composable
fun EmptyDay(dayName: String, onAdd: () -> Unit, modifier: Modifier = Modifier, onImport: (() -> Unit)? = null) {
    val circle = remember { SketchyShape(cornerRadius = DoodleDefaults.Round, seed = 52) }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .sketchyBorder(MaterialTheme.colorScheme.onSurface, cornerRadius = DoodleDefaults.Round, seed = 52)
                .clip(circle)
                .doodleFill(SnoozerTheme.colors.illustrationBacking, cornerRadius = DoodleDefaults.Round, seed = 52),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_doodle_bell), contentDescription = null, modifier = Modifier.size(60.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.free_day_title), style = SnoozerTheme.type.title)
            Text(
                stringResource(R.string.free_day_body, dayName),
                style = SnoozerTheme.type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        DoodleButton(onClick = onAdd, seed = 53, wobble = DoodleDefaults.WobbleLight) {
            Icon(painterResource(R.drawable.ic_doodle_add), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.XS))
            Text(stringResource(R.string.add_snoozer))
        }
        // Classes are quicker to bring in from a calendar file than to type one by one.
        if (onImport != null) {
            DoodleButton(onClick = onImport, seed = 54, wobble = DoodleDefaults.WobbleLight,
                containerColor = Color.Transparent, borderColor = Color.Transparent) {
                Icon(painterResource(R.drawable.ic_doodle_import), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.XS))
                Text(stringResource(R.string.import_a_timetable))
            }
        }
    }
}

/** The Work or Study drawing on each snoozer card. */
private val CardArtSize = 72.dp

/** Room between the drawing and the times, so the left of the card doesn't feel crowded. */
private val CardArtGap = 32.dp
