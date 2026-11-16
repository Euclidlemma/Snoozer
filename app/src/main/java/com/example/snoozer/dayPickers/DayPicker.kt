package com.example.snoozer.dayPickers

import android.content.res.Configuration
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.snoozer.R
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.AlarmViewModel
import com.example.snoozer.ui.doodle.DoodleCard
import com.example.snoozer.ui.doodle.DoodleDefaults
import com.example.snoozer.ui.doodle.DoodleDivider
import com.example.snoozer.ui.doodle.DoodleFab
import com.example.snoozer.ui.doodle.SketchyShape
import com.example.snoozer.ui.doodle.doodleFill
import com.example.snoozer.ui.doodle.highlighter
import com.example.snoozer.ui.doodle.sketchyBorder
import com.example.snoozer.ui.theme.SnoozerTheme
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPickerMain(navController: NavController,
                  dayPickerViewModel: DayPickerViewModel = hiltViewModel(),
                  alarmViewModel: AlarmViewModel) {
    // The theme comes from MainActivity.
    val tabIndex by dayPickerViewModel.tabIndex.observeAsState(0)
    val tabs = dayPickerViewModel.tabs
    val pagerState = rememberPagerState(
        initialPage = tabIndex,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            DoodleFab(
                onClick = {
                    alarmViewModel.setSelectedAlarm(null) // Creating new alarm
                    navController.navigate("editAlarm")
                },
                contentDescription = "Add snoozer",
                seed = 31,
            ) {
                // Decorative: the FAB's own description ("Add snoozer") is what gets announced.
                Icon(
                    painter = painterResource(R.drawable.ic_doodle_add),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            DayTabs(
                selected = pagerState.currentPage,
                labels = tabs,
                onSelect = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    dayPickerViewModel.updateTabIndex(index)
                },
            )

            // Pager content: each page shows alarms for one day
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val alarms by alarmViewModel.getAlarmsForDay(page + 1)
                    .observeAsState(emptyList())

                DayAlarmPage(alarms = alarms,
                    onAlarmClick = { alarm ->
                        alarmViewModel.setSelectedAlarm(alarm)
                        navController.navigate("editAlarm")
                    }
                )
            }
        }
    }

    // Keep tab index in sync with pager
    LaunchedEffect(pagerState.currentPage) {
        dayPickerViewModel.updateTabIndex(pagerState.currentPage)
    }
}

/**
 * Weekday tabs as handwritten letters; the selected day gets a highlighter swipe.
 * Single letters are ambiguous for screen readers (two T's, two S's), so each tab
 * announces the full day name instead.
 */
@Composable
private fun DayTabs(selected: Int, labels: List<Int>, onSelect: (Int) -> Unit) {
    val highlighter = SnoozerTheme.colors.highlighter
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            labels.forEachIndexed { index, resId ->
                val isSelected = index == selected
                val dayName = DayOfWeek.of(index + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                        .semantics { contentDescription = dayName },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(resId),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .then(if (isSelected) Modifier.highlighter(highlighter) else Modifier)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
            }
        }
        DoodleDivider(modifier = Modifier.padding(horizontal = 12.dp), seed = 21)
    }
}

@SuppressLint("NewApi")
@Composable
fun DayAlarmPage(alarms: List<Alarm>, onAlarmClick: (Alarm) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last card clear of the floating + button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(alarms, key = { it.id }) { alarm ->
            AlarmCard(alarm = alarm, onClick = { onAlarmClick(alarm) })
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun AlarmCard(alarm: Alarm, onClick: () -> Unit) {
    val categoryColors = SnoozerTheme.colors.forCategory(alarm.work)
    val circleSeed = alarm.id * 7 + 1
    val circleShape = remember(circleSeed) { SketchyShape(cornerRadius = DoodleDefaults.Round, seed = circleSeed) }

    // Seeded by alarm id, so every card has its own wobble that stays put.
    DoodleCard(colors = categoryColors, onClick = onClick, seed = alarm.id, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The art is black lines on transparent, so it sits on a light doodle circle
            // to stay readable on the dark-mode card colors.
            Image(
                painter = painterResource(if (alarm.work == "Work") R.drawable.work else R.drawable.study),
                contentDescription = alarm.work,
                modifier = Modifier
                    .size(88.dp)
                    .sketchyBorder(MaterialTheme.colorScheme.onSurface, cornerRadius = DoodleDefaults.Round, seed = circleSeed)
                    .clip(circleShape)
                    .doodleFill(SnoozerTheme.colors.illustrationBacking, cornerRadius = DoodleDefaults.Round, seed = circleSeed)
                    .padding(8.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val formattedStartTime = String.format("%02d:%02d", alarm.startHour, alarm.startMinute)
                val formattedEndTime = String.format("%02d:%02d", alarm.endHour, alarm.endMinute)
                // Times are the most important text in the app: the largest, boldest
                // style from the theme. No fixed height, so large system font sizes don't clip.
                Text(text = formattedStartTime, style = MaterialTheme.typography.displayMedium)
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "~ $formattedEndTime", style = MaterialTheme.typography.displaySmall)
                    // Overnight windows end the next day; say so instead of looking like a typo.
                    if (AlarmTime.isOvernight(alarm)) {
                        Text(
                            text = "+1",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .semantics { contentDescription = "next day" },
                        )
                    }
                }
            }
        }
    }
}


@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF2A2D2F, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AlarmCard_preview(){
    SnoozerTheme { AlarmCard(alarm = Alarm(startHour = 10, startMinute = 1, endHour = 11, endMinute = 30, daysOfWeek = DayOfWeek.entries, work = "Work"), onClick = {}) }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF6EC)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF2A2D2F, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DayAlarmPage_preview() {
    SnoozerTheme { DayAlarmPage(alarms = listOf(
        Alarm(1,  9, 0, 10, 30, listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), work = "Work"),
        Alarm(2,  22, 0, 7, 0, listOf(DayOfWeek.WEDNESDAY, DayOfWeek.TUESDAY), work = "Study")
    ), onAlarmClick = {}) }
}
