package com.example.snoozer

import com.example.snoozer.receiver.QuietService.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/** The jobs QuietService carries in its Intent survive the trip, and bad values make no job. */
class QuietJobTest {
    private fun roundTrip(job: Job) = Job.decode(job.kind, job.alarmId, job.day)

    @Test
    fun everyJob_comesBackTheSame() {
        listOf(Job.Mute(7), Job.Unmute(7, DayOfWeek.SUNDAY), Job.Reschedule).forEach {
            assertEquals(it, roundTrip(it))
        }
    }

    @Test
    fun missingOrBadValues_makeNoJob() {
        assertNull(Job.decode(null, 7, 1))
        assertNull(Job.decode("REMIND", 7, 1))   // reminders don't go through the service
        assertNull(Job.decode("MUTE", -1, -1))
        assertNull(Job.decode("UNMUTE", 7, 0))
        assertNull(Job.decode("UNMUTE", -1, 3))
    }
}
