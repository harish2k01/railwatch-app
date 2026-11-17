package dev.railwatch.shared.domain

import kotlin.time.Instant

/** What the Today and Journeys screens show, worked out from the planner (shared so iOS gets the same answers). */

/** A Do next card on Today. */
sealed interface DoNext {
    val journey: Journey
    /** Booking has opened and the trip isn't booked yet. */
    data class BookNow(override val journey: Journey) : DoNext
    /** Several trips are open for booking: one card for all of them (the soonest opens the list). */
    data class BookMany(override val journey: Journey, val count: Int) : DoNext
    /** Booking opens within the next week. */
    data class OpensSoon(override val journey: Journey, val opens: String) : DoNext
    /** Marked to cancel: confirm once it's cancelled on IRCTC. */
    data class ConfirmCancel(override val journey: Journey) : DoNext
    /** Booked, but no ticket shared yet. */
    data class NeedsTicket(override val journey: Journey) : DoNext
    /** Two trips overlap in time. */
    data class Clash(override val journey: Journey, val other: Journey) : DoNext
    /** A booked trip with no trip back within 30 days. */
    data class NoReturn(override val journey: Journey) : DoNext
}

private val ACTIVE = setOf(JourneyStatus.NeedsBooking, JourneyStatus.Booked, JourneyStatus.CancellationNeeded)

/** Upcoming trips still in play, soonest first. */
fun upcoming(p: Planner, today: String): List<Journey> =
    p.journeys.filter { it.archivedAt == null && it.date >= today && it.status in ACTIVE }.sortedWith(compareBy({ it.date }, { it.departure }))

/** The next booked trip, else the next planned one. */
fun nextTrip(p: Planner, today: String): Journey? = upcoming(p, today).let { list -> list.firstOrNull { it.status == JourneyStatus.Booked } ?: list.firstOrNull() }

fun hasTicket(j: Journey) = j.attachments.orEmpty().any { it.type == "application/pdf" }

/** Today's Do next cards, most urgent first, at most [limit]. */
fun doNext(p: Planner, today: String, limit: Int = 6, now: Instant = kotlin.time.Clock.System.now()): List<DoNext> {
    val list = upcoming(p, today)
    val cancel = list.filter { it.status == JourneyStatus.CancellationNeeded }.map { DoNext.ConfirmCancel(it) }
    // Open only once the opening minute has passed (08:00, or 10:00 / 11:00 for Tatkal), not from midnight.
    val open = list.filter { it.status == JourneyStatus.NeedsBooking && bookingInstant(it) <= now }
    // With a 60-day window most planned trips are open at once: more than two become one card.
    val book = if (open.size > 2) listOf(DoNext.BookMany(open.first(), open.size)) else open.map { DoNext.BookNow(it) }
    val ticket = list.filter { it.status == JourneyStatus.Booked && !hasTicket(it) }.take(1).map { DoNext.NeedsTicket(it) }
    val soon = list.filter { it.status == JourneyStatus.NeedsBooking && bookingInstant(it) > now && daysBetween(today, bookingDay(it)) <= 7 }
        .sortedBy { bookingDay(it) }.map { DoNext.OpensSoon(it, bookingDay(it)) }
    val warnings = clashes(p, today).map { (a, b) -> DoNext.Clash(a, b) } + missingReturns(p, today).take(1).map { DoNext.NoReturn(it) }
    return (cancel + warnings + book + ticket + soon).take(limit)
}

/** The Journeys lists. */
/** Upcoming: booked trips (and ones to cancel). Planned: not booked yet. Cancelled: cancelled or to cancel. History: past. */
enum class JourneyList { Upcoming, Planned, Cancelled, History }

fun journeyList(p: Planner, today: String, list: JourneyList): List<Journey> = when (list) {
    JourneyList.Upcoming -> upcoming(p, today).filter { it.status == JourneyStatus.Booked || it.status == JourneyStatus.CancellationNeeded }
    JourneyList.Planned -> upcoming(p, today).filter { it.status == JourneyStatus.NeedsBooking }.sortedWith(compareBy<Journey> { bookingDay(it) }.thenBy { it.date })
    JourneyList.Cancelled -> p.journeys.filter { it.archivedAt == null && (it.status == JourneyStatus.Cancelled || it.status == JourneyStatus.CancellationNeeded) }
        .sortedWith(compareByDescending<Journey> { it.date >= today }.thenBy { if (it.date >= today) it.date else "" }.thenByDescending { it.date })
    JourneyList.History -> p.journeys.filter { it.archivedAt == null && it.status != JourneyStatus.Cancelled && (it.date < today || it.status == JourneyStatus.Completed || it.status == JourneyStatus.Skipped) }
        .sortedWith(compareByDescending<Journey> { it.date }.thenByDescending { it.departure })
}

/** Planned trips grouped by when booking opens: open now, this week, later. */
fun plannedGroups(list: List<Journey>, today: String): List<Pair<String, List<Journey>>> {
    val week = addDays(today, 7)
    return listOf(
        "Booking open now" to list.filter { bookingDay(it) <= today },
        "Opens this week" to list.filter { bookingDay(it) in addDays(today, 1)..week },
        "Opens later" to list.filter { bookingDay(it) > week },
    ).filter { it.second.isNotEmpty() }
}

