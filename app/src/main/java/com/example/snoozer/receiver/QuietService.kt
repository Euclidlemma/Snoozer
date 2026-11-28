package com.example.snoozer.receiver

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.snoozer.R
import com.example.snoozer.alarm.AlarmActions
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

/**
 * Runs a mute, an unmute or a reschedule as a foreground service, for the second or so it takes.
 *
 * Android 17 silently ignores ringer changes (AudioManager.setRingerMode) from an app with no
 * visible screen, unless it runs a foreground service ("background audio hardening"). It does so
 * whatever the app targets, and Do Not Disturb access and exact alarms don't exempt it. Without
 * this, a snoozer starting while Snoozer is closed would never quiet the phone.
 *
 * LIMIT: this is enough only while targetSdk is 36 or lower. An app targeting 37 also needs the
 * service to have "while-in-use" capability, which a service started by an alarm doesn't get, so
 * the changes would be ignored again (Android already logs "would be ignored … level: full").
 * Find another way before raising targetSdk to 37. See developer.android.com, "Background audio
 * hardening".
 *
 * The alarm and system broadcasts that start it allow a foreground service from the background.
 * If one can't start anyway, the receivers do the work themselves, as before, which still works
 * on Android 16 and older.
 */
class QuietService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Jobs not yet finished; the service stops when the last one ends. Main thread only. */
    private var inFlight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
        } catch (e: Exception) {
            // Practically unreachable: Android makes the same check when the receiver starts the
            // service, and the receiver then does the work itself. Do the work anyway, since on
            // Android 16 and older the ringer change works without the foreground.
            Log.e(TAG, "Couldn't run in the foreground", e)
        }
        val job = intent?.let {
            Job.decode(it.getStringExtra(EXTRA_JOB), it.getIntExtra(EXTRA_ID, -1), it.getIntExtra(EXTRA_DAY, -1))
        }
        if (job == null) {
            if (inFlight == 0) stopSelf()
            return START_NOT_STICKY
        }
        inFlight++
        val actions = EntryPointAccessors.fromApplication(applicationContext, ReceiverEntryPoint::class.java).alarmActions()
        scope.launch {
            try {
                job.run(actions)
            } catch (e: Exception) {
                Log.e(TAG, "Error running $job", e)
            } finally {
                withContext(Dispatchers.Main) {
                    inFlight--
                    if (inFlight == 0) stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(getString(R.string.switching_sound))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    /**
     * One piece of work. It travels in the service's Intent as three plain values ([kind],
     * [alarmId], [day]), so the encoding can be tested without Android.
     */
    sealed interface Job {
        val kind: String
        val alarmId: Int get() = -1
        val day: Int get() = -1

        suspend fun run(actions: AlarmActions)

        data class Mute(override val alarmId: Int) : Job {
            override val kind get() = "MUTE"
            override suspend fun run(actions: AlarmActions) = actions.onMuteFired(alarmId)
        }

        data class Unmute(override val alarmId: Int, val startDay: DayOfWeek) : Job {
            override val kind get() = "UNMUTE"
            override val day get() = startDay.value
            override suspend fun run(actions: AlarmActions) = actions.onUnmuteFired(alarmId, startDay)
        }

        data object Reschedule : Job {
            override val kind get() = "RESCHEDULE"
            override suspend fun run(actions: AlarmActions) = actions.rescheduleAll()
        }

        companion object {
            /** The job [kind], [alarmId] and [day] describe, or null if they don't make one. */
            fun decode(kind: String?, alarmId: Int, day: Int): Job? = when (kind) {
                "MUTE" -> if (alarmId != -1) Mute(alarmId) else null
                "UNMUTE" -> if (alarmId != -1 && day in 1..7) Unmute(alarmId, DayOfWeek.of(day)) else null
                "RESCHEDULE" -> Reschedule
                else -> null
            }
        }
    }

    companion object {
        const val CHANNEL = "sound_changes"
        private const val TAG = "QuietService"
        private const val NOTIFICATION_ID = 7001
        private const val EXTRA_JOB = "job"
        private const val EXTRA_ID = "alarmId"
        private const val EXTRA_DAY = "dayOfWeek"

        /**
         * Starts [job] in the service. Returns false if Android doesn't allow a foreground service
         * right now; the caller then does the work itself.
         */
        fun start(context: Context, job: Job): Boolean {
            val intent = Intent(context, QuietService::class.java)
                .putExtra(EXTRA_JOB, job.kind)
                .putExtra(EXTRA_ID, job.alarmId)
                .putExtra(EXTRA_DAY, job.day)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException (Android 12+) or a background start ban.
                Log.w(TAG, "Foreground service not allowed; doing $job in the receiver", e)
                false
            }
        }
    }
}
