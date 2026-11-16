package com.example.snoozer.alarm

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    /** Emits the full list again whenever the table changes. */
    @Query("SELECT * FROM alarms")
    fun observeAll(): Flow<List<Alarm>>

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