package com.example.snoozer.report

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snoozer.R
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.quietLevel
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleCard
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.DoodleSection
import com.example.snoozer.ui.doodle.Illustrations
import com.example.snoozer.ui.doodle.ProvideMotionSetting
import com.example.snoozer.ui.doodle.bob
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.illustration
import com.example.snoozer.ui.doodle.rememberMotionEnabled
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/**
 * The weekly report: last week's quiet time, for Work and for Study. Stateless. Everything
 * moves in once when it opens (numbers count up, the ring draws, cards rise, bars grow, a star
 * pops), all driven by one clock; with "Remove animations" on it simply shows the result.
 */
@Composable
fun ReportContent(state: ReportUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ProvideMotionSetting {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
                ReportTopBar(
                    onBack = onBack,
                    subtitle = when (state) {
                        is ReportUiState.Ready -> state.range
                        is ReportUiState.FirstWeek -> "Your first week"
                        ReportUiState.Loading -> null
                    },
                )
                when (state) {
                    ReportUiState.Loading -> Spacer(Modifier.weight(1f))
                    is ReportUiState.FirstWeek -> FirstWeek(state, Modifier.weight(1f))
                    is ReportUiState.Ready -> Report(state, Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- The reveal clock ----------------------------------------------------------------------

/**
 * 0 to 1 over [RevealMillis] once [key] is first shown; each part of the screen plays during its
 * own slice of it ([slice]). Starts at 1 with animations off, so nothing ever waits.
 */
@Composable
private fun rememberReveal(key: Any): State<Float> {
    val motion = rememberMotionEnabled()
    val reveal = remember(key) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(key, motion) {
        if (motion) reveal.animateTo(1f, tween(RevealMillis, easing = LinearEasing)) else reveal.snapTo(1f)
    }
    return reveal.asState()
}

/** Where [p] is within [from]..[to], eased: 0 before, 1 after. */
private fun slice(p: Float, from: Float, to: Float): Float =
    FastOutSlowInEasing.transform(((p - from) / (to - from)).coerceIn(0f, 1f))

private const val RevealMillis = 2800

// ---- Pieces --------------------------------------------------------------------------------

@Composable
private fun ReportTopBar(onBack: () -> Unit, subtitle: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        DoodleButton(onClick = onBack, seed = 801, wobble = DoodleDefaults.WobbleLight) {
            Icon(painterResource(R.drawable.ic_doodle_back), contentDescription = null)
            Spacer(Modifier.width(Spacing.XS))
            Text("Back")
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text("Weekly report", style = SnoozerTheme.type.title)
            if (subtitle != null) {
                Text(subtitle, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Report(state: ReportUiState.Ready, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(state.range)
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Hero(state, reveal)
        Ring(state, reveal)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            CategoryCard(state.work, reveal, from = 0.30f, Modifier.weight(1f))
            CategoryCard(state.study, reveal, from = 0.38f, Modifier.weight(1f))
        }
        DayBars(state.days, reveal)
        if (state.highlights.isNotEmpty()) Highlights(state.highlights, reveal)
        state.notes.forEach { Note(it) }
        Footer(state.nextReport)
        if (state.earlier.isNotEmpty()) EarlierWeeks(state.earlier)
        Spacer(Modifier.height(Spacing.M))
    }
}

/** A number of minutes counting up as [p] goes 0 to 1. Screen readers get the final value. */
@Composable
private fun CountUp(minutes: Int, p: () -> Float, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val shown by remember(minutes) { derivedStateOf { (minutes * p()).toInt() } }
    val final = AlarmTime.formatDuration(minutes)
    Text(
        AlarmTime.formatDuration(shown),
        style = style,
        color = color,
        modifier = Modifier.clearAndSetSemantics { contentDescription = final },
    )
}

@Composable
private fun Hero(state: ReportUiState.Ready, reveal: State<Float>) {
    val ink = Color(0xFF2E2B26) // dark ink on the yellow card in both themes
    Row(
        Modifier
            .fillMaxWidth()
            .doodleFill(SnoozerTheme.colors.bell, SnoozerTheme.colors.bell.copy(alpha = 0.5f), seed = 810)
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, seed = 810)
            .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        DoodleIllustration(
            image = Illustrations.Guard,
            size = 84.dp,
            round = true,
            backing = Color(0xFFF3F0E8),
            outline = ink,
            imageScale = 1f,
            seed = 811,
            modifier = Modifier.bob(),
        )
        Column(Modifier.weight(1f)) {
            Text("Your phone stayed quiet for", style = SnoozerTheme.type.body, color = ink)
            CountUp(state.totalMinutes, { slice(reveal.value, 0.05f, 0.55f) }, SnoozerTheme.type.headline.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize), ink)
            if (state.change != null) {
                val arrow = when (state.change.up) { true -> "▲ "; false -> "▼ "; null -> "" }
                Text(arrow + state.change.text, style = SnoozerTheme.type.caption, color = ink)
            }
        }
    }
}

/** Work and Study as one ring: Work draws first, then Study, with the legend beside it. */
@Composable
private fun Ring(state: ReportUiState.Ready, reveal: State<Float>) {
    val work = SnoozerTheme.colors.forCategory(Category.Work).hatch
    val study = SnoozerTheme.colors.forCategory(Category.Study).hatch
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val share = state.workShare
    val empty = state.totalMinutes == 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp).clearAndSetSemantics {}) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                if (!empty) {
                    val p = reveal.value
                    val gap = if (share in 0.01f..0.99f) 6f else 0f // a small gap where they meet
                    val workSweep = (360f * share - gap).coerceAtLeast(0f) * slice(p, 0.10f, 0.50f)
                    val studySweep = (360f * (1 - share) - gap).coerceAtLeast(0f) * slice(p, 0.50f, 0.70f)
                    if (workSweep > 0f) drawArc(work, -90f, workSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    if (studySweep > 0f) drawArc(study, -90f + 360f * share, studySweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val lead = if (share >= 0.5f) Category.Work else Category.Study
                val pct = Math.round(if (lead == Category.Work) share * 100 else (1 - share) * 100)
                Text(if (empty) "–" else "$pct%", style = SnoozerTheme.type.title)
                if (!empty) Text(lead.label, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            LegendLine(Category.Work, state.work.minutes, work)
            LegendLine(Category.Study, state.study.minutes, study)
        }
    }
}

@Composable
private fun LegendLine(category: Category, minutes: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(
            Modifier.size(12.dp)
                .doodleFill(color, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
                .sketchyBorder(MaterialTheme.colorScheme.onSurface, width = 1.dp, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
        )
        Text("${category.label} ${AlarmTime.formatDuration(minutes)}", style = SnoozerTheme.type.body)
    }
}

/** One category: its drawing, its hours counting up, sessions and the change. Rises in. */
@Composable
private fun CategoryCard(report: CategoryReportUi, reveal: State<Float>, from: Float, modifier: Modifier = Modifier) {
    val colors = SnoozerTheme.colors.forCategory(report.category)
    DoodleCard(
        colors = colors,
        seed = 820 + report.category.ordinal,
        wobble = DoodleDefaults.WobbleLight,
        modifier = modifier.graphicsLayer {
            val t = slice(reveal.value, from, from + 0.2f)
            alpha = t
            translationY = (1 - t) * 16.dp.toPx()
        },
    ) {
        Column(Modifier.padding(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                DoodleIllustration(report.category.illustration, size = 52.dp, seed = 830 + report.category.ordinal)
                Column {
                    Text(report.category.label, style = SnoozerTheme.type.label)
                    Text(report.category.quietLevel.label.lowercase(), style = SnoozerTheme.type.caption)
                }
            }
            CountUp(report.minutes, { slice(reveal.value, from, from + 0.3f) }, SnoozerTheme.type.headline, colors.content)
            Text(listOfNotNull(report.sessions, report.change).joinToString(" · "), style = SnoozerTheme.type.caption)
        }
    }
}

/** Stacked Work/Study bars per day, growing in one after another; a star on the quietest. */
@Composable
private fun DayBars(days: List<DayBarUi>, reveal: State<Float>) {
    val work = SnoozerTheme.colors.forCategory(Category.Work).hatch
    val study = SnoozerTheme.colors.forCategory(Category.Study).hatch
    val max = days.maxOf { it.totalMinutes }.coerceAtLeast(1)
    val summary = days.joinToString("; ") { d ->
        "${d.name}: " + if (d.totalMinutes == 0) "nothing" else
            listOfNotNull(
                d.workMinutes.takeIf { it > 0 }?.let { "Work ${AlarmTime.formatDuration(it)}" },
                d.studyMinutes.takeIf { it > 0 }?.let { "Study ${AlarmTime.formatDuration(it)}" },
            ).joinToString(", ")
    }
    DoodleSection(title = "Day by day", seed = 840) {
        Row(
            // Wraps its content (bars share the bottom edge), so the star and the day letters always
            // fit, at any font size.
            Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary },
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "★",
                        style = SnoozerTheme.type.title,
                        color = SnoozerTheme.colors.bell,
                        modifier = Modifier.graphicsLayer {
                            val t = if (d.isQuietest) slice(reveal.value, 0.85f, 0.95f) else 0f
                            alpha = t
                            scaleX = 0.4f + 0.6f * t
                            scaleY = 0.4f + 0.6f * t
                        },
                    )
                    val start = 0.50f + i * 0.05f
                    Column(
                        Modifier
                            .width(18.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleY = slice(reveal.value, start, start + 0.2f)
                            },
                    ) {
                        if (d.studyMinutes > 0) Bar(study, BarAreaHeight * (d.studyMinutes.toFloat() / max))
                        if (d.workMinutes > 0) Bar(work, BarAreaHeight * (d.workMinutes.toFloat() / max))
                    }
                    Text(d.letter, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height)
            .doodleFill(color, cornerRadius = 3.dp, wobble = 0.dp)
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, width = 1.dp, cornerRadius = 3.dp, wobble = 0.dp)
    )
}

private val BarAreaHeight = 96.dp

@Composable
private fun Highlights(items: List<HighlightUi>, reveal: State<Float>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        items.forEachIndexed { i, h ->
            val from = 0.75f + i * 0.06f
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val t = slice(reveal.value, from, from + 0.15f)
                        alpha = t
                        translationY = (1 - t) * 12.dp.toPx()
                    }
                    .doodleFill(MaterialTheme.colorScheme.surface, wobble = DoodleDefaults.WobbleLight, seed = 850 + i)
                    .sketchyBorder(MaterialTheme.colorScheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 850 + i)
                    .padding(Spacing.S)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(painterResource(h.icon.drawable()), contentDescription = null, modifier = Modifier.size(28.dp),
                    tint = if (h.icon == HighlightIcon.Trophy) SnoozerTheme.colors.bell else MaterialTheme.colorScheme.onSurface)
                Text(h.title, style = SnoozerTheme.type.caption, textAlign = TextAlign.Center)
                Text(h.value, style = SnoozerTheme.type.label, textAlign = TextAlign.Center)
                Text(h.detail, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun HighlightIcon.drawable(): Int = when (this) {
    HighlightIcon.Trophy -> R.drawable.ic_doodle_trophy
    HighlightIcon.Calendar -> R.drawable.ic_doodle_calendar
    HighlightIcon.Flame -> R.drawable.ic_doodle_flame
}

/** Planned quiet that didn't happen, or sound turned back on: said plainly, not hidden. */
@Composable
private fun Note(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .doodleFill(MaterialTheme.colorScheme.surface, wobble = DoodleDefaults.WobbleLight, seed = 860)
            .sketchyBorder(MaterialTheme.colorScheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 860)
            .padding(Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Icon(painterResource(R.drawable.ic_doodle_warning), contentDescription = null, tint = SnoozerTheme.colors.bell, modifier = Modifier.size(24.dp))
        Text(text, style = SnoozerTheme.type.body)
    }
}

@Composable
private fun Footer(nextReport: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_doodle_clock), contentDescription = null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(nextReport, style = SnoozerTheme.type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EarlierWeeks(weeks: List<EarlierWeekUi>) {
    DoodleSection(title = "Earlier weeks", seed = 870) {
        weeks.forEach { w ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(w.range, style = SnoozerTheme.type.body, modifier = Modifier.weight(1f))
                Text(w.total, style = SnoozerTheme.type.label)
            }
        }
    }
}

@Composable
private fun FirstWeek(state: ReportUiState.FirstWeek, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M, Alignment.CenterVertically),
    ) {
        DoodleIllustration(
            image = Illustrations.Guard,
            size = 150.dp,
            round = true,
            backing = SnoozerTheme.colors.bell,
            outline = MaterialTheme.colorScheme.onBackground,
            imageScale = 1f,
            seed = 880,
            modifier = Modifier.bob(),
        )
        Text("Your first report arrives Monday", style = SnoozerTheme.type.title, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(
            "Snoozer is counting this week's quiet time now. On Monday at 00:00 you'll see how much you got for Work and for Study.",
            style = SnoozerTheme.type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text("So far this week: ${state.soFar}", style = SnoozerTheme.type.label)
        Footer(state.nextReport)
    }
}

// ---- Previews -----------------------------------------------------------------------------

internal val PreviewReport = ReportUiState.Ready(
    range = "Mon 17 – Sun 23 Nov",
    totalMinutes = 860,
    change = ChangeUi("2h 10m more than the week before", up = true),
    work = CategoryReportUi(Category.Work, 590, "5 sessions", "▲ 1h 40m"),
    study = CategoryReportUi(Category.Study, 270, "3 sessions", "▲ 30m"),
    days = listOf(
        DayBarUi("M", "Monday", 120, 90, false), DayBarUi("T", "Tuesday", 60, 0, false),
        DayBarUi("W", "Wednesday", 160, 60, false), DayBarUi("T", "Thursday", 130, 120, true),
        DayBarUi("F", "Friday", 120, 0, false), DayBarUi("S", "Saturday", 0, 0, false), DayBarUi("S", "Sunday", 0, 0, false),
    ),
    highlights = listOf(
        HighlightUi(HighlightIcon.Trophy, "Longest stretch", "2h 40m", "Work · Wed"),
        HighlightUi(HighlightIcon.Calendar, "Quietest day", "Thu", "4h 10m"),
        HighlightUi(HighlightIcon.Flame, "Study days", "3 of 7", "Mon · Wed · Thu"),
    ),
    notes = listOf("You turned the sound back on during 1 quiet time (Tue, Work)."),
    nextReport = "Next report Monday 00:00 · in 3 days",
    earlier = listOf(EarlierWeekUi("10 – 16 Nov", "12h 10m"), EarlierWeekUi("3 – 9 Nov", "13h 5m")),
)

@Preview(name = "Report - light", widthDp = 360, heightDp = 1300, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Report - dark", widthDp = 360, heightDp = 1300, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportPreview() {
    SnoozerTheme { ReportContent(PreviewReport, onBack = {}) }
}

@Preview(name = "Report - first week", widthDp = 360, heightDp = 700, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FirstWeekPreview() {
    SnoozerTheme { ReportContent(ReportUiState.FirstWeek("3h 20m", "Next report Monday 00:00 · in 3 days"), onBack = {}) }
}

/**
 * The week screen's way in: "Last week · 14h 20m quiet" with the split under it and a "›". Sits
 * just under the status card (easy to spot, away from the + button and the swipeable pages),
 * full width and 48dp or more tall. A yellow dot marks a report not opened yet.
 */
@Composable
fun ReportStrip(strip: ReportStripUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth()) {
        DoodleButton(
            onClick = onClick,
            seed = 890,
            wobble = DoodleDefaults.WobbleLight,
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            borderColor = scheme.outline,
            // The button keeps its own semantics (click, role); its content speaks as one line.
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_doodle_chart), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(Spacing.S))
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = strip.description }) {
                Text(strip.title, style = SnoozerTheme.type.label)
                if (strip.detail != null) {
                    Text(strip.detail, style = SnoozerTheme.type.caption, color = scheme.onSurfaceVariant)
                }
            }
            Text("›", style = SnoozerTheme.type.title, modifier = Modifier.clearAndSetSemantics {})
        }
        if (strip.isNew) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(14.dp)
                    .clearAndSetSemantics {}
                    .doodleFill(SnoozerTheme.colors.bell, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
                    .sketchyBorder(scheme.background, width = 2.dp, cornerRadius = DoodleDefaults.Round, wobble = 0.dp)
            )
        }
    }
}
