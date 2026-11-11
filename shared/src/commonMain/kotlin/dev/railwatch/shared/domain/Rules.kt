package dev.railwatch.shared.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/*
 * The planning rules, ported from the web app's src/lib/travel-planner.ts. fixtures/rules.json (exported from the web
 * code) is checked against these in the tests, so a change to either side shows up as a failing case.
 */

/** Calendar helpers on "YYYY-MM-DD" strings. */
fun addDays(day: String, count: Int): String = LocalDate.parse(day).plus(DatePeriod(days = count)).toString()
fun daysBetween(a: String, b: String): Int = LocalDate.parse(a).daysUntil(LocalDate.parse(b))
/** 0 = Sunday … 6 = Saturday, as in JavaScript. */
fun weekday(day: String): Int = LocalDate.parse(day).dayOfWeek.ordinal.let { (it + 1) % 7 }
private fun dayOfMonth(day: String) = LocalDate.parse(day).dayOfMonth
private fun monthNumber(day: String) = LocalDate.parse(day).monthNumber
private fun year(day: String) = LocalDate.parse(day).year

/** Quota-specific booking opening time in IST: 08:00 normal, Tatkal 10:00 (AC) or 11:00 (non-AC). */
fun bookingClock(j: Journey): String = if (j.bookingType == "tatkal") (if (j.tatkalClass == "non_ac") "11:00" else "10:00") else "08:00"
/** The day booking opens: Tatkal the day before the train starts, otherwise the advance window before travel. */
fun bookingDay(j: Journey): String = if (j.bookingType == "tatkal") addDays(j.trainOriginDate ?: j.date, -1) else addDays(j.date, -j.windowDays)
/** The instant booking opens (IST). */
fun bookingInstant(j: Journey): Instant = Instant.parse("${bookingDay(j)}T${bookingClock(j)}:00+05:30")

/** Expands a routine into its journeys up to the planning horizon (months ahead or a journey count). */
fun generateJourneys(rule: Rule, today: String, settings: Settings = Settings()): List<Journey> {
    val result = mutableListOf<Journey>()
    if (rule.paused) return result
    val anchor = addDays(rule.start, -((weekday(rule.start) + 6) % 7))
    val start = if (rule.start > today) rule.start else today
    // Same date one month-step ahead, clamped to the month's last day (kotlinx-datetime clamps like the web code).
    val boundary = LocalDate.parse(today).plus(DatePeriod(months = settings.routineMonthsAhead)).toString()
    val r = rule.recurrence
    val stepDays = when (r?.frequency) {
        "yearly" -> 366 * r.interval
        "monthly" -> 31 * r.interval
        "daily" -> r.interval
        else -> 7 * (r?.interval ?: rule.intervalWeeks)
    }
    val countSpan = settings.routineTicketCount * stepDays + 366
    val horizon = if (settings.routineHorizonMode == "count") addDays(start, minOf(countSpan, daysBetween(start, "9999-12-31"))) else boundary
    val end = if (rule.end != null && rule.end < horizon) rule.end else horizon
    var date = start
    while (date <= end) {
        val week = Math.floorDiv(daysBetween(anchor, date), 7)
        var matches = weekday(date) in rule.weekdays && week % rule.intervalWeeks == 0
        if (r != null) {
            val months = (year(date) - year(rule.start)) * 12 + monthNumber(date) - monthNumber(rule.start)
            matches = when (r.frequency) {
                "daily" -> daysBetween(rule.start, date) % r.interval == 0
                "weekly" -> weekday(date) in rule.weekdays && week % r.interval == 0
                "monthly" -> months % r.interval == 0 && (if (r.monthlyPattern == "date") dayOfMonth(date) == r.dayOfMonth
                    else weekday(date) == r.weekday && (if (r.ordinal == -1) monthNumber(addDays(date, 7)) != monthNumber(date) else (dayOfMonth(date) + 6) / 7 == r.ordinal))
                "yearly" -> (year(date) - year(rule.start)) % r.interval == 0 && date.substring(5) == rule.start.substring(5)
                else -> matches
            }
        }
        if (matches && date !in rule.excludedDates) {
            val base = Journey(id = "", from = rule.from, to = rule.to, date = date, departure = rule.departure, train = "", trainName = "", trainNumber = "", travelClass = "",
                windowDays = rule.windowDays, originOffset = 0, timePreference = rule.timePreference ?: "any", status = JourneyStatus.NeedsBooking, ruleId = rule.id)
            result += base.copy(id = "${rule.id}:$date:outbound", leg = "outbound")
            if (rule.returnAfterDays != null) {
                result += base.copy(id = "${rule.id}:$date:return", date = addDays(date, rule.returnAfterDays), from = rule.to, to = rule.from,
                    departure = rule.returnDeparture, train = rule.returnTrain, originOffset = rule.returnOriginOffset, leg = "return")
            }
            if (settings.routineHorizonMode == "count" && result.size >= settings.routineTicketCount) break
        }
        date = addDays(date, 1)
    }
    return if (settings.routineHorizonMode == "count") result.take(settings.routineTicketCount) else result
}

/** Journeys a routine change or removal must keep: anything acted on, edited, archived or in the past. */
private fun protectedOccurrence(j: Journey, today: String) =
    j.status != JourneyStatus.NeedsBooking || j.manualOverride == true || j.archivedAt != null || j.date < today

