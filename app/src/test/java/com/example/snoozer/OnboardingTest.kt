package com.example.snoozer

import androidx.lifecycle.SavedStateHandle
import com.example.snoozer.Route.Routes
import com.example.snoozer.onboarding.OnboardingExit
import com.example.snoozer.onboarding.OnboardingMode
import com.example.snoozer.onboarding.OnboardingSpec
import com.example.snoozer.onboarding.OnboardingStore
import com.example.snoozer.onboarding.OnboardingViewModel
import com.example.snoozer.onboarding.ringerAt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    private class FakeStore(var seen: Boolean = false) : OnboardingStore {
        override fun hasSeen() = seen
        override fun markSeen() { seen = true }
        override fun isFreshInstall() = true
        override fun hasUsedBefore() = false
        override fun hasStarted() = false
        override fun markStarted() {}
    }

    private fun viewModel(mode: String?, store: OnboardingStore) =
        OnboardingViewModel(SavedStateHandle(mapOf(Routes.Onboarding.ARG_MODE to mode)), store)

    @Test
    fun firstRun_startsAtWelcome_skips_andEndsWithGetStarted() {
        val spec = OnboardingSpec.of(OnboardingMode.FirstRun)
        assertEquals(0, spec.startPage)
        assertTrue(spec.showSkip)
        assertEquals("Next", spec.buttonLabel(0))
        assertEquals("Get started", spec.buttonLabel(4))
        assertFalse(spec.canGoBack(0))
        assertTrue(spec.canGoBack(1))
    }

    @Test
    fun help_startsAtTwoKinds_closes_andEndsWithDone() {
        val spec = OnboardingSpec.of(OnboardingMode.Help)
        assertEquals(1, spec.startPage)
        assertFalse(spec.showSkip)
        assertEquals("Done", spec.buttonLabel(4))
        // Back stops at where help started: it doesn't wander back to Welcome.
        assertFalse(spec.canGoBack(1))
        assertTrue(spec.canGoBack(2))
    }

    @Test
    fun help_pagerHoldsOnlyItsOwnPages() {
        // Help leaves Welcome out of the pager, so it can't be swiped back to.
        val help = OnboardingSpec.of(OnboardingMode.Help)
        assertEquals(4, help.pageCount)
        assertEquals(1, help.pageAt(0))
        assertEquals(4, help.pageAt(help.pageCount - 1))
        val firstRun = OnboardingSpec.of(OnboardingMode.FirstRun)
        assertEquals(5, firstRun.pageCount)
        assertEquals(0, firstRun.pageAt(0))
    }

    @Test
    fun modeComesFromTheRoute() {
        assertEquals(OnboardingMode.FirstRun, viewModel(Routes.Onboarding.MODE_FIRST_RUN, FakeStore()).spec.mode)
        assertEquals(OnboardingMode.Help, viewModel(Routes.Onboarding.MODE_HELP, FakeStore()).spec.mode)
        assertEquals(OnboardingMode.FirstRun, viewModel(null, FakeStore()).spec.mode)
    }

    @Test
    fun finishingTheFirstRun_marksSeen_andGoesHome() = runBlocking {
        val store = FakeStore()
        val vm = viewModel(Routes.Onboarding.MODE_FIRST_RUN, store)
        vm.finish()
        assertTrue(store.seen)
        assertEquals(OnboardingExit.GoHome, vm.exitFlow.first())
    }

    @Test
    fun closingHelp_goesBack() = runBlocking {
        val store = FakeStore(seen = true)
        val vm = viewModel(Routes.Onboarding.MODE_HELP, store)
        vm.finish()
        assertTrue(store.seen)
        assertEquals(OnboardingExit.Close, vm.exitFlow.first())
    }

    @Test
    fun overlapPage_ringerFollowsTheQuietestWindow() {
        // Pretend day 08-18: Work 09-17, Study 10-12.
        assertEquals("Normal", ringerAt(0.05f))
        assertEquals("Vibrate", ringerAt(0.15f))
        assertEquals("Silent", ringerAt(0.3f))
        assertEquals("Vibrate", ringerAt(0.6f))
        assertEquals("Normal", ringerAt(0.95f))
    }
}
