package com.example.snoozer.timetable

import androidx.compose.runtime.Immutable
import com.example.snoozer.alarm.Category
import com.example.snoozer.R
import com.example.snoozer.util.Strings

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
        /** Set when the ticked classes would need more alarms than Android allows; Add is off. */
        val limitNote: String? = null,
    ) : ImportUiState {
        /** How many will be added: ticked, and not already in Snoozer. */
        val addCount: Int get() = rows.count { it.checked && !it.alreadyAdded }

        fun addLabel(strings: Strings): String =
            if (addCount == 0) strings.get(R.string.import_pick_one)
            else strings.plural(R.plurals.import_add, addCount, addCount)

        /** "Found 6 classes" over the list. */
        fun foundLine(strings: Strings): String = strings.plural(R.plurals.import_found, rows.size, rows.size)
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
