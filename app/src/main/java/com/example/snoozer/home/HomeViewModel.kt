package com.example.snoozer.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.dayPickers.StatusUi
import com.example.snoozer.dayPickers.WeekUi
import com.example.snoozer.util.PermissionChecker
import com.example.snoozer.util.Permissions
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

@Immutable
data class HomeUiState(
    /** "Next quiet time: 22:00 today". */
    val statusLine: String,
    val permissions: Permissions,
) {
    /** Do Not Disturb access is the only hard requirement; exact alarms are recommended. */
    val ready: Boolean get() = permissions.dnd
}

object HomeUi {
    fun build(alarms: List<Alarm>, now: LocalDateTime, permissions: Permissions) =
        HomeUiState(statusLine(WeekUi.status(alarms, now, canMute = permissions.dnd)), permissions)

    /** The status card's message squeezed into one line. Same source as the week screen. */
    fun statusLine(status: StatusUi): String = when (status) {
        is StatusUi.QuietNow -> "Quiet now, until ${status.until}"
        is StatusUi.Next -> "Next quiet time: ${status.time} ${status.day}"
        StatusUi.NothingScheduled -> "No quiet times planned yet"
        StatusUi.NoAccess -> "Paused: Snoozer can't mute without Do Not Disturb access"
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: AlarmRepository,
    timeSource: TimeSource,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    // Android has no callback for these permissions changing, so the screen calls
    // refreshPermissions() whenever it resumes (e.g. coming back from Settings).
    private val permissions = MutableStateFlow(permissionChecker.check())

    /** Null until the first database read. */
    val uiState: StateFlow<HomeUiState?> =
        combine(repository.observeAlarms(), timeSource.minuteTicks, permissions) { alarms, now, perms ->
            HomeUi.build(alarms, now, perms)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions() {
        permissions.value = permissionChecker.check()
    }
}