/** Saves a routine and regenerates its future plans, keeping booked tickets and individually changed journeys. */
fun saveRule(planner: Planner, rule: Rule, today: String): Planner {
    val generated = generateJourneys(rule.copy(windowDays = planner.settings.bookingWindowDays), today, planner.settings)
    val existing = planner.journeys.associateBy { it.id }
    val preserved = planner.journeys.filter { it.ruleId != rule.id || protectedOccurrence(it, today) }
    val preservedIds = preserved.map { it.id }.toSet()
    val additions = generated.filter { it.id !in preservedIds }.map { it.copy(notes = existing[it.id]?.notes ?: "") }
    return planner.copy(rules = planner.rules.filter { it.id != rule.id } + rule, journeys = preserved + additions)
}

/** Removes a routine and its untouched future plans, keeping tickets, exceptions and history. */
fun removeRule(planner: Planner, id: String, today: String): Planner = planner.copy(
    rules = planner.rules.filter { it.id != id }.map { if (it.linkedRuleId == id) it.copy(linkedRuleId = null) else it },
    journeys = planner.journeys.filter { it.ruleId != id || protectedOccurrence(it, today) },
)

/** Tops every active routine up to the planning horizon. */
fun extendRoutines(planner: Planner, today: String): Planner =
    planner.rules.filter { !it.paused }.fold(planner) { p, rule -> saveRule(p, rule, today) }

const val CANCELLED_RETENTION_DAYS = 7
private val STATION_SUFFIX = Regex("""\s[-–]\s*([A-Z]{2,5})\s*$""")

/** Completes past booked journeys, dates cancellations and archives them after the retention period. */
fun reconcileLifecycle(planner: Planner, today: String): Planner {
    val keep = planner.settings.cancelledArchiveDays ?: CANCELLED_RETENTION_DAYS
    val journeys = planner.journeys.map { raw ->
        // Older ticket imports saved stations as "MADURAI JN. - MDU": keep the code.
        fun code(v: String) = STATION_SUFFIX.find(v)?.groupValues?.get(1) ?: v
        var j = raw
        if (code(j.from) != j.from || code(j.to) != j.to) j = j.copy(from = code(j.from), to = code(j.to))
        when {
            j.archivedAt != null -> j
            j.status == JourneyStatus.Booked && j.date < today -> j.copy(status = JourneyStatus.Completed, completedAt = addDays(j.date, 1))
            j.status == JourneyStatus.Cancelled || j.status == JourneyStatus.Skipped -> {
                val cancelledAt = j.cancelledAt?.takeIf { it <= today } ?: today
                j.copy(cancelledAt = cancelledAt, archivedAt = if (daysBetween(cancelledAt, today) >= keep) today else null)
            }
            j.cancelledAt != null -> j.copy(cancelledAt = null)
            else -> j
        }
    }
    return if (journeys == planner.journeys) planner else planner.copy(journeys = journeys)
}

/** Moves a journey to a new status, recording when it was completed or cancelled. */
fun transition(j: Journey, status: JourneyStatus, today: String): Journey = j.copy(
    status = status,
    completedAt = if (status == JourneyStatus.Completed) j.completedAt ?: today else null,
    cancelledAt = if (status == JourneyStatus.Cancelled || status == JourneyStatus.Skipped) (if (j.status == status) j.cancelledAt else today) else null,
    manualOverride = if (j.ruleId != null) true else j.manualOverride,
)

/** Departure and arrival as dates and times (no "+1"): screens show the date above each time. */
data class TripTimes(val departDate: String, val depart: String?, val arriveDate: String?, val arrive: String?, val span: String?)

fun tripTimes(j: Journey): TripTimes {
    val dep = j.departure.takeIf { j.departureConfirmed == true && it.isNotEmpty() }
    val arr = j.arrival
    if (dep == null || arr == null) return TripTimes(j.date, dep, null, null, null)
    fun min(t: String) = t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    val days = if (j.arrivalDate != null) daysBetween(j.date, j.arrivalDate) else if (min(arr) < min(dep)) 1 else 0
    val minutes = days * 1440 + min(arr) - min(dep)
    val span = if (days < 0 || days > 3 || minutes <= 0) null else "${minutes / 60}h" + if (minutes % 60 != 0) " ${minutes % 60}m" else ""
    return TripTimes(j.date, dep, addDays(j.date, days.coerceIn(0, 3)), arr, span)
}

/** "23:10 → 07:10 +1 · 8h" from a ticket's departure and arrival; null until both are known. */
fun tripTiming(j: Journey): String? {
    val dep = j.departure.takeIf { j.departureConfirmed == true && it.isNotEmpty() } ?: return null
    val arr = j.arrival ?: return null
    fun min(t: String) = t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    val days = if (j.arrivalDate != null) daysBetween(j.date, j.arrivalDate) else if (min(arr) < min(dep)) 1 else 0
    val minutes = days * 1440 + min(arr) - min(dep)
    if (days < 0 || days > 3 || minutes <= 0) return "$dep → $arr"
    val span = "${minutes / 60}h" + if (minutes % 60 != 0) " ${minutes % 60}m" else ""
    return "$dep → $arr" + (if (days != 0) " +$days" else "") + " · $span"
}
