package dev.railwatch.shared.domain

/**
 * A new trip like [source] on [date]: same route, train, class, times and quota, to book again. Nothing from the old
 * booking comes along (PNR, ticket, berth, statuses, checklist ticks).
 */
fun bookAgain(source: Journey, date: String, id: String, windowDays: Int): Journey {
    val shift = daysBetween(source.date, date)
    return Journey(
        id = id, from = source.from, to = source.to, date = date, departure = source.departure, departureConfirmed = source.departureConfirmed,
        train = source.train, trainNumber = source.trainNumber, trainName = source.trainName, travelClass = source.travelClass,
        bookingType = source.bookingType, tatkalClass = source.tatkalClass, windowDays = windowDays,
        originOffset = source.originOffset, trainOriginDate = source.trainOriginDate?.let { addDays(it, shift) },
        arrival = source.arrival, arrivalDate = source.arrivalDate?.let { addDays(it, shift) }, leg = source.leg,
        reminderOverride = source.reminderOverride, oneWay = source.oneWay,
    )
}

/** The next two dates on the same weekday as [source], after today. */
fun bookAgainDates(source: Journey, today: String): List<String> {
    val ahead = ((weekday(source.date) - weekday(today)) + 7) % 7
    val first = addDays(today, if (ahead == 0) 7 else ahead)
    return listOf(first, addDays(first, 7))
}

/** The way back after [source]: a trip from its destination to its start within 30 days after, if there was one. */
fun returnOf(p: Planner, source: Journey): Journey? =
    p.journeys.filter { it.from == source.to && it.to == source.from && it.date >= source.date && daysBetween(source.date, it.date) <= 30 && it.status != JourneyStatus.Skipped }
        .minByOrNull { it.date }
