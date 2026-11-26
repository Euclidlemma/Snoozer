package com.example.snoozer.alarm

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * One stretch of time Snoozer actually held the phone quiet (or was meant to but couldn't).
 * Written as the ringer changes, read by the weekly report. Times are epoch milliseconds, so a
 * time-zone change doesn't move what already happened; the report reads them in the phone's
 * zone. Kept on the phone only.
 */
@Entity(tableName = "quiet_stretches")
data class QuietStretch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMillis: Long,
    /** Null while it's still going. */
    val endMillis: Long?,
    /** The category that set the level ([Category.label]); with overlaps, the quieter one. */
    val category: String,
    /** The ringer was louder than Snoozer set it when the stretch ended: turned back up by hand. */
    val louder: Boolean,
    /** Snoozer had no Do Not Disturb access: planned quiet that didn't happen. */
    val missed: Boolean,
)

/** The stretch's category, read from the stored text. */
val QuietStretch.categoryValue: Category get() = Category.from(category)

@Dao
interface QuietStretchDao {
    @Insert
    suspend fun insert(stretch: QuietStretch): Long

    @Update
    suspend fun update(stretch: QuietStretch)

    /** The stretch still going, if any (there is at most one). */
    @Query("SELECT * FROM quiet_stretches WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun open(): QuietStretch?

    /** Every stretch that overlaps [fromMillis] or later, again on each change. */
    @Query("SELECT * FROM quiet_stretches WHERE endMillis IS NULL OR endMillis > :fromMillis ORDER BY startMillis")
    fun observeSince(fromMillis: Long): Flow<List<QuietStretch>>

    /** Whether anything was logged before [millis]: the report then has a past to show. */
    @Query("SELECT EXISTS(SELECT 1 FROM quiet_stretches WHERE startMillis < :millis)")
    fun observeAnyBefore(millis: Long): Flow<Boolean>

    /** Drops finished stretches older than the report keeps. */
    @Query("DELETE FROM quiet_stretches WHERE endMillis IS NOT NULL AND endMillis < :millis")
    suspend fun deleteEndedBefore(millis: Long)
}

/**
 * Records what [AlarmActions] did to the ringer, as stretches. An interface so the rules are
 * unit-tested with a fake; [RoomQuietLog] is the real one.
 */
interface QuietLog {
    /**
     * From [at], the phone is quiet for [category] ([missed]: it should be, but Snoozer has no
     * access). The same as the open stretch continues it; anything else ends the open one at
     * [at] and starts a new one. [louder]: the ringer had been turned up during the open one.
     */
    suspend fun quiet(category: Category, missed: Boolean, at: Long, louder: Boolean)

    /** Nothing is quiet any more: ends the open stretch at [at]. */
    suspend fun stop(at: Long, louder: Boolean)

    /** The stretch still going, if any. */
    suspend fun open(): QuietStretch?
}

class RoomQuietLog @Inject constructor(private val dao: QuietStretchDao) : QuietLog {
    override suspend fun quiet(category: Category, missed: Boolean, at: Long, louder: Boolean) {
        val open = dao.open()
        if (open != null && open.category == category.label && open.missed == missed) {
            if (louder && !open.louder) dao.update(open.copy(louder = true))
            return
        }
        if (open != null) dao.update(open.copy(endMillis = maxOf(at, open.startMillis), louder = open.louder || louder))
        dao.insert(QuietStretch(startMillis = at, endMillis = null, category = category.label, louder = false, missed = missed))
    }

    override suspend fun stop(at: Long, louder: Boolean) {
        val open = dao.open() ?: return
        dao.update(open.copy(endMillis = maxOf(at, open.startMillis), louder = open.louder || louder))
    }

    override suspend fun open(): QuietStretch? = dao.open()
}
