package com.example.snoozer.alarm

import androidx.room.Database
import androidx.room.RoomDatabase

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Alarm::class], version = 3, exportSchema = true)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

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

        fun getDatabase(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
