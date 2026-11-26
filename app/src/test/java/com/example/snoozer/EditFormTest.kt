package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.EditAlarm.EditUi
import com.example.snoozer.EditAlarm.RepeatPreset
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.alarm.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.example.snoozer.util.Permissions
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY

class EditFormTest {

    private val base = EditForm(9, 0, 10, 30, listOf(MONDAY), Category.Work)

    @Test
    fun toggleDay_keepsMondayFirstOrder() {
        val f = base.toggleDay(FRIDAY).toggleDay(WEDNESDAY)
        assertEquals(listOf(MONDAY, WEDNESDAY, FRIDAY), f.days)
        assertEquals(listOf(WEDNESDAY, FRIDAY), f.toggleDay(MONDAY).days)
    }

    @Test
    fun preset_setsDaysAndIsRecognised() {
        val f = base.withPreset(RepeatPreset.Weekends)
        assertEquals(listOf(SATURDAY, SUNDAY), f.days)
        assertEquals(RepeatPreset.Weekends, EditUi.describe(f, base, isNew = false).preset)
        assertNull(EditUi.describe(base, base, isNew = false).preset)
    }

    @Test
    fun summary_sameDayAndOvernight() {
        assertEquals("Quiet for 1h 30m", EditUi.describe(base, base, false).summary)
        val night = base.withStart(22, 0).withEnd(7, 0)
        assertEquals("Quiet for 9h, until 07:00 the next day", EditUi.describe(night, base, false).summary)
    }

    @Test
    fun errors_blockSaving() {
        val same = base.withEnd(9, 0)
        assertEquals("Start and end can't be the same time.", EditUi.describe(same, base, false).error)
        assertFalse(EditUi.describe(same, base, false).canSave)
        val noDays = base.toggleDay(MONDAY)
        assertEquals("Pick at least one day.", EditUi.describe(noDays, base, false).error)
        assertTrue(EditUi.describe(base, base, false).canSave)
    }

    @Test
    fun hasChanges_onlyWhenEditedOrNew() {
        assertFalse(EditUi.describe(base, base, isNew = false).hasChanges)
        assertTrue(EditUi.describe(base.withEnd(11, 0), base, isNew = false).hasChanges)
        assertTrue(EditUi.describe(base, base, isNew = true).hasChanges)
    }

    @Test
    fun applyTo_keepsIdAndOnOffState() {
        val saved = Alarm(id = 7, startHour = 1, startMinute = 0, endHour = 2, endMinute = 0,
            daysOfWeek = listOf(SUNDAY), work = "Study", isEnabled = false)
        val updated = base.applyTo(saved)
        assertEquals(7, updated.id)
        assertFalse(updated.isEnabled)
        assertEquals(listOf(MONDAY), updated.daysOfWeek)
        assertEquals("Work", updated.work)
    }

    @Test
    fun category_readFromAndWrittenAsTheStoredText() {
        val saved = Alarm(id = 3, startHour = 9, startMinute = 0, endHour = 10, endMinute = 0,
            daysOfWeek = listOf(MONDAY), work = "Study")
        val form = EditForm.from(saved)
        assertEquals(Category.Study, form.category)
        // The database keeps the same text as before the enum existed.
        assertEquals("Study", form.applyTo(saved).work)
        assertEquals("Work", form.copy(category = Category.Work).toNewAlarm().work)
        assertEquals(Category.Work, EditForm.new(null).category)
    }

    @Test
    fun changingCategory_countsAsAChange() {
        val edited = base.copy(category = Category.Study)
        assertTrue(EditUi.describe(edited, base, isNew = false).hasChanges)
        assertEquals(Category.Study, EditUi.describe(edited, base, isNew = false).category)
    }

    @Test
    fun newForm_startsWithTheCategorysWindow() {
        val work = EditForm.new(null)
        assertEquals(listOf(9, 0, 17, 0), listOf(work.startHour, work.startMinute, work.endHour, work.endMinute))
        val study = EditForm.new(null, Category.Study)
        assertEquals(listOf(19, 0, 21, 0), listOf(study.startHour, study.startMinute, study.endHour, study.endMinute))
    }

    @Test
    fun switchCategory_untouchedNewForm_followsTheNewCategory() {
        val switched = EditForm.new(null).switchCategory(Category.Study, isNew = true, fromDay = null)
        assertEquals(EditForm.new(null, Category.Study), switched)
        // And back again.
        assertEquals(EditForm.new(null), switched.switchCategory(Category.Work, isNew = true, fromDay = null))
    }

