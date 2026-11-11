package dev.railwatch.shared.domain

import dev.railwatch.shared.parsing.TicketDetails

/**
 * Attaches a read ticket to a journey: the journey becomes booked and takes the ticket's train, PNR, class, coach,
 * seat, berth, status, fare and times (a value already on the journey is kept when the ticket lacks it).
 */
fun applyTicket(j: Journey, d: TicketDetails, file: Attachment, today: String): Journey {
    val base = if (j.status == JourneyStatus.NeedsBooking || j.status == JourneyStatus.Skipped) transition(j, JourneyStatus.Booked, today) else j
    return base.copy(
        attachments = base.attachments.orEmpty().filter { it.id != file.id } + file,
        readTickets = (base.readTickets.orEmpty() + file.id).distinct(),
        pnr = d.pnr ?: base.pnr,
        trainNumber = d.trainNumber ?: base.trainNumber, trainName = d.trainName ?: base.trainName, train = d.trainNumber ?: base.train,
        travelClass = d.travelClass ?: base.travelClass, coach = d.coach ?: base.coach, seat = d.seat ?: base.seat, berth = d.berth ?: base.berth,
        ticketStatus = d.ticketStatus ?: base.ticketStatus, fare = d.fare ?: base.fare,
        departure = d.departure ?: base.departure, departureConfirmed = if (d.departure != null) true else base.departureConfirmed,
        arrival = d.arrival ?: base.arrival, arrivalDate = d.arrivalDate ?: base.arrivalDate,
        bookingType = d.bookingType ?: base.bookingType,
    )
}

/** A booked one-off journey made from a ticket that matched nothing. Needs a date and both stations. */
fun journeyFromTicket(id: String, d: TicketDetails, file: Attachment, settings: Settings, today: String): Journey? {
    val date = d.date ?: return null; val from = d.from ?: return null; val to = d.to ?: return null
    val j = Journey(id = id, from = from, to = to, date = date, departure = d.departure ?: "00:00", departureConfirmed = d.departure != null, windowDays = settings.bookingWindowDays, status = JourneyStatus.Booked)
    return applyTicket(j, d, file, today)
}

/** Ticket files due for removal: PDFs of journeys that ended (travelled or cancelled) more than the retention days ago. */
fun expiredTicketFiles(p: Planner, today: String): Pair<Planner, List<String>> {
    val keep = p.settings.ticketRetentionDays ?: 7
    val removed = mutableListOf<String>()
    val journeys = p.journeys.map { j ->
        val ended = j.completedAt ?: j.cancelledAt.takeIf { j.status == JourneyStatus.Cancelled }
        if (ended == null || j.attachments.isNullOrEmpty() || daysBetween(ended, today) < keep) j
        else { removed += j.attachments.map { it.id }; j.copy(attachments = null) }
    }
    return (if (removed.isEmpty()) p else p.copy(journeys = journeys)) to removed
}
