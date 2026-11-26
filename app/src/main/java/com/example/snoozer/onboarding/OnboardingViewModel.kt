package com.example.snoozer.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.snoozer.Route.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/** Why the pages are showing: the one-time first run, or opened again for help. */
enum class OnboardingMode { FirstRun, Help }

/** Where to go when the pages are done. */
enum class OnboardingExit {
    /** First run finished or skipped: Home replaces the pages. */
    GoHome,

    /** Help closed: back to the screen that opened it. */
    Close,
}

/** The fixed parts of the pages for a mode: where they start and what the buttons say. */
data class OnboardingSpec(
    val mode: OnboardingMode,
    /** The first run starts at Welcome; help skips straight to "Two kinds of quiet". */
    val startPage: Int,
    /** "Skip" on the first run; help has a close button instead. */
    val showSkip: Boolean,
) {
    /** "Next" on every page but the last, which finishes: "Get started" or "Done". */
    fun buttonLabel(page: Int): String = when {
        page < OnboardingPageCount - 1 -> "Next"
        mode == OnboardingMode.FirstRun -> "Get started"
        else -> "Done"
    }

    /** Back goes to the previous page, but not before where this mode started. */
    fun canGoBack(page: Int): Boolean = page > startPage

    /** How many pages this mode shows: help leaves out Welcome. */
    val pageCount: Int get() = OnboardingPageCount - startPage

    /** The page (0 = Welcome) at this mode's pager position [index]. */
    fun pageAt(index: Int): Int = index + startPage

    companion object {
        fun of(mode: OnboardingMode) = when (mode) {
            OnboardingMode.FirstRun -> OnboardingSpec(mode, startPage = 0, showSkip = true)
            OnboardingMode.Help -> OnboardingSpec(mode, startPage = 1, showSkip = false)
        }
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: OnboardingStore,
) : ViewModel() {

    /** From the route: "onboarding" defaults to the first run, "howItWorks" to help. */
    val spec: OnboardingSpec = OnboardingSpec.of(
        if (savedStateHandle.get<String>(Routes.Onboarding.ARG_MODE) == Routes.Onboarding.MODE_HELP) OnboardingMode.Help
        else OnboardingMode.FirstRun
    )

    private val exits = Channel<OnboardingExit>(Channel.BUFFERED)
    val exitFlow: Flow<OnboardingExit> = exits.receiveAsFlow()

    /** Skip, "Get started", "Done" and the close button all end up here. */
    fun finish() {
        // Seen either way, so the first run never comes back on its own.
        store.markSeen()
        exits.trySend(if (spec.mode == OnboardingMode.FirstRun) OnboardingExit.GoHome else OnboardingExit.Close)
    }
}
