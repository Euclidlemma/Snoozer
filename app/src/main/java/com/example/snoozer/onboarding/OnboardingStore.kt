package com.example.snoozer.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** What to do about onboarding when the app opens. */
enum class FirstLaunch {
    /** Show the onboarding pages before Home. */
    ShowOnboarding,

    /** An existing user updating to this version: mark it seen without showing it. */
    MarkSeenQuietly,

    /** Already seen: go straight to Home. */
    GoHome,
}

/**
 * Onboarding shows once, for people new to Snoozer. That's a fresh install, or an app that was
 * never used (for example after clearing the app's data). Someone updating from an older version
 * who has used the app knows it, so it's marked seen without showing.
 * Once onboarding has been shown ([started]), it keeps showing until it's finished: the app
 * creates its database as soon as it runs, so "used before" alone would skip a half-done
 * onboarding after a relaunch.
 * After that, it only opens from the "?" button and the edit screen's link.
 */
fun firstLaunch(seen: Boolean, started: Boolean, freshInstall: Boolean, usedBefore: Boolean): FirstLaunch = when {
    seen -> FirstLaunch.GoHome
    started || freshInstall || !usedBefore -> FirstLaunch.ShowOnboarding
    else -> FirstLaunch.MarkSeenQuietly
}

/**
 * Picks the start screen and records the choice: onboarding shown now counts as started, and an
 * existing user is marked seen. Kept apart from MainActivity so it can be tested with a fake store.
 * The checks are cheap (preferences, package info, a file check): nothing waits on the database.
 */
fun decideFirstLaunch(store: OnboardingStore): FirstLaunch {
    val seen = store.hasSeen()
    val started = !seen && store.hasStarted()
    val fresh = !seen && !started && store.isFreshInstall()
    val usedBefore = !seen && !started && !fresh && store.hasUsedBefore()
    val decision = firstLaunch(seen, started, fresh, usedBefore)
    when (decision) {
        FirstLaunch.ShowOnboarding -> store.markStarted()
        FirstLaunch.MarkSeenQuietly -> store.markSeen()
        FirstLaunch.GoHome -> Unit
    }
    return decision
}

/** Whether onboarding was seen or started, and what the install looks like. Faked in tests. */
interface OnboardingStore {
    fun hasSeen(): Boolean
    fun markSeen()

    /** Onboarding was shown at least once but not finished yet. */
    fun hasStarted(): Boolean
    fun markStarted()

    /** True until the app is updated for the first time after being installed. */
    fun isFreshInstall(): Boolean

    /** Whether the app has been used here before (its snoozer database exists). */
    fun hasUsedBefore(): Boolean
}

/**
 * Kept in its own preferences file, onboarding.xml, which the backup rules leave out, so a
 * reinstall restored from backup still counts as new and shows onboarding again.
 */
class SharedPrefsOnboardingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : OnboardingStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun hasSeen(): Boolean = prefs.getBoolean(KEY_SEEN, false)

    // apply(): written in the background. Later reads in this process see it at once, and the
    // next launch is long after the write has landed.
    override fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    override fun hasStarted(): Boolean = prefs.getBoolean(KEY_STARTED, false)

    override fun markStarted() {
        prefs.edit().putBoolean(KEY_STARTED, true).apply()
    }

    override fun hasUsedBefore(): Boolean = context.getDatabasePath(DATABASE_NAME).exists()

    override fun isFreshInstall(): Boolean {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        // Android sets both to the install time and moves lastUpdateTime on every update.
        return info.firstInstallTime == info.lastUpdateTime
    }

    private companion object {
        const val PREFS = "onboarding" // onboarding.xml, excluded from backup
        const val KEY_SEEN = "seen"
        const val KEY_STARTED = "started"
        const val DATABASE_NAME = "alarm_database" // as in AlarmDatabase
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    abstract fun bindOnboardingStore(impl: SharedPrefsOnboardingStore): OnboardingStore
}
