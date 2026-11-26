package com.example.snoozer.report

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Category

/** The strip on the week screen that opens the report. */
@Immutable
data class ReportStripUi(
    /** "Last week · 14h 20m quiet", or "Your first report arrives Monday". */
    val title: String,
    /** "Work 9h 50m · Study 4h 30m", or "So far this week: 3h 20m". */
    val detail: String?,
    /** A report came in at Monday 00:00 that hasn't been opened yet: the yellow dot. */
    val isNew: Boolean,
    /** What screen readers say for the strip. */
    val description: String,
)

/** What the report screen shows. Built by [WeeklyReport]. */
@Immutable
sealed interface ReportUiState {
    data object Loading : ReportUiState

    /** Nothing logged before this week yet: the first report comes on Monday. */
    data class FirstWeek(
        /** "3h 20m" counted so far this week. */
        val soFar: String,
        /** "Next report Monday 00:00 · in 3 days". */
        val nextReport: String,
    ) : ReportUiState

    data class Ready(
        /** "Mon 17 – Sun 23 Nov". */
        val range: String,
        val totalMinutes: Int,
        /** Against the week before; null when there's nothing to compare with. */
        val change: ChangeUi?,
        val work: CategoryReportUi,
        val study: CategoryReportUi,
        /** Monday first. */
        val days: List<DayBarUi>,
        val highlights: List<HighlightUi>,
        /** Planned quiet that didn't happen, and sound turned back on. */
        val notes: List<String>,
        val nextReport: String,
        /** The weeks before, newest first. */
        val earlier: List<EarlierWeekUi>,
    ) : ReportUiState {
        /** Work's part of the total, 0..1 (0 when the week was empty). */
        val workShare: Float get() = if (totalMinutes == 0) 0f else work.minutes.toFloat() / totalMinutes
    }
}

/** "2h 10m more than the week before"; [up] null when it's the same. */
@Immutable
data class ChangeUi(val text: String, val up: Boolean?)

@Immutable
data class CategoryReportUi(
    val category: Category,
    val minutes: Int,
    /** "5 sessions". */
    val sessions: String,
    /** "▲ 1h 40m", or null with nothing to compare. */
    val change: String?,
)

@Immutable
data class DayBarUi(
    /** "M". */
    val letter: String,
    /** "Monday", for screen readers. */
    val name: String,
    val workMinutes: Int,
    val studyMinutes: Int,
    /** The day with the most quiet: gets the star. */
    val isQuietest: Boolean,
) {
    val totalMinutes: Int get() = workMinutes + studyMinutes
}

enum class HighlightIcon { Trophy, Calendar, Flame }

@Immutable
data class HighlightUi(val icon: HighlightIcon, val title: String, val value: String, val detail: String)

@Immutable
data class EarlierWeekUi(val range: String, val total: String)
