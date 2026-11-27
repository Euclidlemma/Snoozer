package com.example.snoozer.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snoozer.R
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.ExplainIcon
import com.example.snoozer.alarm.ReminderText
import com.example.snoozer.alarm.explanation
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleIllustration
import com.example.snoozer.ui.doodle.DoodlePhone
import com.example.snoozer.ui.doodle.Illustrations
import com.example.snoozer.ui.doodle.bob
import com.example.snoozer.ui.doodle.buzz
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.illustration
import com.example.snoozer.ui.doodle.pulse
import com.example.snoozer.ui.doodle.rememberMotionEnabled
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing

/** The onboarding pages, in order. The pager shows them one by one. */
const val OnboardingPageCount = 5

@Composable
fun OnboardingPage(page: Int, mode: OnboardingMode = OnboardingMode.FirstRun, modifier: Modifier = Modifier) {
    // Each page scrolls on its own, so large fonts or the folded screen never clip it.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        when (page) {
            0 -> WelcomePage()
            1 -> TwoKindsPage()
            2 -> OverlapPage()
            3 -> HeadsUpPage()
            else -> WhereThingsArePage(mode)
        }
    }
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = SnoozerTheme.type.headline, modifier = Modifier.semantics { heading() })
}

@Composable
private fun PageNote(text: String) {
    Text(text, style = SnoozerTheme.type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The drawable for an explanation icon. */
@DrawableRes
fun ExplainIcon.drawable(): Int = when (this) {
    ExplainIcon.Vibrate -> R.drawable.ic_doodle_vibrate
    ExplainIcon.Silent -> R.drawable.ic_doodle_silent
    ExplainIcon.Alarm -> R.drawable.ic_doodle_alarm
    ExplainIcon.Call -> R.drawable.ic_doodle_call
    ExplainIcon.Notify -> R.drawable.ic_doodle_notify
}

// ---- 1 · Welcome -----------------------------------------------------------------------

@Composable
private fun ColumnScope.WelcomePage() {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        DoodleIllustration(
            image = Illustrations.Guard,
            size = 196.dp,
            round = true,
            backing = SnoozerTheme.colors.bell,
            outline = MaterialTheme.colorScheme.onBackground,
            imageScale = 1f,
            seed = 11,
            modifier = Modifier.bob(),
        )
        Text("SNOOZER", style = MaterialTheme.typography.displaySmall, letterSpacing = 2.sp,
            modifier = Modifier.semantics { heading() })
        Text(
            "Your phone's bodyguard. Pick when you want quiet, and Snoozer mutes it for you, every week.",
            style = SnoozerTheme.type.body,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- 2 · Two kinds of quiet ------------------------------------------------------------

@Composable
private fun ColumnScope.TwoKindsPage() {
    PageTitle("Two kinds of quiet")
    // Same height for both cards, even when one summary wraps to two lines.
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Category.entries.forEach { category ->
            KindCard(category, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** One category: its drawing, a little phone showing what happens, and the one-line summary. */
@Composable
private fun KindCard(category: Category, modifier: Modifier = Modifier) {
    val colors = SnoozerTheme.colors.forCategory(category)
    val explanation = category.explanation
    val isWork = category == Category.Work
    Column(
        modifier = modifier
            .doodleFill(colors.container, colors.hatch, wobble = DoodleDefaults.WobbleLight, seed = 200 + category.ordinal)
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, wobble = DoodleDefaults.WobbleLight, seed = 200 + category.ordinal)
            .padding(Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        DoodleIllustration(category.illustration, size = 88.dp, seed = 210 + category.ordinal)
        Text(category.label, style = SnoozerTheme.type.label, color = colors.content)
        // Work's phone buzzes now and then; Study's stays perfectly still.
        DoodlePhone(
            width = 70.dp,
            height = 110.dp,
            seed = 220 + category.ordinal,
            modifier = if (isWork) Modifier.buzz() else Modifier,
        ) {
            Icon(
                painterResource(explanation.lines.first().icon.drawable()),
                contentDescription = null,
                tint = Graphite,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(explanation.summary, style = SnoozerTheme.type.caption, color = colors.content, textAlign = TextAlign.Center)
    }
}

// Ink on the always-light phone screen, in both themes.
private val Graphite = Color(0xFF2E2B26)

// ---- 3 · Overlaps ----------------------------------------------------------------------

// The pretend day runs 08:00-18:00; Work 09-17 and Study 10-12 as fractions of it.
private const val WorkFrom = 0.1f
private const val WorkTo = 0.9f
private const val StudyFrom = 0.2f
private const val StudyTo = 0.4f

/** What the ringer is at a point of the pretend day: quietest window wins. */
internal fun ringerAt(fraction: Float): String = when {
    fraction in StudyFrom..StudyTo -> "Silent"
    fraction in WorkFrom..WorkTo -> "Vibrate"
    else -> "Normal"
}

@Composable
private fun ColumnScope.OverlapPage() {
    PageTitle("When they overlap, the quieter one wins")
    PageNote("Study inside Work: silent for the study part, back to vibrate after.")
    val motion = rememberMotionEnabled()
    val now = if (motion) {
        rememberInfiniteTransition(label = "day").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
            label = "now",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) } // resting pose: inside Study
    }
    // Recomposes only when the label changes, not every frame.
    val ringer by remember(now) { derivedStateOf { ringerAt(now.value) } }
    val work = SnoozerTheme.colors.forCategory(Category.Work)
    val study = SnoozerTheme.colors.forCategory(Category.Study)
    val ink = MaterialTheme.colorScheme.onSurface

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(120.dp).clearAndSetSemantics {}
    ) {
        val w = maxWidth
        TimelineBar("Work 9–17", work.container, work.hatch, ink, w * WorkFrom, w * (WorkTo - WorkFrom), 14.dp, 300)
        TimelineBar("Study", study.container, study.hatch, ink, w * StudyFrom, w * (StudyTo - StudyFrom), 64.dp, 301)
        // The "now" line, moved in layout (offset lambda) so it doesn't recompose each frame.
        Box(
            Modifier
                .offset { IntOffset((w * now.value).roundToPx(), 0) }
                .width(3.dp)
                .fillMaxHeight()
                .doodleFill(Color(0xFFC2185B), cornerRadius = 2.dp, wobble = 0.dp)
        )
    }
    Row(
        Modifier
            .align(Alignment.CenterHorizontally)
            .doodleFill(MaterialTheme.colorScheme.surface, wobble = DoodleDefaults.WobbleLight, seed = 302)
            .sketchyBorder(ink, wobble = DoodleDefaults.WobbleLight, seed = 302)
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        val icon = when (ringer) {
            "Silent" -> R.drawable.ic_doodle_silent
            "Vibrate" -> R.drawable.ic_doodle_vibrate
            else -> R.drawable.ic_doodle_notify
        }
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        Text("Phone: $ringer", style = SnoozerTheme.type.label)
    }
    PageNote("If your phone is already silent, Snoozer never makes it louder.")
}

@Composable
private fun TimelineBar(
    label: String, fill: Color, hatch: Color, ink: Color,
    start: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, top: androidx.compose.ui.unit.Dp, seed: Int,
) {
    Box(
        Modifier
            .offset(x = start, y = top)
            .width(width)
            .height(36.dp)
            .doodleFill(fill, hatch, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .sketchyBorder(ink, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .padding(start = Spacing.S),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = SnoozerTheme.type.caption.copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
    }
}

// ---- 4 · A heads-up first --------------------------------------------------------------

@Composable
private fun ColumnScope.HeadsUpPage() {
    PageTitle("A heads-up first")
    PageNote("A few minutes before a quiet time starts. Optional; you can turn it off.")
    val motion = rememberMotionEnabled()
    // The notice slides down into the phone, stays, and slides away (or just stays, at rest).
    val slide = if (motion) {
        rememberInfiniteTransition(label = "notice").animateFloat(
            initialValue = -1f, targetValue = -1f,
            animationSpec = infiniteRepeatable(keyframes {
                durationMillis = 4500
                -1f at 500
                0f at 1100
                0f at 3600
                -1f at 4200
            }),
            label = "notice",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    DoodlePhone(
        width = 260.dp,
        height = 140.dp,
        screen = Color(0xFF2A2D2F),
        seed = 310,
        modifier = Modifier.align(Alignment.CenterHorizontally).clearAndSetSemantics {},
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp, start = 10.dp, end = 10.dp)
                .graphicsLayer { translationY = slide.value * 110.dp.toPx() }
                .doodleFill(Color.White, cornerRadius = 10.dp, wobble = 0.dp)
                .padding(8.dp),
        ) {
            // Built by the same code as the real notice, so the sample can't drift from it.
            val sample = ReminderText.of(SampleStudy)
            Text("Snoozer · now", style = SnoozerTheme.type.caption, color = Graphite)
            Text(sample.title, style = SnoozerTheme.type.label, color = Graphite)
            Text(sample.body, style = SnoozerTheme.type.caption, color = Graphite)
        }
    }
    Category.entries.reversed().forEach { category ->
        val line = category.explanation.lines.last()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(painterResource(line.icon.drawable()), contentDescription = null, modifier = Modifier.size(22.dp))
            Text("${category.label}: ${line.text.replaceFirstChar { it.lowercase() }}", style = SnoozerTheme.type.body)
        }
    }
}

// ---- 5 · Where things are --------------------------------------------------------------

@Composable
private fun ColumnScope.WhereThingsArePage(mode: OnboardingMode) {
    // Help is usually opened by someone who already has snoozers, so "first" is for the first run only.
    PageTitle(if (mode == OnboardingMode.FirstRun) "Plan your first quiet time" else "Plan a quiet time")
    // A dimmed sketch of the week screen with a pulsing ring around "+".
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .width(260.dp)
            .height(300.dp)
            .clearAndSetSemantics {}
    ) {
        DoodlePhone(width = 260.dp, height = 300.dp, screen = MaterialTheme.colorScheme.background, seed = 320) {
            Column(
                Modifier.fillMaxSize().padding(top = 20.dp, start = 12.dp, end = 12.dp).graphicsLayer { alpha = 0.45f },
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                MiniCard(SnoozerTheme.colors.forCategory(Category.Work).container, "Work · Vibrate", "09:00 – 17:00", 321)
                MiniCard(SnoozerTheme.colors.forCategory(Category.Study).container, "Study · Silent", "19:00 – 21:00", 322)
            }
        }
        // "+" in its place, at full strength, ringed.
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(64.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).pulse()
                    .sketchyBorder(Color(0xFFC2185B), width = 3.dp, cornerRadius = DoodleDefaults.Round, seed = 323)
            )
            Box(
                Modifier.size(46.dp)
                    .doodleFill(SnoozerTheme.colors.bell, cornerRadius = DoodleDefaults.Round, seed = 324)
                    .sketchyBorder(MaterialTheme.colorScheme.onSurface, cornerRadius = DoodleDefaults.Round, seed = 324),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_doodle_add), contentDescription = null, tint = Graphite, modifier = Modifier.size(26.dp))
            }
        }
    }
    // First run ends on Home's Setup, not the week screen, so say how to get to the "+".
    PageNote(
        if (mode == OnboardingMode.FirstRun) {
            "Once Setup is done, tap \"Open my week\", then + to plan your first quiet time. " +
                "Start with Work or Study; you can change it anytime."
        } else {
            "Tap + on the week screen to plan a quiet time. Pick Work or Study; you can change it anytime."
        }
    )
    // Students can skip typing classes in one by one.
    PageNote("Have a class timetable? \"Import timetable\" on the week screen adds every class at once, until the term ends.")
    Spacer(Modifier.height(Spacing.S))
}

@Composable
private fun MiniCard(fill: Color, tag: String, time: String, seed: Int) {
    Column(
        Modifier.fillMaxWidth()
            .doodleFill(fill, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .sketchyBorder(MaterialTheme.colorScheme.onSurface, wobble = DoodleDefaults.WobbleLight, seed = seed)
            .padding(Spacing.S),
    ) {
        Text(tag, style = SnoozerTheme.type.caption)
        Text(time, style = SnoozerTheme.type.title)
    }
}

// The sample reminder on the heads-up page: a Study window 19:00-21:00.
private val SampleStudy = com.example.snoozer.alarm.Alarm(
    startHour = 19, startMinute = 0, endHour = 21, endMinute = 0,
    daysOfWeek = listOf(java.time.DayOfWeek.MONDAY), work = Category.Study.stored,
)
