package com.example.snoozer.dayPickers

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.snoozer.R
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmViewModel
import com.example.snoozer.ui.theme.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPickerMain(navController: NavController,
                  dayPickerViewModel: DayPickerViewModel = hiltViewModel(),
                  alarmViewModel: AlarmViewModel) {
    SnoozerTheme {
        val tabIndex by dayPickerViewModel.tabIndex.observeAsState(0)
        val tabs = dayPickerViewModel.tabs
        val pagerState = rememberPagerState(
            initialPage = tabIndex,
            pageCount = { tabs.size }
        )
        val coroutineScope = rememberCoroutineScope()


        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        alarmViewModel.setSelectedAlarm(null) // Creating new alarm
                        navController.navigate("editAlarm")
                    }
                ) {
                    Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 25.sp)
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // TabRow
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, titleResId ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                                dayPickerViewModel.updateTabIndex(index)
                            },
                            text = {
                                Text(text = stringResource(id = titleResId))
                            }
                        )
                    }
                }

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
}

@SuppressLint("NewApi")
@Composable
fun DayAlarmPage(alarms: List<Alarm>, onAlarmClick: (Alarm) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alarms) { alarm ->
                AlarmCard(alarm = alarm, onClick = { onAlarmClick(alarm) })
            }
        }
}

@SuppressLint("DefaultLocale")
@Composable
fun AlarmCard(alarm: Alarm, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (alarm.work == "Work") Color(0xFF90D5FF) else Color(0xFFDAB1DA)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.Left
        ) {
            Image(
                painter = painterResource(if (alarm.work == "Work") R.drawable.work else R.drawable.study),
                contentDescription = "Alarm Icon",
                modifier = Modifier.size(100.dp, 100.dp)
            )
            Column(verticalArrangement = Arrangement.Center) {
                val formattedStartTime = String.format("%02d:%02d", alarm.startHour, alarm.startMinute)
                val formattedEndTime = String.format("%02d:%02d", alarm.endHour, alarm.endMinute)
                Text(
                    text = formattedStartTime,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(70.dp)
                        .padding(start = 20.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Text(
                    text = "~ $formattedEndTime",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(70.dp)
                        .padding(start = 20.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
        }
    }
}


@Preview
@Composable
fun AlarmCard_preview(){
    AlarmCard(alarm = Alarm(startHour = 10, startMinute = 1, endHour = 10, endMinute = 1, daysOfWeek = DayOfWeek.entries, work = "Work"), onClick = {})
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun DayAlarmPage_preview() {
    DayAlarmPage(alarms = listOf(
        Alarm(0,  9, 0, 10, 0, listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), work = "Work"),
        Alarm(1,  10, 0, 11, 0, listOf(DayOfWeek.WEDNESDAY, DayOfWeek.TUESDAY), work = "Study")

    ), onAlarmClick = {})
}