package com.example.snoozer.dayPickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.util.PermissionChecker
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DayPickerViewModel @Inject constructor(
    repository: AlarmRepository,
    private val actions: AlarmActions,
    timeSource: TimeSource,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    /** The page to open on: today (0 = Monday). The pager owns the selection after that. */
    val initialPage: Int = timeSource.now().dayOfWeek.value - 1

    // Do Not Disturb access can be revoked in Settings at any time; the screen re-checks on resume.
    private val canMute = MutableStateFlow(permissionChecker.check().dnd)

    /**
     * Rebuilt whenever the alarms change and at every minute, so "in 2h 15m" stays current.
     * Null until the first database read, so the screen doesn't flash "A free day" on open.
     * The clock only ticks while the screen collects this (it stops 5s after it leaves).
     */
    val uiState: StateFlow<WeekUiState?> =
        combine(repository.observeAlarms(), timeSource.minuteTicks, canMute) { alarms, now, dnd ->
            WeekUi.build(alarms, now, canMute = dnd)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions() {
        canMute.value = permissionChecker.check().dnd
    }

    /** AlarmActions reads the saved row itself, so quick repeated taps never act on stale data. */
    fun onToggle(id: Int, enabled: Boolean) {
        viewModelScope.launch { actions.setEnabled(id, enabled) }
    }
}
