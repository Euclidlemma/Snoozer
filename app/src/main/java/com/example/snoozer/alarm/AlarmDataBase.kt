package com.example.snoozer.alarm

import androidx.room.Database
import androidx.room.RoomDatabase

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class, QuietStretch::class], version = 4, exportSchema = true)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun quietStretchDao(): QuietStretchDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Entity field `work: String` is non-null, so the column must be NOT NULL
                // (with a default for existing rows) or Room's schema validation fails.
                db.execSQL("ALTER TABLE alarms ADD COLUMN work TEXT NOT NULL DEFAULT 'Work'")
            }
        }

        // v3: an optional name and the dates a timetable import sets. All nullable with no
        // default, matching the entity, so existing snoozers read back unchanged (no name, no dates).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN name TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN fromDate TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN untilDate TEXT")
            }
        }

        // v4: the log of quiet stretches behind the weekly report. Matches the entity exactly
        // (no SQL defaults), which Room checks on open.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiet_stretches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER, `category` TEXT NOT NULL, " +
                        "`louder` INTEGER NOT NULL, `missed` INTEGER NOT NULL)"
                )
            }
        }

        /** Every step from the first version to now; the migration tests run these same ones. */
        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun getDatabase(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_database"
                ).addMigrations(*MIGRATIONS).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
