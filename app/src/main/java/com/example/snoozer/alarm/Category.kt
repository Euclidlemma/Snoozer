package com.example.snoozer.alarm

import androidx.annotation.StringRes
import com.example.snoozer.R

/**
 * What a quiet window is for. Stored in the database as plain text ([Alarm.work] holds
 * [stored]), so adding this type needed no migration. [stored] is a key, not words people
 * read: it stays English in every language, and screens show [labelRes] instead.
 */
enum class Category(val stored: String, @StringRes val labelRes: Int) {
    Work("Work", R.string.category_work),
    Study("Study", R.string.category_study);

    /** The English name, still shown by screens that haven't moved to [labelRes] yet. */
    val label: String get() = stored

    companion object {
        /** Reads the stored text. Anything unknown counts as [Work], as the colors always did. */
        fun from(stored: String): Category = entries.firstOrNull { it.stored == stored } ?: Work

        /** The category whose level this is (each level belongs to one category). */
        fun forLevel(level: QuietLevel): Category = entries.first { it.quietLevel == level }
    }
}

/** This window's category, read from the stored text. */
val Alarm.category: Category get() = Category.from(work)
