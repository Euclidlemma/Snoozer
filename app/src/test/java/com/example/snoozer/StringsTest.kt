package com.example.snoozer

import com.example.snoozer.alarm.AlarmTime
import com.example.snoozer.alarm.Category
import com.example.snoozer.alarm.QuietLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class StringsTest {

    private val en = ResourceStrings(Locale.ENGLISH)
    private val ko = ResourceStrings(Locale.KOREAN)

    @Test
    fun english_matchesTheWordsBeforeTranslation() {
        assertEquals(listOf("Work", "Study"), Category.entries.map { en.get(it.labelRes) })
        assertEquals(listOf("Vibrate", "Silent"), QuietLevel.entries.map { en.get(it.labelRes) })
        assertEquals(listOf("Phone on vibrate", "Phone fully silent"), QuietLevel.entries.map { en.get(it.descriptionRes) })
        // Every day of a year, the way dates read before: "4 Dec".
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            assertEquals("${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}", AlarmTime.formatDate(d, en))
            d = d.plusDays(1)
        }
        (0..1500 step 5).forEach { m ->
            val old = when {
                m / 60 == 0 -> "${m % 60}m"
                m % 60 == 0 -> "${m / 60}h"
                else -> "${m / 60}h ${m % 60}m"
            }
            assertEquals(old, AlarmTime.formatDuration(m, en))
        }
        assertEquals("0m", AlarmTime.formatDuration(-5, en))
        assertEquals("Wednesday 18 November", AlarmTime.formatLongDate(LocalDate.of(2026, 11, 18), en))
        assertEquals("December 2026", AlarmTime.formatMonth(java.time.YearMonth.of(2026, 12), en))
    }

    @Test
    fun korean_usesTheAgreedWords() {
        assertEquals("일", ko.get(Category.Work.labelRes))
        assertEquals("공부", ko.get(Category.Study.labelRes))
        assertEquals("진동", ko.get(QuietLevel.Vibrate.labelRes))
        assertEquals("무음", ko.get(QuietLevel.Silent.labelRes))
        assertEquals("Snoozer", ko.get(R.string.app_name))
    }

    @Test
    fun korean_datesDurationsAndDays() {
        assertEquals("12월 4일", AlarmTime.formatDate(LocalDate.of(2026, 12, 4), ko))
        assertEquals("1월 15일", AlarmTime.formatDate(LocalDate.of(2027, 1, 15), ko))
        assertEquals("45분", AlarmTime.formatDuration(45, ko))
        assertEquals("2시간", AlarmTime.formatDuration(120, ko))
        assertEquals("1시간 30분", AlarmTime.formatDuration(90, ko))
        assertEquals("목", AlarmTime.dayName(DayOfWeek.THURSDAY, TextStyle.SHORT, ko))
        assertEquals("목요일", AlarmTime.dayName(DayOfWeek.THURSDAY, TextStyle.FULL, ko))
        assertEquals("11월 18일 수요일", AlarmTime.formatLongDate(LocalDate.of(2026, 11, 18), ko))
        assertEquals("2026년 12월", AlarmTime.formatMonth(java.time.YearMonth.of(2026, 12), ko))
        assertEquals("Thu", AlarmTime.dayName(DayOfWeek.THURSDAY, TextStyle.SHORT, en))
    }

    @Test
    fun untranslatedPhoneLanguage_fallsBackToEnglish_datesToo() {
        val ja = ResourceStrings(Locale.JAPANESE)
        assertEquals(Locale.ENGLISH.language, ja.locale.language)
        assertEquals("Work", ja.get(Category.Work.labelRes))
        assertEquals("Thu", AlarmTime.dayName(DayOfWeek.THURSDAY, TextStyle.SHORT, ja))
        assertEquals("4 Dec", AlarmTime.formatDate(LocalDate.of(2026, 12, 4), ja))
    }

    /** Lint also checks this; the test says which word is missing without a lint run. */
    @Test
    fun everyWord_isInBothLanguages() {
        assertEquals(names("values"), names("values-ko"))
    }

    @Test
    fun unescape_followsAndroidsRules() {
        assertEquals("It's on", ResourceStrings.unescape("It\\'s on"))
        assertEquals("a b", ResourceStrings.unescape("  a \n   b  "))
        assertEquals("  kept  ", ResourceStrings.unescape("\"  kept  \""))
        assertEquals("line\nnext", ResourceStrings.unescape("line\\nnext"))
        assertEquals("@home", ResourceStrings.unescape("\\@home"))
        assertEquals("é", ResourceStrings.unescape("\\u00e9"))
    }

    private fun names(folder: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml"))
        val nodes = doc.documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? org.w3c.dom.Element }
            .filter { it.getAttribute("translatable") != "false" }.map { it.getAttribute("name") }.toSet()
    }
}
