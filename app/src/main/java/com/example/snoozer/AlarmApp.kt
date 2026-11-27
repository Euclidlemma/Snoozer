package com.example.snoozer

import android.app.Application
import android.content.res.Configuration
import com.example.snoozer.receiver.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The reminder channels' names are in the phone's language; refresh them in case it changed.
        ReminderNotifier.ensureChannels(this)
    }

    // The phone's language can change while the process lives on; rename the channels then too.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ReminderNotifier.ensureChannels(this)
    }
}
