package com.example.snoozer.alarm

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms")
    fun getAllAlarms(): LiveData<List<Alarm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    // for background alram update
    @Query("SELECT * FROM alarms")
    suspend fun getAllAlarmsSuspend(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :alarmID")
    suspend fun getAlarmById(alarmID: Int): Alarm?

}