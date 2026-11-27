package com.example.snoozer

import com.example.snoozer.EditAlarm.EditForm
import com.example.snoozer.EditAlarm.EditUi
import com.example.snoozer.alarm.Alarm
import com.example.snoozer.dayPickers.MonthUi
import com.example.snoozer.dayPickers.StatusUi
import com.example.snoozer.dayPickers.WeekUi
import com.example.snoozer.home.HomeUi
import com.example.snoozer.report.WeeklyReport
import com.example.snoozer.util.Permissions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** The main sentences read in Korean word order, built by the same code as the English ones. */
class KoreanTextTest {

    // Wednesday 18 November 2026, 11:00.
    private val now = LocalDateTime.of(2026, 11, 18, 11, 0)
    private val work = Alarm(id = 1, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0,
        daysOfWeek = DayOfWeek.entries.take(5), work = "Work")
    private val study = Alarm(id = 2, startHour = 19, startMinute = 0, endHour = 21, endMinute = 0,
        daysOfWeek = listOf(MONDAY, WEDNESDAY), work = "Study", name = "MATH 101",
        fromDate = LocalDate.of(2026, 9, 9), untilDate = LocalDate.of(2026, 12, 9))

    @Test
    fun weekScreen() {
        val status = WeekUi.status(KO, listOf(work, study), now) as StatusUi.QuietNow
        assertEquals("17:00", status.until)
        assertEquals("6시간 남음", status.left)
        val next = WeekUi.status(KO, listOf(study), now) as StatusUi.Next
        assertEquals("오늘", next.day)
        assertEquals("8시간 후", next.startsIn)

        val week = WeekUi.build(KO, listOf(work, study), now)
        val wed = week.days[2]
        assertEquals("수요일", wed.name)
        assertEquals("수", wed.letter)
        assertEquals("스누저 2개 · 10시간", wed.summary)
        assertEquals("평일", wed.cards.first().repeatText)
        assertEquals("12월 9일까지", wed.cards.last().dateNote)
        assertEquals("이번 주 · 일 40시간 · 공부 4시간", week.weeklyTotals)
    }

    @Test
    fun homeLine() {
        val all = Permissions(dnd = true, exactAlarms = true, exactAlarmsApplies = true)
        assertEquals("다음 스누저: 오늘 19:00", HomeUi.build(KO, listOf(study), now, all).statusLine)
        assertEquals("지금 조용해요, 17:00까지", HomeUi.build(KO, listOf(work), now, all).statusLine)
    }

    @Test
    fun monthCalendar() {
        val m = MonthUi.build(KO, listOf(work, study), now, YearMonth.of(2026, 12), LocalDate.of(2026, 12, 9))
        assertEquals("2026년 12월", m.title)
        assertEquals(listOf("월", "화", "수", "목", "금", "토", "일"), m.dayLetters)
        assertEquals("12월 9일 수요일", m.selected.title)
        assertEquals(listOf("MATH 101 마지막 수업"), m.selected.notes)
        val cell = m.weeks.flatten().first { it.date == LocalDate.of(2026, 12, 9) }
        assertEquals("12월 9일 수요일: 일과 공부, MATH 101 마지막 날", cell.description)
    }

    @Test
    fun editScreen() {
        val f = EditForm.from(work)
        assertEquals("8시간 동안 조용해요", EditUi.describe(KO, f, f, isNew = false).summary)
        val night = f.withStart(22, 0).withEnd(7, 0)
        assertEquals("9시간 동안 조용해요, 다음 날 07:00까지", EditUi.describe(KO, night, f, isNew = false).summary)
        assertEquals("요일을 하나 이상 고르세요.", EditUi.describe(KO, f.copy(days = emptyList()), f, isNew = false).error)
        assertEquals("9월 9일 – 12월 9일", EditUi.describe(KO, EditForm.from(study), EditForm.from(study), isNew = false).dates)
    }

    @Test
    fun reportDates() {
        assertEquals("11월 16일 (월) – 11월 22일 (일)", WeeklyReport.range(KO, LocalDate.of(2026, 11, 16)))
        assertEquals("Mon 16 – Sun 22 Nov", WeeklyReport.range(EN, LocalDate.of(2026, 11, 16)))
        assertEquals("Mon 26 Oct – Sun 1 Nov", WeeklyReport.range(EN, LocalDate.of(2026, 10, 26)))
        assertEquals("다음 리포트는 월요일 00:00 · 5일 후", WeeklyReport.nextReport(KO, now))
    }
}
