package com.example.snoozer.alarm
import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow


class AlarmRepository(private val alarmDao: AlarmDao) {
    //val alarms: LiveData<List<Alarm>> = alarmDao.getAllAlarms()

    fun getAlarm(): LiveData<List<Alarm>> {
        return alarmDao.getAllAlarms()
    }

    fun observeAlarms(): Flow<List<Alarm>> = alarmDao.observeAll()

    suspend fun insertAlarm(alarm: Alarm): Long {
       return alarmDao.insertAlarm(alarm)
    }

    suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
    }

    suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun getAllAlarmsDirect(): List<Alarm> {
        return alarmDao.getAllAlarmsSuspend()
    }

    suspend fun getAlarmById(alarmID: Int): Alarm? {
        return alarmDao.getAlarmById(alarmID)
    }
}