package com.example.snoozer.timetable

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One weekly class found in a calendar file, in the phone's time zone. */
data class ParsedClass(
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek>,
    /** The first class's date. */
    val from: LocalDate,
    /** The last class's date, or null when the file says it repeats with no end. */
    val until: LocalDate?,
)

/** Why an event in the file didn't become a class. */
enum class SkipReason(val label: String) {
    AllDay("all-day"),
    NotWeekly("not weekly"),
    OneOff("one-off"),
    TooLong("a day or longer"),
    Unreadable("unreadable"),
}

data class ParseResult(val classes: List<ParsedClass>, val skipped: Map<SkipReason, Int>)

/**
 * Reads the part of iCalendar (RFC 5545) that school timetables use: timed VEVENTs, either
 * repeating weekly (RRULE FREQ=WEEKLY with BYDAY, ending by UNTIL or COUNT) or listed one by one.
 * Times with a TZID, in UTC, or floating are all moved to [zone], the phone's time zone.
 *
 * Kept deliberately small: no library, no network, nothing leaves the phone. Anything outside
 * that subset is counted in [ParseResult.skipped] with a reason, never guessed at.
 */
object IcsParser {

    /** Past this, a file isn't a timetable; the caller refuses to read further. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** How far ahead a repeat with no end is followed, just to find its weekdays. */
    private const val OPEN_ENDED_DAYS = 14L

    /** A cap on generated occurrences, so a broken rule can't spin forever. */
    private const val MAX_OCCURRENCES = 2000

