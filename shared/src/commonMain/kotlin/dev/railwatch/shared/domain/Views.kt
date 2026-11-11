package dev.railwatch.shared.domain

/** What the Today and Journeys screens show, worked out from the planner (shared so iOS gets the same answers). */

/** A Do next card on Today. */
sealed interface DoNext {
    val journey: Journey
    /** Booking has opened and the trip isn't booked yet. */
    data class BookNow(override val journey: Journey) : DoNext
    /** Booking opens within the next week. */
    data class OpensSoon(override val journey: Journey, val opens: String) : DoNext
    /** Marked to cancel: confirm once it's cancelled on IRCTC. */
    data class ConfirmCancel(override val journey: Journey) : DoNext
    /** Booked, but no ticket shared yet. */
    data class NeedsTicket(override val journey: Journey) : DoNext
}

private val ACTIVE = setOf(JourneyStatus.NeedsBooking, JourneyStatus.Booked, JourneyStatus.CancellationNeeded)

/** Upcoming trips still in play, soonest first. */
fun upcoming(p: Planner, today: String): List<Journey> =
    p.journeys.filter { it.archivedAt == null && it.date >= today && it.status in ACTIVE }.sortedWith(compareBy({ it.date }, { it.departure }))

/** The next booked trip, else the next planned one. */
fun nextTrip(p: Planner, today: String): Journey? = upcoming(p, today).let { list -> list.firstOrNull { it.status == JourneyStatus.Booked } ?: list.firstOrNull() }

fun hasTicket(j: Journey) = j.attachments.orEmpty().any { it.type == "application/pdf" }

/** Today's Do next cards, most urgent first, at most [limit]. */
fun doNext(p: Planner, today: String, limit: Int = 6): List<DoNext> {
    val list = upcoming(p, today)
    val cancel = list.filter { it.status == JourneyStatus.CancellationNeeded }.map { DoNext.ConfirmCancel(it) }
    val book = list.filter { it.status == JourneyStatus.NeedsBooking && bookingDay(it) <= today }.map { DoNext.BookNow(it) }
    val ticket = list.filter { it.status == JourneyStatus.Booked && !hasTicket(it) }.take(1).map { DoNext.NeedsTicket(it) }
    val soon = list.filter { it.status == JourneyStatus.NeedsBooking && bookingDay(it) > today && daysBetween(today, bookingDay(it)) <= 7 }
        .sortedBy { bookingDay(it) }.map { DoNext.OpensSoon(it, bookingDay(it)) }
    return (cancel + book + ticket + soon).take(limit)
}

/** The Journeys lists. */
enum class JourneyList { Upcoming, ToBook, History }

fun journeyList(p: Planner, today: String, list: JourneyList): List<Journey> = when (list) {
    JourneyList.Upcoming -> upcoming(p, today)
    JourneyList.ToBook -> upcoming(p, today).filter { it.status == JourneyStatus.NeedsBooking && bookingDay(it) <= today }
    JourneyList.History -> p.journeys.filter { it.archivedAt == null && (it.date < today || it.status == JourneyStatus.Completed || it.status == JourneyStatus.Cancelled || it.status == JourneyStatus.Skipped) }
        .sortedWith(compareByDescending<Journey> { it.date }.thenByDescending { it.departure })
}

/** Groups a list into months ("2026-11" → trips), keeping order. */
fun byMonth(list: List<Journey>): List<Pair<String, List<Journey>>> =
    list.groupBy { it.date.substring(0, 7) }.toList()
