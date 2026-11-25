package com.example.snoozer.dayPickers

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.snoozer.R
import com.example.snoozer.Route.Routes
import com.example.snoozer.Route.navigateFrom
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleDivider
import com.example.snoozer.ui.doodle.DoodleFab
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import com.example.snoozer.ui.theme.Spacing
import com.example.snoozer.util.openDndSettings
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The week screen. State comes from [DayPickerViewModel]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPickerMain(
    navController: NavController,
    viewModel: DayPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = viewModel.initialPage, pageCount = { 7 })
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current // this screen's back stack entry

    // Do Not Disturb access can be revoked in Settings while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // A new snoozer starts on the day being viewed.
    val createOnCurrentDay = {
        navController.navigateFrom(lifecycleOwner, Routes.EditAlarm.create(DayOfWeek.of(pagerState.currentPage + 1)))
    }

    Scaffold(
        floatingActionButton = {
            DoodleFab(onClick = createOnCurrentDay, contentDescription = "Add snoozer", seed = 31) {
                // Decorative: the FAB's own description ("Add snoozer") is what gets announced.
                Icon(painterResource(R.drawable.ic_doodle_add), contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
    ) { padding ->
        // Null only until the first database read; showing nothing avoids a flash of empty days.
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            WeekContent(
                state = current,
                pagerState = pagerState,
                onCardClick = { id -> navController.navigateFrom(lifecycleOwner, Routes.EditAlarm.edit(id)) },
                onToggle = viewModel::onToggle,
                onAdd = createOnCurrentDay,
                onAllowAccess = context::openDndSettings,
                onHelp = { navController.navigateFrom(lifecycleOwner, Routes.HowItWorks.route) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** Stateless body of the week screen: status card, week strip, and one timeline page per day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekContent(
    state: WeekUiState,
    pagerState: PagerState,
    onCardClick: (Int) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onAllowAccess: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = Spacing.M, end = Spacing.M, top = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            StatusCard(state.status, onAllowAccess = onAllowAccess, modifier = Modifier.weight(1f))
            HelpButton(onHelp)
        }
        if (state.weeklyTotals != null) {
            Text(
                state.weeklyTotals,
                style = SnoozerTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.M, end = Spacing.M, top = Spacing.S),
            )
        }
        Spacer(Modifier.height(Spacing.S))
        WeekStrip(
            days = state.days,
            selected = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )
        DoodleDivider(modifier = Modifier.padding(horizontal = Spacing.M), seed = 21)
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            DayPage(day = state.days[page], onCardClick = onCardClick, onToggle = onToggle, onAdd = onAdd)
        }
    }
}

@Composable
private fun DayPage(day: DayUi, onCardClick: (Int) -> Unit, onToggle: (Int, Boolean) -> Unit, onAdd: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last card clear of the floating + button.
        contentPadding = PaddingValues(start = Spacing.M, end = Spacing.M, top = Spacing.S, bottom = 104.dp),
    ) {
        item(key = "header") { DayHeader(day, Modifier.padding(bottom = Spacing.M)) }
        if (day.cards.isEmpty()) {
            item(key = "empty") { EmptyDay(dayName = day.name, onAdd = onAdd) }
        } else {
            itemsIndexed(day.cards, key = { _, card -> card.id }) { index, card ->
                TimelineItem(
                    card = card,
                    isFirst = index == 0,
                    isLast = index == day.cards.lastIndex,
                    onClick = { onCardClick(card.id) },
                    onToggle = { onToggle(card.id, it) },
                )
            }
        }
    }
}

// Previews: Wednesday 2026-11-18 at 09:20, one window active, one off, a few empty days.
private val PreviewNow: LocalDateTime = LocalDateTime.of(2026, 11, 18, 9, 20)
private val PreviewAlarms = listOf(
    Alarm(1, 9, 0, 10, 30, DayOfWeek.entries.take(5), work = "Work"),
    Alarm(2, 13, 0, 14, 0, listOf(DayOfWeek.WEDNESDAY), work = "Study", isEnabled = false),
    Alarm(3, 22, 0, 7, 0, listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), work = "Study"),
)

@OptIn(ExperimentalFoundationApi::class)
@Preview(name = "Week - light", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Week - dark", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeekContentPreview() {
    SnoozerTheme {
        WeekContent(
            state = WeekUi.build(PreviewAlarms, PreviewNow),
            pagerState = rememberPagerState(initialPage = 2, pageCount = { 7 }),
            onCardClick = {}, onToggle = { _, _ -> }, onAdd = {},
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview(name = "Empty day", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Composable
private fun EmptyWeekPreview() {
    SnoozerTheme {
        WeekContent(
            state = WeekUi.build(emptyList(), PreviewNow),
            pagerState = rememberPagerState(initialPage = 2, pageCount = { 7 }),
            onCardClick = {}, onToggle = { _, _ -> }, onAdd = {},
        )
    }
}

// Every state of the status card: the category drawing shows only when there's a window.
@Preview(name = "Status card - all states", widthDp = 360, showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Status card - all states, dark", widthDp = 360, showBackground = true, backgroundColor = 0xFF2A2D2F,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatusCardStatesPreview() {
    SnoozerTheme {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            StatusCard(StatusUi.QuietNow(until = "10:30", category = Category.Study, level = QuietLevel.Silent, left = "1h 10m left"))
            StatusCard(StatusUi.Next(time = "13:00", day = "today", category = Category.Work, level = QuietLevel.Vibrate, startsIn = "in 3h 40m"))
            StatusCard(StatusUi.NothingScheduled)
            StatusCard(StatusUi.NoAccess)
        }
    }
}

/**
 * A small round "?" next to the status card. Opens "How Snoozer works" (the onboarding pages
 * in help mode). 48dp touch target, drawn as a 40dp pencil circle.
 */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Open how Snoozer works", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "How Work and Study work" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp)
                .doodleFill(MaterialTheme.colorScheme.secondaryContainer, cornerRadius = DoodleDefaults.Round,
                    wobble = DoodleDefaults.WobbleLight, seed = 500)
                .sketchyBorder(ink, width = 1.5.dp, cornerRadius = DoodleDefaults.Round,
                    wobble = DoodleDefaults.WobbleLight, seed = 500),
            contentAlignment = Alignment.Center,
        ) {
            // Hidden from screen readers: the button already says what it does.
            Text("?", style = SnoozerTheme.type.title, color = ink, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
