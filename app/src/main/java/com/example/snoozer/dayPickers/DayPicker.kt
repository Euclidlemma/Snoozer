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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import com.example.snoozer.ui.doodle.DoodleButton
import com.example.snoozer.ui.doodle.DoodleChip
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
import com.example.snoozer.report.ReportStrip
import com.example.snoozer.report.ReportStripUi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.snoozer.timetable.openImport
import com.example.snoozer.timetable.rememberTimetablePicker
import com.example.snoozer.ui.doodle.DoodleAlertDialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.LocalDateTime

/** The week screen. State comes from [DayPickerViewModel]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPickerMain(
    navController: NavController,
    viewModel: DayPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view by viewModel.weekView.collectAsStateWithLifecycle()
    val month by viewModel.monthState.collectAsStateWithLifecycle()
    val reportStrip by viewModel.reportStrip.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = viewModel.initialPage, pageCount = { 7 })
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current // this screen's back stack entry
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    // Do Not Disturb access can be revoked in Settings while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    // A new snoozer starts on the day being viewed: the week page, or the day picked in Month.
    // In Month, a new snoozer is for the picked date only ("One date", that day selected); in Week
    // it repeats every week, starting on the day being viewed.
    val createOnCurrentDay = {
        val picked = if (view == WeekView.Month) month?.selected?.date else null
        navController.navigateFrom(
            lifecycleOwner,
            if (picked != null) Routes.EditAlarm.createOn(picked)
            else Routes.EditAlarm.create(DayOfWeek.of(pagerState.currentPage + 1)),
        )
    }

    // The picker's result arrives while this screen is still coming back to the front, so it
    // navigates directly (navigateFrom would see it not yet resumed). One result, one screen.
    val pickTimetable = rememberTimetablePicker { uri -> navController.openImport(uri) }

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
                view = view,
                month = month,
                onView = viewModel::onView,
                onMonth = viewModel::onMonth,
                onSelectDate = viewModel::onSelectDate,
                onImport = pickTimetable,
                onRemoveEnded = { confirmRemove = true },
                report = reportStrip,
                onReport = { navController.navigateFrom(lifecycleOwner, Routes.WeeklyReport.route) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (confirmRemove) {
        val count = state?.endedCount ?: 0
        DoodleAlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = if (count == 1) "Remove the ended snoozer?" else "Remove $count ended snoozers?",
            text = (if (count == 1) "It's past its last date and won't quiet your phone again." else "They're past their last date and won't quiet your phone again.") +
                (if (count == 1) " Its" else " Their") + " quiet time stays in the weekly report.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                viewModel.removeEnded()
            },
            dismissLabel = "Keep",
            destructive = true,
        )
    }
}

/**
 * Stateless body of the week screen: status card, then the Week or Month view. Week is the
 * week strip with one timeline page per day; Month is [MonthContent].
 */
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
    view: WeekView = WeekView.Week,
    month: MonthUiState? = null,
    onView: (WeekView) -> Unit = {},
    onMonth: (YearMonth) -> Unit = {},
    onSelectDate: (LocalDate) -> Unit = {},
    onImport: () -> Unit = {},
    onRemoveEnded: () -> Unit = {},
    report: ReportStripUi? = null,
    onReport: () -> Unit = {},
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
        // Last week's report, in place of the plain totals line (shown until the report state arrives).
        if (report != null) {
            ReportStrip(report, onClick = onReport, modifier = Modifier.padding(start = Spacing.M, end = Spacing.M, top = Spacing.S))
        } else if (state.weeklyTotals != null) {
            Text(
                state.weeklyTotals,
                style = SnoozerTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.M, end = Spacing.M, top = Spacing.S),
            )
        }
        ViewBar(view = view, onView = onView, onImport = onImport)
        if (state.endedCount > 0) EndedNotice(state.endedCount, onRemoveEnded)
        if (view == WeekView.Month) {
            // The month arrives a moment after switching; keep its space empty meanwhile rather
            // than flashing the week pages.
            if (month == null) {
                Box(Modifier.weight(1f).fillMaxWidth())
            } else {
                MonthContent(
                    state = month,
                    onMonth = onMonth,
                    onSelect = onSelectDate,
                    onCardClick = onCardClick,
                    onToggle = onToggle,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            WeekStrip(
                days = state.days,
                selected = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
            DoodleDivider(modifier = Modifier.padding(horizontal = Spacing.M), seed = 21)
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                DayPage(day = state.days[page], onCardClick = onCardClick, onToggle = onToggle, onAdd = onAdd, onImport = onImport)
            }
        }
    }
}

/** Week | Month on the left, "Import timetable" on the right. */
@Composable
private fun ViewBar(view: WeekView, onView: (WeekView) -> Unit, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            WeekView.entries.forEachIndexed { i, v ->
                DoodleChip(
                    selected = v == view,
                    onClick = { onView(v) },
                    label = v.name,
                    seed = 740 + i,
                    wobble = DoodleDefaults.WobbleLight,
                    modifier = Modifier.semantics { role = Role.Tab },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        DoodleButton(
            onClick = onImport,
            seed = 742,
            wobble = DoodleDefaults.WobbleLight,
            containerColor = Color.Transparent,
        ) {
            Icon(painterResource(R.drawable.ic_doodle_import), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.XS))
            Text("Import timetable")
        }
    }
}

/** "2 classes have ended" after a term, with a way to clear them out. */
@Composable
private fun EndedNotice(count: Int, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.M)
            .padding(bottom = Spacing.S)
            .doodleFill(MaterialTheme.colorScheme.surface, wobble = DoodleDefaults.WobbleLight, seed = 745)
            .sketchyBorder(MaterialTheme.colorScheme.outline, width = 1.5.dp, wobble = DoodleDefaults.WobbleLight, seed = 745)
            .padding(start = Spacing.M, end = Spacing.XS, top = Spacing.XS, bottom = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == 1) "1 snoozer has ended." else "$count snoozers have ended.",
            style = SnoozerTheme.type.body,
            modifier = Modifier.weight(1f),
        )
        DoodleButton(onClick = onRemove, seed = 746, wobble = DoodleDefaults.WobbleLight, containerColor = Color.Transparent,
            borderColor = Color.Transparent) {
            Text(if (count == 1) "Remove it" else "Remove them")
        }
    }
}

@Composable
private fun DayPage(day: DayUi, onCardClick: (Int) -> Unit, onToggle: (Int, Boolean) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last card clear of the floating + button.
        contentPadding = PaddingValues(start = Spacing.M, end = Spacing.M, top = Spacing.S, bottom = 104.dp),
    ) {
        item(key = "header") { DayHeader(day, Modifier.padding(bottom = Spacing.M)) }
        if (day.cards.isEmpty()) {
            item(key = "empty") { EmptyDay(dayName = day.name, onAdd = onAdd, onImport = onImport) }
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
