package dev.railwatch.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Synthetic trips only. */
class BookAgainTest {
    private val past = Journey(id = "a", from = "AAA", to = "BBB", date = "2026-11-06", departure = "21:00", arrival = "06:00", arrivalDate = "2026-11-07",
        trainNumber = "12345", travelClass = "3A", status = JourneyStatus.Completed, pnr = "1234567890", berth = "LB", checked = listOf("ID proof"))

    @Test fun copiesTheTripWithoutTheBooking() {
        val n = bookAgain(past, "2026-11-27", "n", 60)
        assertEquals(listOf("AAA", "BBB", "12345", "3A", "2026-11-28"), listOf(n.from, n.to, n.trainNumber, n.travelClass, n.arrivalDate))
        assertEquals(JourneyStatus.NeedsBooking, n.status)
        assertEquals("", n.pnr); assertNull(n.berth); assertNull(n.checked)
    }

    @Test fun suggestsTheSameWeekdayAndFindsTheReturn() {
        assertEquals(listOf("2026-11-20", "2026-11-27"), bookAgainDates(past, "2026-11-18"))
        val back = Journey(id = "b", from = "BBB", to = "AAA", date = "2026-11-08", departure = "21:00")
        assertEquals("b", returnOf(Planner(journeys = listOf(past, back)), past)?.id)
        assertNull(returnOf(Planner(journeys = listOf(past)), past))
    }
}
