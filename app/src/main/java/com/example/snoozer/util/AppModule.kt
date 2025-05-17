package com.example.snoozer.util

import android.content.Context
import com.example.snoozer.alarm.AlarmDao
import com.example.snoozer.alarm.AlarmDatabase
import com.example.snoozer.alarm.AlarmRepository
import com.example.snoozer.dayPickers.DayPickerViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AlarmDatabase {
        return AlarmDatabase.getDatabase(context)
    }

    @Provides
    fun provideAlarmDao(db: AlarmDatabase): AlarmDao {
        return db.alarmDao()
    }

    @Provides
    fun provideAlarmRepository(alarmDao: AlarmDao): AlarmRepository {
        return AlarmRepository(alarmDao)
    }

    @Provides
    fun provideDayPicker(): DayPickerViewModel {
        return DayPickerViewModel()
    }
}
