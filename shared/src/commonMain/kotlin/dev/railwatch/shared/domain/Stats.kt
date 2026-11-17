package dev.railwatch.shared.domain

import kotlinx.datetime.LocalDate

/** A run of successes: the one going on now, and the longest ever. */
data class Streak(val current: Int, val best: Int)

data class YearStats(
    val year: Int,
    val trips: Int,
    val spent: Double,
    val minutes: Int,
    val stations: Int,
    val route: Pair<String, String>?,
    val routeTrips: Int,
    val routeTrain: String?,
    val routeClass: String?,
    val byMonth: List<Int>,
    val tatkal: Streak,
    val tatkalTries: Int,
    val tatkalBooked: Int,
    val windows: Streak,
    val weeks: Int,
    val confirmedFirst: Int,
    val withStatus: Int,
    val waitlistCleared: Int,
    val waitlisted: Int,
    val longest: Journey?,
    val longestMinutes: Int,
    val earliest: String?,
    val berth: Pair<String, Int>?,
)

private val travelled = setOf(JourneyStatus.Booked, JourneyStatus.Completed)
private val gotTicket = setOf(JourneyStatus.Booked, JourneyStatus.Completed, JourneyStatus.CancellationNeeded, JourneyStatus.Cancelled)

/** Minutes on the train from the ticket's departure and arrival, or null when the arrival isn't known. */
fun rideMinutes(j: Journey): Int? {
    val arr = j.arrival ?: return null
    fun m(t: String) = t.split(":").let { it[0].trim().toInt() * 60 + it[1].trim().toInt() }
    return runCatching {
        val days = j.arrivalDate?.let { daysBetween(j.date, it) } ?: if (m(arr) < m(j.departure)) 1 else 0
        (days * 1440 + m(arr) - m(j.departure)).takeIf { it in 1..(4 * 1440) }
    }.getOrNull()
}

/**
 * Whether booking worked out for a trip, for streaks: true when a ticket was got (even if later cancelled), false
 * when the trip passed still unbooked, null when it doesn't count (skipped, or still to come).
 */
private fun booked(j: Journey, today: String): Boolean? = when {
    j.status in gotTicket && (j.status != JourneyStatus.Cancelled || j.pnr.isNotEmpty()) -> true
    j.status == JourneyStatus.NeedsBooking && j.date < today -> false
    else -> null
}

private fun streak(results: List<Boolean>): Streak {
    var cur = 0; var best = 0
    results.forEach { if (it) { cur++; best = maxOf(best, cur) } else cur = 0 }
    return Streak(cur, best)
}

private fun weekStart(day: String): String = addDays(day, -LocalDate.parse(day).dayOfWeek.ordinal)

private fun <T> List<T>.mostCommon(): T? = groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** Everything on the Stats › Year screen, from data the app already has. Streaks run across years, up to today. */
fun yearStats(p: Planner, year: Int, today: String): YearStats {
    val y = year.toString()
    val trips = p.journeys.filter { it.date.startsWith(y) && it.date <= today && it.status in travelled }
    val spent = p.journeys.filter { it.date.startsWith(y) && it.fare != null && it.status != JourneyStatus.Skipped }
        .sumOf { it.fare!!.total - (it.fare.refund ?: 0.0) }
    val top = trips.groupBy { listOf(it.from, it.to).sorted().let { (a, b) -> a to b } }.maxByOrNull { it.value.size }
    val ordered = p.journeys.sortedBy { it.date + it.departure }
    val tatkalYear = ordered.filter { it.bookingType == "tatkal" && it.date.startsWith(y) }.mapNotNull { booked(it, today) }
    val weeksTravelled = p.journeys.filter { it.date <= today && it.status in travelled }.map { weekStart(it.date) }.toSet()
    var week = weekStart(today).let { if (it in weeksTravelled) it else addDays(it, -7) }
    var weeks = 0
    while (week in weeksTravelled) { weeks++; week = addDays(week, -7) }
    val withStatus = trips.filter { it.ticketStatus != null }
    val waitlisted = trips.filter { it.ticketStatus == "WL" || it.statusLog.orEmpty().any { e -> e.status == "WL" } }
    val longest = trips.mapNotNull { j -> rideMinutes(j)?.let { j to it } }.maxByOrNull { it.second }
    val berths = trips.mapNotNull { it.berth?.trim()?.uppercase()?.takeIf { b -> b.isNotEmpty() && b.any(Char::isLetter) } }
    return YearStats(
        year = year, trips = trips.size, spent = spent, minutes = trips.sumOf { rideMinutes(it) ?: 0 },
        stations = trips.flatMap { listOf(it.from, it.to) }.toSet().size,
        route = top?.key, routeTrips = top?.value?.size ?: 0,
        routeTrain = top?.value?.mapNotNull { j -> j.trainNumber?.takeIf(String::isNotBlank)?.let { n -> listOfNotNull(n, j.trainName?.takeIf(String::isNotBlank)).joinToString(" ") } }?.mostCommon(),
        routeClass = top?.value?.map { it.travelClass }?.filter(String::isNotBlank)?.mostCommon(),
        byMonth = (1..12).map { m -> trips.count { it.date.substring(5, 7).toInt() == m } },
        tatkal = streak(ordered.filter { it.bookingType == "tatkal" }.mapNotNull { booked(it, today) }),
        tatkalTries = tatkalYear.size, tatkalBooked = tatkalYear.count { it },
        windows = streak(ordered.mapNotNull { booked(it, today) }), weeks = weeks,
        confirmedFirst = withStatus.count { it.ticketStatus == "CNF" }, withStatus = withStatus.size,
        waitlistCleared = waitlisted.count { effectiveTicketStatus(it) in setOf("CNF", "RAC") }, waitlisted = waitlisted.size,
        longest = longest?.first, longestMinutes = longest?.second ?: 0,
        earliest = trips.map { it.departure }.filter { it.length >= 4 }.minOrNull(),
        berth = berths.groupingBy { it }.eachCount().maxByOrNull { it.value }?.toPair(),
    )
}

/** Years that have any trip, newest first, always including this one. */
fun statYears(p: Planner, today: String): List<Int> =
    (p.journeys.mapNotNull { it.date.take(4).toIntOrNull() }.filter { it <= today.take(4).toInt() } + today.take(4).toInt()).toSet().sortedDescending()
