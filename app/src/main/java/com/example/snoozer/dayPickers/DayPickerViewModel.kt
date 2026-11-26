package com.example.snoozer.dayPickers

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class DayPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val actions: AlarmActions,
    private val timeSource: TimeSource,
    private val permissionChecker: PermissionChecker,
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

    /**
     * Rebuilt whenever the alarms change and at every minute, so "in 2h 15m" stays current.
     * Null until the first database read, so the screen doesn't flash "A free day" on open.
     * The clock only ticks while the screen collects this (it stops 5s after it leaves).
     */
    val uiState: StateFlow<WeekUiState?> =
        combine(repository.observeAlarms(), timeSource.minuteTicks, canMute) { alarms, now, dnd ->
            WeekUi.build(alarms, now, canMute = dnd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The month calendar, built only from the same alarms and clock as [uiState]. */
    val monthState: StateFlow<MonthUiState?> =
        combine(repository.observeAlarms(), timeSource.minuteTicks, canMute, month, selected) { alarms, now, dnd, m, d ->
            MonthUi.build(alarms, now, m, d, canMute = dnd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