    fun looksLikeCalendar(text: String): Boolean = text.contains("BEGIN:VCALENDAR", ignoreCase = true)

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): ParseResult {
        val skipped = mutableMapOf<SkipReason, Int>()
        fun skip(reason: SkipReason) { skipped[reason] = (skipped[reason] ?: 0) + 1 }

        val classes = mutableListOf<ParsedClass>()
        val singles = mutableListOf<Single>()
        for (event in events(unfold(text))) {
            when (val r = read(event, zone)) {
                is Read.Weekly -> classes += r.cls
                is Read.Once -> singles += r.single
                is Read.Skip -> skip(r.reason)
                Read.Ignore -> Unit
            }
        }
        // Classes listed one by one: the same name, weekday and times on two or more dates are a
        // weekly class from the first date to the last. A lone event is a one-off (an exam).
        singles.groupBy { listOf(it.name, it.start.dayOfWeek, it.start.toLocalTime(), it.end.toLocalTime()) }
            .values.forEach { group ->
                if (group.size < 2) {
                    skip(SkipReason.OneOff)
                } else {
                    val first = group.minBy { it.start }
                    classes += ParsedClass(
                        name = first.name,
                        start = first.start.toLocalTime(),
                        end = first.end.toLocalTime(),
                        days = setOf(first.start.dayOfWeek),
                        from = first.start.toLocalDate(),
                        until = group.maxOf { it.start.toLocalDate() },
                    )
                }
            }
        return ParseResult(classes, skipped)
    }

    // ---- Lines and components ----------------------------------------------------------------

    /** Joins folded lines (a line starting with a space or tab continues the one before). */
    internal fun unfold(text: String): List<String> {
        val out = mutableListOf<StringBuilder>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) out.last().append(line, 1, line.length)
            else out += StringBuilder(line)
        }
        return out.map { it.toString() }.filter { it.isNotBlank() }
    }

    /** A content line: NAME;PARAM=VALUE:value. */
    internal data class Property(val name: String, val params: Map<String, String>, val value: String)

    internal fun property(line: String): Property? {
        // The value starts at the first ':' outside a quoted parameter value.
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ':' -> if (!quoted) { colon = i; break }
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"')
        }.toMap()
        return Property(head[0].uppercase(), params, line.substring(colon + 1))
    }

    /** Each VEVENT's own properties (an alarm or other component nested inside is left out). */
    private fun events(lines: List<String>): List<Map<String, Property>> {
        val events = mutableListOf<Map<String, Property>>()
        var current: MutableMap<String, Property>? = null
        var nested = 0
        for (line in lines) {
            val p = property(line) ?: continue
            when {
                p.name == "BEGIN" && p.value.equals("VEVENT", true) -> { current = mutableMapOf(); nested = 0 }
                p.name == "END" && p.value.equals("VEVENT", true) -> { current?.let(events::add); current = null }
                current == null -> Unit
                p.name == "BEGIN" -> nested++
                p.name == "END" -> nested--
                nested == 0 -> current.putIfAbsent(p.name, p) // first one wins
            }
        }
        return events
    }

    // ---- One event ----------------------------------------------------------------------------

    private data class Single(val name: String, val start: LocalDateTime, val end: LocalDateTime)

    private sealed interface Read {
        data class Weekly(val cls: ParsedClass) : Read
        data class Once(val single: Single) : Read
        data class Skip(val reason: SkipReason) : Read
        /** Not a class and not worth reporting: a cancelled event, or a changed single occurrence. */
        data object Ignore : Read
    }

    private fun read(event: Map<String, Property>, zone: ZoneId): Read {
        if (event["STATUS"]?.value.equals("CANCELLED", true)) return Read.Ignore
        // A changed single occurrence of a repeating event; the repeat itself is read instead.
        if (event.containsKey("RECURRENCE-ID")) return Read.Ignore
        val dtStart = event["DTSTART"] ?: return Read.Skip(SkipReason.Unreadable)
        if (isDateOnly(dtStart)) return Read.Skip(SkipReason.AllDay)
        val start = dateTime(dtStart, zone) ?: return Read.Skip(SkipReason.Unreadable)
        val end = event["DTEND"]?.let { dateTime(it, zone) }
            ?: event["DURATION"]?.let { runCatching { start.plus(Duration.parse(it.value.trim())) }.getOrNull() }
            ?: return Read.Skip(SkipReason.Unreadable)
        val length = Duration.between(start, end)
        if (length.isNegative || length.isZero) return Read.Skip(SkipReason.Unreadable)
        if (length >= Duration.ofDays(1)) return Read.Skip(SkipReason.TooLong)
        val name = event["SUMMARY"]?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() } ?: "Class"

        val rule = event["RRULE"]?.value ?: return Read.Once(Single(name, local(start, zone), local(end, zone)))
        return weekly(name, start, length, rule, zone)
    }

    private fun weekly(name: String, start: ZonedDateTime, length: Duration, ruleText: String, zone: ZoneId): Read {
        val rule = ruleText.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1)
        }.toMap()
        if (!rule["FREQ"].equals("WEEKLY", true)) return Read.Skip(SkipReason.NotWeekly)
        if ((rule["INTERVAL"]?.toIntOrNull() ?: 1) != 1) return Read.Skip(SkipReason.NotWeekly)
        val byDay = rule["BYDAY"]?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }
        // "1MO" (the first Monday) belongs to monthly rules; weekly ones list plain days.
        val days = if (byDay.isNullOrEmpty()) setOf(start.dayOfWeek)
            else byDay.map { DAY_CODES[it] ?: return Read.Skip(SkipReason.NotWeekly) }.toSet()
        val count = rule["COUNT"]?.toIntOrNull()
        val until = rule["UNTIL"]?.let { untilInstant(it, start.zone) ?: return Read.Skip(SkipReason.Unreadable) }

        // Walk the dates in the event's own time zone, as the file means them.
        val occurrences = mutableListOf<ZonedDateTime>()
        val time = start.toLocalTime()
        var date = start.toLocalDate()
        val openEnded = count == null && until == null
        val lastDay = if (openEnded) date.plusDays(OPEN_ENDED_DAYS) else null
        while (occurrences.size < MAX_OCCURRENCES) {
            if (lastDay != null && date.isAfter(lastDay)) break
            if (date.dayOfWeek in days) {
                val occurrence = date.atTime(time).atZone(start.zone)
                if (until != null && occurrence.isAfter(until)) break
                occurrences += occurrence
                if (count != null && occurrences.size >= count) break
            }
            date = date.plusDays(1)
        }
        if (occurrences.isEmpty()) return Read.Skip(SkipReason.Unreadable)
        // On the phone, in its own time zone: a class at 23:30 elsewhere can land on another day.
        val local = occurrences.map { local(it, zone) }
        val first = local.first()
        return Read.Weekly(
            ParsedClass(
                name = name,
                start = first.toLocalTime(),
                end = first.plus(length).toLocalTime(),
                days = local.map { it.dayOfWeek }.toSet(),
                from = first.toLocalDate(),
                until = if (openEnded) null else local.last().toLocalDate(),
            )
        )
    }

    // ---- Values -------------------------------------------------------------------------------

    private val DAY_CODES = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun isDateOnly(p: Property): Boolean =
        p.params["VALUE"].equals("DATE", true) || p.value.trim().length == 8

    /** A DTSTART/DTEND value as a moment: UTC with "Z", in its TZID, or floating (the phone's zone). */
    private fun dateTime(p: Property, zone: ZoneId): ZonedDateTime? {
        val v = p.value.trim()
        return try {
            if (v.endsWith("Z", ignoreCase = true)) {
                LocalDateTime.parse(v.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC)
            } else {
                LocalDateTime.parse(v, DATE_TIME).atZone(p.params["TZID"]?.let(::zoneOf) ?: zone)
            }
        } catch (e: DateTimeException) {
            null
        }
    }

    /** UNTIL is UTC ("...Z"), a date (the whole day counts), or floating in the event's zone. */
    private fun untilInstant(v: String, eventZone: ZoneId): ZonedDateTime? = try {
        val t = v.trim()
        when {
            t.length == 8 -> LocalDate.parse(t, DATE).atTime(LocalTime.MAX).atZone(eventZone)
            t.endsWith("Z", true) -> LocalDateTime.parse(t.dropLast(1), DATE_TIME).atZone(ZoneOffset.UTC)
            else -> LocalDateTime.parse(t, DATE_TIME).atZone(eventZone)
        }
    } catch (e: DateTimeException) {
        null
    }

    /**
     * An Olson id ("America/Toronto"), or null for one Java doesn't know (some exporters write
     * Windows names like "Eastern Standard Time"); the caller then reads it as the phone's zone.
     */
    private fun zoneOf(id: String): ZoneId? = try {
        ZoneId.of(id.trim().removePrefix("/"))
    } catch (e: DateTimeException) {
        null
    }

    private fun local(t: ZonedDateTime, zone: ZoneId): LocalDateTime = t.withZoneSameInstant(zone).toLocalDateTime()

    /** Text values escape \\ \; \, and \n. */
    internal fun unescape(v: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < v.length) {
            val c = v[i]
            if (c == '\\' && i + 1 < v.length) {
                when (val n = v[i + 1]) {
                    'n', 'N' -> out.append(' ')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
