package com.example.snoozer.EditAlarm

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.Route.Routes
import com.example.snoozer.alarm.AlarmActions
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.alarm.Category
import com.example.snoozer.util.PermissionChecker
import com.example.snoozer.util.TimeSource
import java.time.LocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

/**
 * One-off results for the screen. [message] is shown as a toast if set; the screen leaves
 * when [leave] is true and stays (so the user can try again) when it is false.
 */
data class EditEvent(val message: String?, val leave: Boolean = true)

@HiltViewModel
class EditAlarmViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val actions: AlarmActions,
    private val permissionChecker: PermissionChecker,
    private val timeSource: TimeSource,
) : ViewModel() {

    // Whether reminders can be shown, so the "What happens" panel doesn't promise a heads-up
    // that won't come. Re-read when the screen resumes (e.g. back from Settings).
    private val permissions = MutableStateFlow(permissionChecker.check())

    fun refreshPermissions() {
        permissions.value = permissionChecker.check()
    }

    /** The snoozer being edited, or null when creating one. Comes from the route. */
    private val alarmId: Int? = savedStateHandle.get<Int>(Routes.EditAlarm.ARG_ID)?.takeIf { it >= 0 }
    private val isNew = alarmId == null

    /**
     * The day picked in the Month view, for a new snoozer on that date only. Comes from the
     * route; a day already past becomes today (a snoozer can't be set in the past).
     */
    private val fromDate: LocalDate? =
        savedStateHandle.get<String>(Routes.EditAlarm.ARG_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { maxOf(it, timeSource.now().toLocalDate()) }

    /** The day page a new snoozer was opened from, if any (or the picked date's weekday). */
    private val fromDay: DayOfWeek? = fromDate?.dayOfWeek ?:
        savedStateHandle.get<Int>(Routes.EditAlarm.ARG_DAY)?.takeIf { it in 1..7 }?.let(DayOfWeek::of)

    // Kept in SavedStateHandle so a half-filled form survives the process being killed. Stored
    // as plain text (EditForm.encode), never as a class: Android reads every saved value before
    // this ViewModel exists, so a saved class that no longer matches would crash right there.
    // Text always reads back, and decode() rejects anything malformed, so both start fresh.
    private val form = MutableStateFlow(restore(KEY_FORM))
    private val original = MutableStateFlow(restore(KEY_ORIGINAL))

    private val events = Channel<EditEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditEvent> = events.receiveAsFlow()

    /** Prevents a double tap on Save or Delete from writing twice. */
    private var busy = false

    /** Null while the saved snoozer is loading. */
    // The clock keeps "That time has already passed today" current while the screen is open.
    val uiState: StateFlow<EditUiState?> = combine(form, original, permissions, timeSource.minuteTicks) { f, o, p, now ->
        if (f == null || o == null) null
        else EditUi.describe(f, o, isNew, permissions = p, now = now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Both or neither: a form without its original can't tell what changed.
        if (form.value == null || original.value == null) {
            form.value = null
            original.value = null
            viewModelScope.launch { load() }
        }
    }

    private fun restore(key: String): EditForm? = savedStateHandle.get<String>(key)?.let(EditForm::decode)

    private suspend fun load() {
        val loaded = try {
            if (alarmId == null) {
                EditForm.new(fromDay, date = fromDate)
            } else {
                repository.getAlarmById(alarmId)?.let(EditForm::from)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not load snoozer $alarmId", e)
            events.send(EditEvent("Couldn't open that snoozer. Try again."))
            return
        }
        if (loaded == null) {
            // Deleted elsewhere (or a stale link): nothing to edit.
            events.send(EditEvent("That snoozer no longer exists"))
            return
        }
        original.value = loaded
        savedStateHandle[KEY_ORIGINAL] = loaded.encode()
        setForm(loaded)
    }

    private fun setForm(value: EditForm) {
        form.value = value
        savedStateHandle[KEY_FORM] = value.encode()
    }

    private fun edit(change: (EditForm) -> EditForm) {
        form.value?.let { setForm(change(it)) }
    }

    fun onStartChange(hour: Int, minute: Int) = edit { it.withStart(hour, minute) }
    fun onEndChange(hour: Int, minute: Int) = edit { it.withEnd(hour, minute) }
    fun onToggleDay(day: DayOfWeek) = edit { it.toggleDay(day) }
    fun onPreset(preset: RepeatPreset) = edit { it.withPreset(preset) }
    fun onCategory(category: Category) = edit { it.switchCategory(category, isNew, fromDay) }

    /**
     * "One date" or "Every week". Switching to one date with none picked yet offers the next
     * day (from today) on one of the chosen weekdays, or today.
     */
    fun onOneDateMode(oneDate: Boolean) = edit { f ->
        when {
            !oneDate -> f.repeatWeekly()
            f.oneDate != null -> f
            else -> {
                val today = timeSource.now().toLocalDate()
                val next = (0L..6L).map { today.plusDays(it) }.firstOrNull { it.dayOfWeek in f.days } ?: today
                f.withOneDate(next)
            }
        }
    }

    fun onPickDate(date: LocalDate) = edit { it.withOneDate(date) }
    fun onName(name: String) = edit { it.withName(name) }
    fun onClearDates() = edit { it.clearDates() }

    fun save() {
        val current = form.value ?: return
        if (busy || uiState.value?.canSave != true) return
        // The screen only leaves after the write finishes; leaving clears this ViewModel and
        // would cancel a write still in flight.
        runWrite(failure = "Couldn't save. Your changes are still here; try again.") {
            if (alarmId == null) {
                actions.create(current.toNewAlarm())
            } else {
                // Re-read so the update starts from what is saved now (e.g. its on/off state).
                val saved = repository.getAlarmById(alarmId)
                    ?: return@runWrite EditEvent("That snoozer no longer exists")
                actions.update(old = saved, new = current.applyTo(saved))
            }
            EditEvent("Snoozer saved")
        }
    }

    fun delete() {
        if (busy || alarmId == null) return
        runWrite(failure = "Couldn't delete. Try again.") {
            repository.getAlarmById(alarmId)?.let { actions.delete(it) }
            EditEvent("Snoozer deleted")
        }
    }

    /**
     * Runs a database write once at a time. If it throws (disk full, database locked), the app
     * stays open: the screen shows [failure], keeps the form, and Save works again.
     */
    private fun runWrite(failure: String, write: suspend () -> EditEvent) {
        busy = true
        viewModelScope.launch {
            val result = try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Write failed", e)
                EditEvent(failure, leave = false)
            }
            if (!result.leave) busy = false
            events.send(result)
        }
    }

    private companion object {
        const val TAG = "EditAlarmViewModel"
        // New keys: values saved under the old ones were Serializable objects.
        const val KEY_FORM = "form_text"
        const val KEY_ORIGINAL = "original_text"
    }
}
