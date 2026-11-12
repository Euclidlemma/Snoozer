package com.example.snoozer.alarm

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import androidx.lifecycle.map
import com.example.snoozer.receiver.RingerController
import com.example.snoozer.scheduler.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val allAlarms: LiveData<List<Alarm>> = repository.getAlarm()


    private val _selectedAlarm = MutableLiveData<Alarm?>()
    val selectedAlarm: LiveData<Alarm?> = _selectedAlarm

    fun setSelectedAlarm(alarm: Alarm?) {
        _selectedAlarm.value = alarm
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun insertAlarm(startHour: Int, startMinute:Int, endHour:Int, endMinute:Int, days: List<DayOfWeek>, work: String) {
        val alarm = Alarm(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            daysOfWeek = days,
            work = work
        )

        Log.d("Insert Alarm Daycheck", days.toString())

        viewModelScope.launch {
            val id = repository.insertAlarm(alarm)
            scheduler.scheduleAlarm(alarm.copy(id = id.toInt()))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            scheduler.cancelAlarm(alarm)
            releaseRingerIfIdle(alarm)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateAlarm(startHour: Int, startMinute:Int, endHour:Int, endMinute:Int, days: List<DayOfWeek>, work: String) {
        val current = _selectedAlarm.value

        if (current != null) {
            val updatedAlarm = Alarm(
                id = current.id,
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
                releaseRingerIfIdle(current)
            }
        }
    }

    /**
     * Deleting or editing a window while it is muting the phone cancels its pending unmute,
     * which would leave the phone silent. If [changed] (the old version) was active and no
     * saved window (including an edited one) still covers now, restore the ringer right away.
     */
    private suspend fun releaseRingerIfIdle(changed: Alarm) {
        val now = LocalDateTime.now()
        if (!AlarmTime.isActiveAt(changed, now)) return
        val stillActive = repository.getAllAlarmsDirect().any {
            it.isEnabled && AlarmTime.isActiveAt(it, now)
        }
        if (!stillActive) RingerController.restore(appContext)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getAlarmsForDay(dayInt: Int): LiveData<List<Alarm>> {
        val day = DayOfWeek.of(dayInt)
        return allAlarms.map { alarms ->
            alarms.filter { it.daysOfWeek.contains(day) }
                .sortedWith(compareBy({ it.startHour }, { it.startMinute }))
        }
    }
}
