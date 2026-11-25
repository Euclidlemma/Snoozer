package com.example.snoozer

import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.ExplainIcon
import com.example.snoozer.alarm.QuietLevel
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
    fun firstLaunch_showsOnceForNewPeopleOnly() {
        // Fresh install: show it.
        assertEquals(FirstLaunch.ShowOnboarding, firstLaunch(seen = false, freshInstall = true, hasSnoozers = false))
        // Data cleared (no snoozers) after updates: new to us again, show it.
        assertEquals(FirstLaunch.ShowOnboarding, firstLaunch(seen = false, freshInstall = false, hasSnoozers = false))
        // Existing user updating to this version: mark it seen, don't show.
        assertEquals(FirstLaunch.MarkSeenQuietly, firstLaunch(seen = false, freshInstall = false, hasSnoozers = true))
        // Seen before: never again on launch (only from "?").
        assertEquals(FirstLaunch.GoHome, firstLaunch(seen = true, freshInstall = true, hasSnoozers = false))
        assertEquals(FirstLaunch.GoHome, firstLaunch(seen = true, freshInstall = false, hasSnoozers = true))
    }
}
