package dev.railwatch.shared.domain

/** A time-off day before it gets an id. type is "company" (holiday) or "leave". */
data class TimeOffDay(val date: String, val name: String, val type: String)

private val DAY = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun valid(d: TimeOffDay) = DAY.matches(d.date) && d.name.isNotBlank() && d.name.length <= 100 && d.type in setOf("company", "leave")

/** All-day events from an iCalendar file, one day each (DTEND exclusive), like the web's parseHolidayICS. */
fun parseTimeOffIcs(text: String, type: String = "company"): List<TimeOffDay> {
    require(Regex("BEGIN:VCALENDAR", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "This doesn't look like a calendar (.ics) file." }
    val lines = text.replace(Regex("\r\n?"), "\n").replace(Regex("\n[ \t]"), "").split("\n")
    fun day(v: String) = Regex("""^(\d{4})(\d{2})(\d{2})""").find(v)?.destructured?.let { (y, m, d) -> "$y-$m-$d" } ?: ""
    fun unescape(v: String) = v.replace(Regex("""\\n""", RegexOption.IGNORE_CASE), " ").replace(Regex("""\\([,;\\])"""), "$1").trim()
    val out = mutableListOf<TimeOffDay>()
    var event: MutableMap<String, String>? = null
    for (line in lines) {
        if (line.equals("BEGIN:VEVENT", true)) { event = mutableMapOf(); continue }
        if (line.equals("END:VEVENT", true) && event != null) {
            val start = event["DTSTART"].orEmpty()
            val allDay = Regex("VALUE=DATE(?!-)", RegexOption.IGNORE_CASE).containsMatchIn(event["DTSTART_PARAMS"].orEmpty()) || Regex("""^\d{8}$""").matches(start)
            val first = day(start); val name = unescape(event["SUMMARY"].orEmpty()).take(100)
            if (allDay && first.isNotEmpty() && name.isNotEmpty()) {
                val end = day(event["DTEND"].orEmpty()).ifEmpty { addDays(first, 1) }
                var d = first
                while (d < end && out.size < 3000) { TimeOffDay(d, name, type).takeIf(::valid)?.let(out::add); d = addDays(d, 1) }
            }
            event = null; continue
        }
        val e = event ?: continue
        Regex("""^([A-Z-]+)((?:;[^:]*)?):(.*)$""", RegexOption.IGNORE_CASE).find(line)?.destructured?.let { (k, params, v) ->
            val key = k.uppercase(); e[key] = v; if (params.isNotEmpty()) e["${key}_PARAMS"] = params
        }
    }
    require(out.isNotEmpty()) { "No all-day events were found in this calendar." }
    return out
}

/** A strict date,name,type CSV (quoted commas allowed), like the web's parseHolidayCSV. */
fun parseTimeOffCsv(text: String): List<TimeOffDay> {
    val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val field = StringBuilder(); var quoted = false
    val src = text.removePrefix("﻿"); var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '"' -> if (quoted && src.getOrNull(i + 1) == '"') { field.append('"'); i++ } else quoted = !quoted
            !quoted && (c == ',' || c == '\n') -> { row.add(field.toString().trim()); field.clear(); if (c == '\n') { if (row.any { it.isNotEmpty() }) rows.add(row); row = mutableListOf() } }
            c != '\r' || quoted -> field.append(c)
        }
        i++
    }
    require(!quoted) { "An imported CSV field has an unclosed quote." }
    row.add(field.toString().trim()); if (row.any { it.isNotEmpty() }) rows.add(row)
    require(rows.removeFirstOrNull()?.joinToString(",") { it.lowercase() } == "date,name,type") { "CSV columns must be date,name,type in that order." }
    require(rows.size in 1..3000) { "Import between 1 and 3,000 days." }
    return rows.mapIndexed { n, v ->
        val d = TimeOffDay(v.getOrElse(0) { "" }, v.getOrElse(1) { "" }, v.getOrElse(2) { "" }.ifEmpty { "company" })
        require(v.size == 3 && valid(d)) { "Check CSV row ${n + 2}: use YYYY-MM-DD,name,company (or leave)." }
        d
    }
}

/** Adds days off, skipping dates already in the list. Returns the planner and how many were added. */
fun addTimeOff(p: Planner, days: List<TimeOffDay>, newId: () -> String): Pair<Planner, Int> {
    val have = p.holidays.map { it.date }.toMutableSet()
    val added = days.filter { have.add(it.date) }.map { Holiday(newId(), it.name, it.date, it.type) }
    return p.copy(holidays = (p.holidays + added).sortedBy { it.date }) to added.size
}

/** Planned or booked trips on upcoming time off, unless kept (web plan 3.1). */
fun timeOffConflicts(p: Planner, today: String): List<Journey> {
    val off = p.holidays.map { it.date }.filter { it >= today }.toSet()
    return p.journeys.filter { it.date in off && it.timeOffKept != true && (it.status == JourneyStatus.NeedsBooking || it.status == JourneyStatus.Booked) }.sortedBy { it.date }
}

/** Money spent on tickets, by month (YYYY-MM): fare total less any refund. */
data class Spend(val month: String, val amount: Double, val trips: Int)

fun spendByMonth(p: Planner): List<Spend> =
    p.journeys.filter { it.fare != null && it.status != JourneyStatus.Skipped }
        .groupBy { it.date.take(7) }
        .map { (m, js) -> Spend(m, js.sumOf { it.fare!!.total - (it.fare.refund ?: 0.0) }, js.size) }
        .sortedByDescending { it.month }
