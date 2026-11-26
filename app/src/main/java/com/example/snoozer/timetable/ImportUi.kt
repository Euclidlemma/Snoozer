package com.example.snoozer.timetable

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Category

/** What the import preview shows. Built by the import ViewModel from the parsed file. */
@Immutable
sealed interface ImportUiState {
    /** Reading and parsing the file. */
    data object Loading : ImportUiState

    /** The file couldn't be opened or read. */
    data class Failed(val message: String) : ImportUiState

    /**
     * The file was read. [rows] can be empty (nothing weekly in it); [skipped] then says why.
     * [category] applies to every class added.
     */
    data class Ready(
        val rows: List<ImportRowUi>,
        val category: Category,
        /** "3 skipped: 2 all-day, 1 not weekly", or null when nothing was skipped. */
        val skipped: String?,
        /** True while the classes are being saved, so Add can't be tapped twice. */
        val saving: Boolean = false,
    ) : ImportUiState {
        /** How many will be added: ticked, and not already in Snoozer. */
        val addCount: Int get() = rows.count { it.checked && !it.alreadyAdded }

        val addLabel: String
            get() = when (addCount) {
                0 -> "Pick a class to add"
                1 -> "Add 1 class"
                else -> "Add $addCount classes"
            }

        /** "Found 6 classes" over the list. */
        val foundLine: String
            get() = if (rows.size == 1) "Found 1 weekly class" else "Found ${rows.size} weekly classes"
    }
}

/** One class found in the file, as a row with a checkbox. */
@Immutable
data class ImportRowUi(
    /** Stable across rebuilds, so a tick stays on its row. */
    val key: String,
    val name: String,
    /** "09:30 – 10:20". */
    val time: String,
    val endsNextDay: Boolean,
    /** "Mon, Wed, Fri". */
    val days: String,
    /** "8 Sep – 4 Dec", "from 8 Sep", or null with no dates. */
    val dates: String?,
    val checked: Boolean,
    /** The same class (name, times and days) is already saved, so it won't be added twice. */
    val alreadyAdded: Boolean,
)
