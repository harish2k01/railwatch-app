package dev.railwatch.app.ui

import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.bookingDay
import dev.railwatch.shared.domain.daysBetween
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date labels in Indian English, from "YYYY-MM-DD". */
private val IN = Locale.forLanguageTag("en-IN")
private fun fmt(day: String, pattern: String) = LocalDate.parse(day).format(DateTimeFormatter.ofPattern(pattern, IN))
fun shortDay(day: String) = fmt(day, "EEE d MMM")          // Tue 17 Nov
fun longDay(day: String) = fmt(day, "EEEE, d MMMM")       // Tuesday, 17 November
fun dayNum(day: String) = fmt(day, "dd")                  // 17
fun weekdayShort(day: String) = fmt(day, "EEE").uppercase(IN) // TUE
fun monthLabel(month: String, today: String): String {     // November, or November 2027 in another year
    val d = "$month-01"
    return if (month.take(4) == today.take(4)) fmt(d, "MMMM") else fmt(d, "MMMM yyyy")
}
fun dayMonth(day: String) = fmt(day, "d MMM")              // 17 Nov

/** "today", "tomorrow", "in 6 days". */
fun relative(today: String, day: String): String = when (val n = daysBetween(today, day)) { 0 -> "today"; 1 -> "tomorrow"; else -> if (n > 0) "in $n days" else "${-n} days ago" }

/** How a trip's state reads to a person. */
fun statusLabel(j: Journey): String = when (j.status) {
    JourneyStatus.NeedsBooking -> "Planned"
    JourneyStatus.Booked -> when (j.ticketStatus) { "WL" -> "Waitlisted"; "RAC" -> "RAC"; else -> "Confirmed" }
    JourneyStatus.CancellationNeeded -> "To cancel"
    JourneyStatus.Cancelled -> "Cancelled"
    JourneyStatus.Skipped -> "Skipped"
    JourneyStatus.Completed -> "Travelled"
}

/** The detail line under a journey row: train and seat when booked, else when booking opens. */
fun journeyLine(j: Journey, today: String): String {
    val seat = listOfNotNull(j.coach, j.seat).joinToString(" / ") + (j.berth?.let { " " + berthLabel(it) } ?: "")
    val ticket = listOfNotNull(j.trainNumber?.takeIf { it.isNotBlank() }, seat.trim().takeIf { it.isNotEmpty() }).joinToString(" · ")
    if (j.status == JourneyStatus.NeedsBooking) {
        val opens = bookingDay(j)
        return if (opens <= today) "Booking open · not booked" else "Booking opens ${shortDay(opens)}"
    }
    return ticket.ifEmpty { if (j.departureConfirmed == true) "Departs ${j.departure}" else "" }
}

/** "UPPER" → "Upper"; short codes like "LB" stay as they are. */
fun berthLabel(b: String) = if (b.length > 3) b.lowercase().replaceFirstChar { it.uppercase() } else b
