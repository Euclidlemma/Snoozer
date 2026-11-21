package com.example.snoozer.EditAlarm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.Route.Routes
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/** One-off results the screen reacts to by leaving. [message] is shown as a toast if set. */
data class EditEvent(val message: String?)

@HiltViewModel
class EditAlarmViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val actions: AlarmActions,
) : ViewModel() {

    /** The snoozer being edited, or null when creating one. Comes from the route. */
    private val alarmId: Int? = savedStateHandle.get<Int>(Routes.EditAlarm.ARG_ID)?.takeIf { it >= 0 }
    private val isNew = alarmId == null

    // Kept in SavedStateHandle so a half-filled form survives the process being killed.
    private val form = MutableStateFlow(savedStateHandle.get<EditForm>(KEY_FORM))
    private val original = MutableStateFlow(savedStateHandle.get<EditForm>(KEY_ORIGINAL))

    private val events = Channel<EditEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditEvent> = events.receiveAsFlow()

    /** Prevents a double tap on Save or Delete from writing twice. */
    private var busy = false

    /** Null while the saved snoozer is loading. */
    val uiState: StateFlow<EditUiState?> = combine(form, original) { f, o ->
        if (f == null || o == null) null else EditUi.describe(f, o, isNew)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (form.value == null) viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val loaded = if (alarmId == null) {
            val day = savedStateHandle.get<Int>(Routes.EditAlarm.ARG_DAY)?.takeIf { it in 1..7 }
            EditForm.new(day?.let(DayOfWeek::of))
        } else {
            repository.getAlarmById(alarmId)?.let(EditForm::from)
        }
        if (loaded == null) {
            // Deleted elsewhere (or a stale link): nothing to edit.
            events.send(EditEvent("That snoozer no longer exists"))
            return
        }
        original.value = loaded
        savedStateHandle[KEY_ORIGINAL] = loaded
        setForm(loaded)
    }

    private fun setForm(value: EditForm) {
        form.value = value
        savedStateHandle[KEY_FORM] = value
    }

    private fun edit(change: (EditForm) -> EditForm) {
        form.value?.let { setForm(change(it)) }
    }

    fun onStartChange(hour: Int, minute: Int) = edit { it.withStart(hour, minute) }
    fun onEndChange(hour: Int, minute: Int) = edit { it.withEnd(hour, minute) }
    fun onToggleDay(day: DayOfWeek) = edit { it.toggleDay(day) }
    fun onPreset(preset: RepeatPreset) = edit { it.withPreset(preset) }
    fun onCategory(category: String) = edit { it.copy(category = category) }

    fun save() {
        val current = form.value ?: return
        if (busy || uiState.value?.canSave != true) return
        busy = true
        // The screen only leaves after the write finishes; leaving clears this ViewModel and
        // would cancel a write still in flight.
        viewModelScope.launch {
            if (alarmId == null) {
                actions.create(current.toNewAlarm())
            } else {
                // Re-read so the update starts from what is saved now (e.g. its on/off state).
                val saved = repository.getAlarmById(alarmId)
                if (saved == null) {
                    events.send(EditEvent("That snoozer no longer exists"))
                    return@launch
                }
                actions.update(old = saved, new = current.applyTo(saved))
            }
            events.send(EditEvent("Snoozer saved"))
        }
    }

    fun delete() {
        if (busy || alarmId == null) return
        busy = true
        viewModelScope.launch {
            repository.getAlarmById(alarmId)?.let { actions.delete(it) }
            events.send(EditEvent("Snoozer deleted"))
        }
    }

    private companion object {
        const val KEY_FORM = "form"
        const val KEY_ORIGINAL = "original"
    }
}
