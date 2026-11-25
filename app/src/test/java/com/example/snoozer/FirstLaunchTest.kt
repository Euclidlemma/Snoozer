package com.example.snoozer

import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.explain
import com.example.snoozer.onboarding.FirstLaunch
import com.example.snoozer.onboarding.OnboardingStore
import com.example.snoozer.onboarding.decideFirstLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The start-screen decision as MainActivity runs it, through a fake store that keeps state. */
class FirstLaunchTest {

    private class Store(
        var seen: Boolean = false,
        var started: Boolean = false,
        var fresh: Boolean = false,
        var dbExists: Boolean = false,
    ) : OnboardingStore {
        override fun hasSeen() = seen
        override fun markSeen() { seen = true }
        override fun hasStarted() = started
        override fun markStarted() { started = true }
        override fun isFreshInstall() = fresh
        override fun hasUsedBefore() = dbExists
    }

    @Test
    fun freshInstall_showsOnboarding_andRemembersItStarted() {
        val store = Store(fresh = true)
        assertEquals(FirstLaunch.ShowOnboarding, decideFirstLaunch(store))
        assertTrue(store.started)
        assertFalse(store.seen)
    }

    @Test
    fun clearedData_relaunchedMidOnboarding_stillShowsIt() {
        // Not fresh (updated before), no database yet: onboarding starts.
        val store = Store()
        assertEquals(FirstLaunch.ShowOnboarding, decideFirstLaunch(store))
        // The app then creates its database (reschedule, receivers) and is closed half-way.
        store.dbExists = true
        // Relaunch: it must continue, not be skipped as an "existing user".
        assertEquals(FirstLaunch.ShowOnboarding, decideFirstLaunch(store))
        assertFalse(store.seen)
    }

    @Test
    fun existingUserUpdating_isMarkedSeenQuietly() {
        val store = Store(dbExists = true)
        assertEquals(FirstLaunch.MarkSeenQuietly, decideFirstLaunch(store))
        assertTrue(store.seen)
        // And next time it goes straight Home.
        assertEquals(FirstLaunch.GoHome, decideFirstLaunch(store))
    }

    @Test
    fun finished_goesHome() {
        assertEquals(FirstLaunch.GoHome, decideFirstLaunch(Store(seen = true, started = true, fresh = true)))
    }

    @Test
    fun remindersOff_pointsToTheRightPlace() {
        assertEquals("Heads-up is off: allow notifications in Setup to get one.",
            Category.Study.explain(remindersOn = false, setupHasNotifications = true).lines.last().text)
        // Android 12 and older: Setup has no Notifications row.
        assertEquals("Heads-up is off: turn on Snoozer's notifications in Android settings to get one.",
            Category.Study.explain(remindersOn = false, setupHasNotifications = false).lines.last().text)
    }
}
