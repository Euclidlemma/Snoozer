package com.example.snoozer.dayPickers

import com.example.snoozer.util.ShownLanguage
import com.example.snoozer.util.alsoOn
import com.example.snoozer.util.Strings
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.util.PermissionChecker
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import com.example.snoozer.alarm.QuietStretchDao
import com.example.snoozer.report.ReportSeenStore
import com.example.snoozer.report.ReportStripUi
import com.example.snoozer.report.WeeklyReport
import java.time.LocalDateTime
import com.example.snoozer.alarm.Alarm
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class DayPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val actions: AlarmActions,
    private val timeSource: TimeSource,
    private val permissionChecker: PermissionChecker,
    private val quietStretches: QuietStretchDao,
    reportSeen: ReportSeenStore,
    private val strings: Strings,
    language: ShownLanguage,
) : ViewModel() {

    /** The page to open on: today (0 = Monday). The pager owns the selection after that. */
    val initialPage: Int = timeSource.now().dayOfWeek.value - 1

    // Do Not Disturb access can be revoked in Settings at any time; the screen re-checks on resume.
    private val canMute = MutableStateFlow(permissionChecker.check().dnd)

    // Week or Month, the month shown and the day picked in it. Saved as plain text, so they
    // survive the process being killed and read back safely (anything odd falls back to today).
    private val view = MutableStateFlow(
        savedStateHandle.get<String>(KEY_VIEW)?.let { v -> WeekView.entries.firstOrNull { it.name == v } } ?: WeekView.Week
    )
    val weekView: StateFlow<WeekView> = view.asStateFlow()

    private val selected = MutableStateFlow(
        savedStateHandle.get<String>(KEY_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: timeSource.now().toLocalDate()
    )
    private val month = MutableStateFlow(
        savedStateHandle.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: YearMonth.from(selected.value)
    )

    // One database observer and one clock for both views.
    private data class Snapshot(val alarms: List<Alarm>, val now: LocalDateTime, val canMute: Boolean)

    private val snapshot = combine(repository.observeAlarms(), timeSource.minuteTicks.alsoOn(language), canMute, ::Snapshot)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Rebuilt whenever the alarms change and at every minute, so "in 2h 15m" stays current.
     * Null until the first database read, so the screen doesn't flash "A free day" on open.
     * The clock only ticks while the screen collects this (it stops 5s after it leaves).
     */
    val uiState: StateFlow<WeekUiState?> =
        snapshot.map { WeekUi.build(strings, it.alarms, it.now, canMute = it.canMute) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The strip that opens the weekly report: last week's total, with a dot until it's opened
     * (or the first-week note). Reads the same weeks of log the report does.
     */
    val reportStrip: StateFlow<ReportStripUi?> =
        combine(snapshot, quietStretches.observeSince(WeeklyReport.keepFrom(timeSource.now(), ZoneId.systemDefault())), reportSeen.seenWeek) { s, stretches, seenWeek ->
            WeeklyReport.strip(strings, stretches, s.now, ZoneId.systemDefault(), seenWeek)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The month calendar; only built while the Month view is showing. */
    val monthState: StateFlow<MonthUiState?> =
        combine(snapshot, view, month, selected) { s, v, m, d ->
            if (v == WeekView.Month) MonthUi.build(strings, s.alarms, s.now, m, d, canMute = s.canMute) else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Keep the quiet log to the weeks the report shows. Here, not on the report screen, so it
        // happens even for someone who never opens the report. Nothing depends on the result.
        viewModelScope.launch {
            try {
                quietStretches.deleteEndedBefore(WeeklyReport.keepFrom(timeSource.now(), ZoneId.systemDefault()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not prune the quiet log", e)
            }
        }
    }

    fun refreshPermissions() {
        canMute.value = permissionChecker.check().dnd
    }

    /** AlarmActions reads the saved row itself, so quick repeated taps never act on stale data. */
    fun onToggle(id: Int, enabled: Boolean) {
        viewModelScope.launch { actions.setEnabled(id, enabled) }
    }

    fun onView(value: WeekView) {
        view.value = value
        savedStateHandle[KEY_VIEW] = value.name
    }

    /**
     * Shows [value]. The picked day follows: today in the current month, otherwise the 1st,
     * so the list under the grid always belongs to the month on screen.
     */
    fun onMonth(value: YearMonth) {
        setMonth(value)
        val today = timeSource.now().toLocalDate()
        setSelected(if (YearMonth.from(today) == value) today else value.atDay(1))
    }

    /** Picks [date]; a day from a neighbouring month (filling the grid) moves to that month. */
    fun onSelectDate(date: LocalDate) {
        setSelected(date)
        if (YearMonth.from(date) != month.value) setMonth(YearMonth.from(date))
    }

    private fun setMonth(value: YearMonth) {
        month.value = value
        savedStateHandle[KEY_MONTH] = value.toString()
    }

    private fun setSelected(date: LocalDate) {
        selected.value = date
        savedStateHandle[KEY_DATE] = date.toString()
    }

    /** Deletes every snoozer past its last date (classes after the term). Re-read, never stale. */
    fun removeEnded() {
        viewModelScope.launch {
            try {
                val now = timeSource.now()
                val ended = repository.getAllAlarmsDirect().filter { AlarmTime.hasEnded(it, now) }
                if (ended.isNotEmpty()) actions.deleteAll(ended)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Removing ended classes failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "DayPickerViewModel"
        const val KEY_VIEW = "week_view"
        const val KEY_MONTH = "month"
        const val KEY_DATE = "selected_date"
    }
}