    @Test
    fun switchCategory_keepsTimesTheUserChose() {
        val edited = EditForm.new(null).withStart(8, 30)
        val switched = edited.switchCategory(Category.Study, isNew = true, fromDay = null)
        assertEquals(Category.Study, switched.category)
        assertEquals(listOf(8, 30, 17, 0), listOf(switched.startHour, switched.startMinute, switched.endHour, switched.endMinute))
    }

    @Test
    fun switchCategory_keepsDaysTheUserChose_andTheDayPageItCameFrom() {
        // Days changed by hand stay; untouched times still follow.
        val ownDays = EditForm.new(null).withPreset(RepeatPreset.Weekends)
        val a = ownDays.switchCategory(Category.Study, isNew = true, fromDay = null)
        assertEquals(listOf(SATURDAY, SUNDAY), a.days)
        assertEquals(19, a.startHour)
        // Opened from Wednesday's page: stays on Wednesday.
        val b = EditForm.new(WEDNESDAY).switchCategory(Category.Study, isNew = true, fromDay = WEDNESDAY)
        assertEquals(listOf(WEDNESDAY), b.days)
        assertEquals(19, b.startHour)
    }

    @Test
    fun switchCategory_savedSnoozerNeverMovesTimes() {
        val saved = EditForm(9, 0, 17, 0, DayOfWeek.entries.take(5), Category.Work)   // same as Work's defaults
        val switched = saved.switchCategory(Category.Study, isNew = false, fromDay = null)
        assertEquals(saved.copy(category = Category.Study), switched)
    }

    @Test
    fun explanation_followsTheChosenCategory() {
        assertEquals(Category.Work, EditUi.describe(base, base, isNew = false).explanation.category)
        val study = base.copy(category = Category.Study)
        assertEquals(Category.Study, EditUi.describe(study, base, isNew = false).explanation.category)
        assertEquals("During Study", EditUi.describe(study, base, isNew = false).explanation.title)
    }

    @Test
    fun encode_roundTrips() {
        val forms = listOf(
            base,
            EditForm(22, 30, 7, 5, listOf(MONDAY, WEDNESDAY, SUNDAY), Category.Study),
            EditForm.new(null).copy(days = emptyList()),   // no days at all
        )
        forms.forEach { assertEquals(it, EditForm.decode(it.encode())) }
    }

    @Test
    fun decode_rejectsAnythingMalformed() {
        listOf(
            "",                       // empty
            "9:0:10:30:1",            // a part missing
            "9:0:10:30:1:Work:extra", // a part too many
            "24:0:10:30:1:Work",      // hour out of range
            "9:60:10:30:1:Work",      // minute out of range
            "9:0:10:30:8:Work",       // no such day
            "9:0:10:30:x:Work",       // not a number
            "9:0:10:30:1:Play",       // unknown category
        ).forEach { assertNull("should reject \"$it\"", EditForm.decode(it)) }
    }

    @Test
    fun newForm_preselectsTheDayItCameFrom() {
        assertEquals(listOf(WEDNESDAY), EditForm.new(WEDNESDAY).days)
        assertEquals(DayOfWeek.entries.take(5), EditForm.new(null).days)   // the category's usual days
    }

    @Test
    fun panelHeadsUpLine_followsThatCategorysOwnChannel() {
        val on = Permissions(dnd = true, exactAlarms = true, exactAlarmsApplies = true, notifications = true, notificationsApply = true)
        val studyOff = on.copy(reminderChannelsOff = setOf(Category.Study))
        // Only Study's channel is off: Work still promises its heads-up, Study says it's off
        // and points to Android's settings (Setup can't turn a channel back on).
        assertEquals("A quiet heads-up before it starts.", EditUi.explainFor(Category.Work, studyOff).lines.last().text)
        assertEquals("Heads-up is off: turn on Snoozer's notifications in Android settings to get one.",
            EditUi.explainFor(Category.Study, studyOff).lines.last().text)
        // App-wide permission missing on Android 13+: Setup can fix it.
        assertEquals("Heads-up is off: allow notifications in Setup to get one.",
            EditUi.explainFor(Category.Study, on.copy(notifications = false)).lines.last().text)
        // No permissions known (previews): assume on.
        assertEquals("A heads-up 5 minutes before it starts.", EditUi.explainFor(Category.Study, null).lines.last().text)
    }
}
