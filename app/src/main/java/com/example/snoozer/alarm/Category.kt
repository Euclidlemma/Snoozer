package com.example.snoozer.alarm

/**
 * What a quiet window is for. Stored in the database as plain text ([Alarm.work] holds
 * [label]), so adding this type needed no migration.
 */
enum class Category(val label: String) {
    Work("Work"),
    Study("Study");

    companion object {
        /** Reads the stored text. Anything unknown counts as [Work], as the colors always did. */
        fun from(stored: String): Category = entries.firstOrNull { it.label == stored } ?: Work

        /** The category whose level this is (each level belongs to one category). */
        fun forLevel(level: QuietLevel): Category = entries.first { it.quietLevel == level }
    }
}

/** This window's category, read from the stored text. */
val Alarm.category: Category get() = Category.from(work)
