package com.example.snoozer

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.AlarmDatabase
import com.example.snoozer.alarm.QuietStretch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Updating the app must keep everyone's snoozers. Each test builds an old database from its
 * exported schema (app/schemas; v1, from before schemas were exported, is rebuilt in
 * androidTest/assets as v2 without `work`), fills it like that version of the app did, runs the
 * app's own migrations, checks the result against the current schema, then reads it back
 * through Room as the app would.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var opened: AlarmDatabase? = null

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AlarmDatabase::class.java)

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun close() {
        opened?.close()
        context.deleteDatabase(name)
    }

    /** Migrates to the latest version, validating it against the current schema, then opens it with Room. */
    private fun migrateAndOpen(): AlarmDatabase {
        helper.runMigrationsAndValidate(name, LATEST, true, *AlarmDatabase.MIGRATIONS).close()
        return Room.databaseBuilder(context, AlarmDatabase::class.java, name)
            .addMigrations(*AlarmDatabase.MIGRATIONS)
            .build()
            .also { opened = it }
    }

    @Test
    fun from1_keepsSnoozers_asWork() {
        helper.createDatabase(name, 1).use {
            it.execSQL(
                "INSERT INTO alarms (id, startHour, startMinute, endHour, endMinute, daysOfWeek, isEnabled) " +
                    "VALUES (5, 22, 30, 6, 0, 'FRIDAY,SATURDAY', 1)"
            )
        }
        val alarm = runBlocking { migrateAndOpen().alarmDao().getAlarmById(5) }
        assertEquals(
            Alarm(id = 5, startHour = 22, startMinute = 30, endHour = 6, endMinute = 0,
                daysOfWeek = listOf(FRIDAY, SATURDAY), work = "Work", isEnabled = true),
            alarm,
        )
    }

    @Test
    fun from2_keepsCategoryAndOnOff_withNoNameOrDates() {
        helper.createDatabase(name, 2).use {
            it.execSQL(
                "INSERT INTO alarms (id, startHour, startMinute, endHour, endMinute, daysOfWeek, work, isEnabled) " +
                    "VALUES (1, 9, 0, 11, 15, 'MONDAY,WEDNESDAY', 'Study', 0), " +
                    "(2, 13, 0, 17, 0, '', 'Work', 1)"
            )
        }
        val alarms = runBlocking { migrateAndOpen().alarmDao().getAllAlarmsSuspend() }.sortedBy { it.id }
        assertEquals(
            listOf(
                Alarm(id = 1, startHour = 9, startMinute = 0, endHour = 11, endMinute = 15,
                    daysOfWeek = listOf(MONDAY, WEDNESDAY), work = "Study", isEnabled = false),
                Alarm(id = 2, startHour = 13, startMinute = 0, endHour = 17, endMinute = 0,
                    daysOfWeek = emptyList(), work = "Work", isEnabled = true),
            ),
            alarms,
        )
    }

    @Test
    fun from3_keepsNameAndDates_andStartsAnEmptyQuietLog() {
        helper.createDatabase(name, 3).use {
            it.execSQL(
                "INSERT INTO alarms (id, startHour, startMinute, endHour, endMinute, daysOfWeek, work, isEnabled, name, fromDate, untilDate) " +
                    "VALUES (7, 9, 0, 10, 20, 'WEDNESDAY', 'Study', 1, 'MATH 101', '2026-09-09', '2026-12-09')"
            )
        }
        val db = migrateAndOpen()
        val alarm = runBlocking { db.alarmDao().getAlarmById(7) }
        assertEquals(
            Alarm(id = 7, startHour = 9, startMinute = 0, endHour = 10, endMinute = 20,
                daysOfWeek = listOf(WEDNESDAY), work = "Study", name = "MATH 101",
                fromDate = LocalDate.of(2026, 9, 9), untilDate = LocalDate.of(2026, 12, 9)),
            alarm,
        )
        // The new table is empty and takes the weekly report's log as the app writes it.
        runBlocking {
            val log = db.quietStretchDao()
            assertEquals(null, log.open())
            val id = log.insert(QuietStretch(startMillis = 1_000L, endMillis = null, category = "Study", louder = false, missed = false))
            assertEquals(id, log.open()?.id)
        }
    }

    /** Each single step lands exactly on that version's exported schema, not only the last one. */
    @Test
    fun everyStepMatchesItsSchema() {
        helper.createDatabase(name, 1).close()
        AlarmDatabase.MIGRATIONS.forEach { step ->
            helper.runMigrationsAndValidate(name, step.endVersion, true, step).close()
        }
    }

    private companion object {
        const val LATEST = 4
    }
}
