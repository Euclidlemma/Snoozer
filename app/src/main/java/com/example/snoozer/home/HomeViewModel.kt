package com.example.snoozer.home

import com.example.snoozer.util.ShownLanguage
import com.example.snoozer.util.alsoOn
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmActions
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.dayPickers.StatusUi
import com.example.snoozer.dayPickers.WeekUi
import com.example.snoozer.util.PermissionChecker
import com.example.snoozer.util.Permissions
import com.example.snoozer.util.TimeSource
import com.example.snoozer.util.Strings
import com.example.snoozer.R
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
    fun build(strings: Strings, alarms: List<Alarm>, now: LocalDateTime, permissions: Permissions) =
        HomeUiState(statusLine(strings, WeekUi.status(strings, alarms, now, canMute = permissions.dnd)), permissions)

    /** The status card's message squeezed into one line. Same source as the week screen. */
    fun statusLine(strings: Strings, status: StatusUi): String = when (status) {
        is StatusUi.QuietNow ->
            if (status.then == null) strings.get(R.string.home_quiet_until, status.until)
            else strings.get(R.string.home_quiet_until_then, status.until, status.then)
        is StatusUi.Next -> strings.get(R.string.home_next, strings.get(R.string.time_on_day, status.time, status.day))
        StatusUi.NothingScheduled -> strings.get(R.string.home_nothing)
        StatusUi.NoAccess -> strings.get(R.string.home_paused)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: AlarmRepository,
    timeSource: TimeSource,
    private val permissionChecker: PermissionChecker,
    private val actions: AlarmActions,
    private val strings: Strings,
    language: ShownLanguage,
) : ViewModel() {

    // Android has no callback for these permissions changing, so the screen calls
    // refreshPermissions() whenever it resumes (e.g. coming back from Settings).
    private val permissions = MutableStateFlow(permissionChecker.check())

    /** Null until the first database read. */
    val uiState: StateFlow<HomeUiState?> =
        combine(repository.observeAlarms(), timeSource.minuteTicks.alsoOn(language), permissions) { alarms, now, perms ->
            HomeUi.build(strings, alarms, now, perms)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions() {
        permissions.value = permissionChecker.check()
        // Access may have just been given in Settings: if a snoozer is running but couldn't
        // quiet the phone, quiet it now. It changes nothing when the ringer already agrees.
        if (permissions.value.dnd) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    actions.catchUp()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A database that can't be read right now: the next alarm or open tries again.
                    Log.e("HomeViewModel", "Couldn't catch the ringer up", e)
                }
            }
        }
    }
}
