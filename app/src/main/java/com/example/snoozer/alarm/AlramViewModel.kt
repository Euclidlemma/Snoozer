package com.example.snoozer.alarm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.scheduler.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Backs the edit screen until it gets its own ViewModel (redesign step 2).
 * The week screen now reads alarms through DayPickerViewModel.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val actions: AlarmActions,
) : ViewModel() {

    private val _selectedAlarm = MutableLiveData<Alarm?>()
    val selectedAlarm: LiveData<Alarm?> = _selectedAlarm

    fun setSelectedAlarm(alarm: Alarm?) {
        _selectedAlarm.value = alarm
    }

    fun insertAlarm(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, days: List<DayOfWeek>, work: String) {
        val alarm = Alarm(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            daysOfWeek = days,
            work = work
        )
        viewModelScope.launch {
            val id = repository.insertAlarm(alarm)
            scheduler.scheduleAlarm(alarm.copy(id = id.toInt()))
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            scheduler.cancelAlarm(alarm)
            actions.releaseRingerIfIdle(alarm)
        }
    }

    fun updateAlarm(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, days: List<DayOfWeek>, work: String) {
        val current = _selectedAlarm.value ?: return
        // copy() keeps the on/off state; editing a switched-off window doesn't switch it back on.
        val updatedAlarm = current.copy(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            daysOfWeek = days,
            work = work
        )
        viewModelScope.launch {
            repository.updateAlarm(updatedAlarm)
            scheduler.cancelAlarm(current)
            scheduler.scheduleAlarm(updatedAlarm)
            actions.releaseRingerIfIdle(current)
        }
    }
}
