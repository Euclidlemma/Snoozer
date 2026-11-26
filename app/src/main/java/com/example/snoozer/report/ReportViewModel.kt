package com.example.snoozer.report

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snoozer.alarm.QuietStretchDao
import com.example.snoozer.util.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which report week was last opened, for the week screen's dot. Kept in report_state.xml.
 * One shared instance, so opening the report clears the dot on the week screen right away.
 */
@Singleton
class ReportSeenStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val seen = MutableStateFlow(prefs.getString(KEY_WEEK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    val seenWeek: StateFlow<LocalDate?> = seen.asStateFlow()

    fun markSeen(week: LocalDate) {
        if (seen.value == week) return
        seen.value = week
        prefs.edit { putString(KEY_WEEK, week.toString()) }
    }

    private companion object {
        const val PREFS = "report_state"
        const val KEY_WEEK = "seen_week"
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val dao: QuietStretchDao,
    private val timeSource: TimeSource,
    private val seen: ReportSeenStore,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * Rebuilt as the log changes and every minute (a stretch still going keeps growing, and at
     * Monday 00:00 the report moves on to the week just finished, even with the screen open).
     */
    val uiState: StateFlow<ReportUiState> =
        combine(dao.observeSince(WeeklyReport.keepFrom(timeSource.now(), zone)), timeSource.minuteTicks) { stretches, now ->
            // Opening the report (or having it open when a new one comes in) clears the dot.
            seen.markSeen(WeeklyReport.reportWeek(now))
            WeeklyReport.build(stretches, now, zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState.Loading)

    init {
        // Drop stretches older than the report shows. Nothing depends on the result.
        viewModelScope.launch {
            try {
                dao.deleteEndedBefore(WeeklyReport.keepFrom(timeSource.now(), zone))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ReportViewModel", "Could not prune the quiet log", e)
            }
        }
    }
}