/** Groups a list into months ("2026-11" → trips), keeping order. */
fun byMonth(list: List<Journey>): List<Pair<String, List<Journey>>> =
    list.groupBy { it.date.substring(0, 7) }.toList()

/** What the home-screen widget leads with: the most urgent thing to do, else the next dates. */
sealed interface WidgetFocus {
    /** A booking window opens within the hour (general or Tatkal): a live countdown. */
    data class Opening(val journey: Journey, val at: Instant) : WidgetFocus
    /** A booked trip departs today. */
    data class Travelling(val journey: Journey) : WidgetFocus
    /** Windows are open (the opening minute has passed) on trips not booked yet. */
    data class Book(val journey: Journey, val count: Int) : WidgetFocus
    /** A window opens later today or tomorrow. */
    data class OpensSoon(val journey: Journey, val at: Instant) : WidgetFocus
    /** Nothing pressing. */
    data class AllSet(val nextTrip: Journey?, val nextBookingDay: String?) : WidgetFocus
}

fun widgetFocus(p: Planner, today: String, now: Instant): WidgetFocus {
    val list = upcoming(p, today)
    val waiting = list.filter { it.status == JourneyStatus.NeedsBooking }.map { it to bookingInstant(it) }
    val ahead = waiting.filter { it.second > now }.minByOrNull { it.second }
    ahead?.takeIf { it.second - now <= kotlin.time.Duration.parse("1h") }?.let { return WidgetFocus.Opening(it.first, it.second) }
    list.firstOrNull { it.status == JourneyStatus.Booked && it.date == today }?.let { return WidgetFocus.Travelling(it) }
    val open = waiting.filter { it.second <= now }.map { it.first }
    if (open.isNotEmpty()) return WidgetFocus.Book(open.first(), open.size)
    ahead?.takeIf { it.second - now <= kotlin.time.Duration.parse("36h") }?.let { return WidgetFocus.OpensSoon(it.first, it.second) }
    return WidgetFocus.AllSet(nextTrip(p, today), ahead?.let { bookingDay(it.first) })
}

/** When the widget's focus can next change on its own: an hour before, and at, each upcoming window opening. */
fun widgetNextChange(p: Planner, today: String, now: Instant): Instant? =
    upcoming(p, today).filter { it.status == JourneyStatus.NeedsBooking }.map { bookingInstant(it) }
        .flatMap { listOf(it - kotlin.time.Duration.parse("36h"), it - kotlin.time.Duration.parse("1h"), it) }.filter { it > now }.minOrNull()
/** When a trip occupies: departure to arrival (or one hour if the arrival isn't known), in IST. */
private fun span(j: Journey): Pair<Instant, Instant>? = runCatching {
    val start = Instant.parse("${j.date}T${j.departure}:00+05:30")
    // Arrival on its own date, or the next day when it's earlier than departure (overnight trains).
    val end = j.arrival?.let { a -> val day = j.arrivalDate ?: if (a < j.departure) addDays(j.date, 1) else j.date; Instant.parse("${day}T$a:00+05:30") }
        ?.takeIf { it > start } ?: (start + kotlin.time.Duration.parse("1h"))
    start to end
}.getOrNull()

/** Pairs of upcoming booked or planned trips whose travel times overlap, soonest first. */
fun clashes(p: Planner, today: String): List<Pair<Journey, Journey>> {
    val trips = upcoming(p, today).filter { it.status == JourneyStatus.Booked || it.status == JourneyStatus.NeedsBooking }.mapNotNull { j -> span(j)?.let { j to it } }
    val out = mutableListOf<Pair<Journey, Journey>>()
    for (i in trips.indices) for (k in i + 1 until trips.size) {
        val (a, sa) = trips[i]; val (b, sb) = trips[k]
        if (sa.first < sb.second && sb.first < sa.second) out += a to b
    }
    return out
}

/** Booked trips in the next 60 days with no trip back (B to A) within 30 days after, unless marked one-way. */
fun missingReturns(p: Planner, today: String): List<Journey> {
    val trips = upcoming(p, today)
    val horizon = addDays(today, 60)
    return trips.filter { it.status == JourneyStatus.Booked && it.oneWay != true && it.date <= horizon }.filter { j ->
        trips.none { r -> r.id != j.id && r.from.equals(j.to, true) && r.to.equals(j.from, true) && r.date >= j.date && r.date <= addDays(j.date, 30) }
    }
}

/** Search across a trip: stations (codes or names), train, PNR, coach, notes and the travel date ("12 jan", "jan", "2027-01-12"). */
fun matchesQuery(j: Journey, query: String, stationName: (String) -> String? = { null }): Boolean {
    val q = query.trim().lowercase(); if (q.isEmpty()) return true
    val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val (y, m, d) = j.date.split("-").let { Triple(it[0], it[1].toInt(), it[2].toInt()) }
    val month = months[m - 1]
    val hay = listOfNotNull(j.from, j.to, stationName(j.from), stationName(j.to), j.train, j.trainNumber, j.trainName, j.pnr, j.coach, j.seat, j.travelClass, j.notes,
        j.date, "$d $month", "$d $month $y", month, "$month $y").joinToString(" | ").lowercase()
    // A month typed in full ("january") or in part ("janu") matches that month.
    return q.split(Regex("\\s+")).all { word -> hay.contains(word) || (word.length >= 3 && word.take(3) in months && word.take(3) == month) }
}