package com.example.snoozer.dayPickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DayPickerViewModel @Inject constructor(
    repository: AlarmRepository,
    private val actions: AlarmActions,
    timeSource: TimeSource,
) : ViewModel() {

    /** The page to open on: today (0 = Monday). The pager owns the selection after that. */
    val initialPage: Int = timeSource.now().dayOfWeek.value - 1

    private var latestAlarms: List<Alarm> = emptyList()

    /**
     * Rebuilt whenever the alarms change and at every minute, so "in 2h 15m" stays current.
     * Null until the first database read, so the screen doesn't flash "A free day" on open.
     * The clock only ticks while the screen collects this (it stops 5s after it leaves).
     */
    val uiState: StateFlow<WeekUiState?> =
        combine(repository.observeAlarms().onEach { latestAlarms = it }, timeSource.minuteTicks) { alarms, now ->
            WeekUi.build(alarms, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The saved alarm behind a card, for opening it in the edit screen. */
    fun alarm(id: Int): Alarm? = latestAlarms.firstOrNull { it.id == id }

    fun onToggle(id: Int, enabled: Boolean) {
        val alarm = alarm(id) ?: return
        viewModelScope.launch { actions.setEnabled(alarm, enabled) }
    }
}
