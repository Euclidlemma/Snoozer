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
 * Onboarding shows once, for people new to Snoozer. That's a fresh install, or an install with
 * no snoozers at all (for example after clearing the app's data). Someone updating from an
 * older version who already has snoozers knows the app, so it's marked seen without showing.
 * After that, it only opens from the "?" button and the edit screen's link.
 */
fun firstLaunch(seen: Boolean, freshInstall: Boolean, hasSnoozers: Boolean): FirstLaunch = when {
    seen -> FirstLaunch.GoHome
    freshInstall || !hasSnoozers -> FirstLaunch.ShowOnboarding
    else -> FirstLaunch.MarkSeenQuietly
}

/** Whether onboarding was seen, and whether this is a fresh install. Faked in tests. */
interface OnboardingStore {
    fun hasSeen(): Boolean
    fun markSeen()

    /** True until the app is updated for the first time after being installed. */
    fun isFreshInstall(): Boolean
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

    // commit(), not apply(): the start screen is picked from this right after, possibly in a
    // new process, so the write must have landed.
    override fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).commit()
    }

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
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    abstract fun bindOnboardingStore(impl: SharedPrefsOnboardingStore): OnboardingStore
}
