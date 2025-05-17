# 🔕 Snoozer - Scheduled Phone Mute App

Snoozer is an Android app that automatically mutes and unmutes your phone based on your chosen schedule. Perfect for classes, meetings, or sleep routines — never forget to silence your phone again!

---

## 📱 Features

- ✅ Mute and unmute your phone at scheduled times.
- 📅 Choose specific days of the week for the alarm to repeat.
- 🔁 Repeats weekly on selected days.
- 🔋 Uses `AlarmManager.setExactAndAllowWhileIdle` to support Doze mode.
- ♻️ Automatically reschedules alarms after they trigger.
- 🔄 Alarms persist across device reboots.
- 🧩 Built with modern Android architecture components:
    - Jetpack Compose
    - ViewModel + Hilt DI
    - Room Database

---

## 🛠 Architecture Overview

- **AlarmScheduler**: Manages scheduling, canceling, and repeating alarms.
- **AlarmReceiver**: Listens for alarm broadcasts and changes ringer mode.
- **BootReceiver**: Reschedules all alarms on device reboot.
- **AlarmRepository**: Interface to local Room database for alarm persistence.
- **AlarmViewModel**: Implements functions that combines scheduler and repository to save the Alarm at Room DB and sets the alarm by `AlarmScheduler`.

---

## 🔐 Required Permissions

To function properly, the app requests the following permissions:
- `SCHEDULE_EXACT_ALARM` – for precise scheduling
- `RECEIVE_BOOT_COMPLETED` – to reschedule alarms after device restart
- `ACCESS_NOTIFICATION_POLICY` – to control ringer mode (mute/unmute)

> These permissions must be granted by the user. The app guides the user if permission is missing.

---

## 💡 How It Works

1. **User sets an alarm** (e.g., mute at 9:00 AM, unmute at 11:00 AM on Mondays).
2. **App schedules two exact alarms**:
    - One for muting at the specified start time.
    - One for unmuting at the specified end time.
3. **When alarm fires**, `AlarmReceiver` adjusts the ringer mode accordingly.
4. **After unmuting**, the receiver reschedules the alarm for the same day next week.

---

### Requirements

- Android Studio Giraffe or newer
- Android 8.0+ (API 26+)
- Gradle with Jetpack Compose & Hilt setup

### TECH STACK

- Kotlin 
- Jetpack Compose 
- Room 
- Hilt (Dependency Injection)
- AlarmManager 
- BroadcastReceiver 
- ViewModel + LiveData

All illustrations and icons are the exclusive copyrighted property of Ivy. Any use, reproduction, distribution, or modification of these materials is strictly prohibited without explicit written permission from Ivy.
