package com.example.snoozer

import android.app.Application
import com.example.snoozer.receiver.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The reminder channels' names are in the phone's language; refresh them in case it changed.
        ReminderNotifier.ensureChannels(this)
    }
}
