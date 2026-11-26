package com.example.snoozer

import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.ExplainIcon
import com.example.snoozer.alarm.QuietLevel
import com.example.snoozer.alarm.explain
import com.example.snoozer.alarm.explanation
import com.example.snoozer.alarm.quietLevel
import com.example.snoozer.onboarding.FirstLaunch
import com.example.snoozer.onboarding.firstLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationTest {

    @Test
    fun work_explainsVibrateAndAQuietHeadsUp() {
        val e = Category.Work.explanation
        assertEquals("During Work", e.title)
        assertEquals("Vibrate. Calls still buzz.", e.summary)
        assertEquals(listOf(ExplainIcon.Vibrate, ExplainIcon.Call, ExplainIcon.Notify), e.lines.map { it.icon })
        assertEquals("A quiet heads-up before it starts.", e.lines.last().text)
    }

    @Test
    fun study_explainsSilenceAlarmsAndTheFiveMinuteHeadsUp() {
        val e = Category.Study.explanation
        assertEquals("During Study", e.title)
        assertEquals("Fully silent. Only alarms ring.", e.summary)
        assertEquals(listOf(ExplainIcon.Silent, ExplainIcon.Alarm, ExplainIcon.Notify), e.lines.map { it.icon })
        assertEquals("A heads-up 5 minutes before it starts.", e.lines.last().text)
    }

    @Test
    fun explanation_followsTheQuietLevel() {
        // If a category's level ever changes, its explanation changes with it.
        Category.entries.forEach { c ->
            val first = c.explanation.lines.first().icon
            when (c.quietLevel) {
                QuietLevel.Vibrate -> assertEquals(ExplainIcon.Vibrate, first)
                QuietLevel.Silent -> assertEquals(ExplainIcon.Silent, first)
            }
            assertTrue(c.explanation.lines.size == 3)
        }
    }

    @Test
    fun notificationsOff_theReminderLineSaysSo() {
        Category.entries.forEach { c ->
            val line = c.explain(remindersOn = false).lines.last()
            assertEquals("Heads-up is off: allow notifications in Setup to get one.", line.text)
        }
        // The rest is unchanged.
        assertEquals(Category.Study.explanation.lines.dropLast(1), Category.Study.explain(false).lines.dropLast(1))
    }

    @Test
    fun firstLaunch_showsOnceForNewPeopleOnly() {
        // Fresh install: show it.
        assertEquals(FirstLaunch.ShowOnboarding, firstLaunch(seen = false, started = false, freshInstall = true, usedBefore = false))
        // Data cleared (database gone) after updates: new to us again, show it.
        assertEquals(FirstLaunch.ShowOnboarding, firstLaunch(seen = false, started = false, freshInstall = false, usedBefore = false))
        // Existing user updating to this version: mark it seen, don't show.
        assertEquals(FirstLaunch.MarkSeenQuietly, firstLaunch(seen = false, started = false, freshInstall = false, usedBefore = true))
        // Seen before: never again on launch (only from "?").
        assertEquals(FirstLaunch.GoHome, firstLaunch(seen = true, started = false, freshInstall = true, usedBefore = false))
        assertEquals(FirstLaunch.GoHome, firstLaunch(seen = true, started = false, freshInstall = false, usedBefore = true))
    }
}
