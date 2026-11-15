package dev.railwatch.shared

import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.Planner
import dev.railwatch.shared.domain.clashes
import dev.railwatch.shared.domain.matchesQuery
import dev.railwatch.shared.domain.missingReturns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Synthetic trips only. */
class ClashSearchTest {
    private val today = "2026-11-16"
    private fun trip(id: String, from: String, to: String, date: String, dep: String, arrive: String? = null, arriveDate: String? = null, status: JourneyStatus = JourneyStatus.Booked, oneWay: Boolean? = null) =
        Journey(id = id, from = from, to = to, date = date, departure = dep, arrival = arrive, arrivalDate = arriveDate, status = status, oneWay = oneWay)

    @Test fun overlappingTripsClash() {
        val a = trip("a", "AAA", "BBB", "2026-11-20", "21:00", "06:00", "2026-11-21")
        val b = trip("b", "BBB", "CCC", "2026-11-21", "05:00", status = JourneyStatus.NeedsBooking)
        val c = trip("c", "BBB", "AAA", "2026-11-22", "21:00")
        assertEquals(listOf("a" to "b"), clashes(Planner(journeys = listOf(a, b, c)), today).map { it.first.id to it.second.id })
    }

    @Test fun backToBackTripsDontClash() {
        val a = trip("a", "AAA", "BBB", "2026-11-20", "08:00", "12:00")
        val b = trip("b", "BBB", "AAA", "2026-11-20", "12:00", "16:00")
        assertTrue(clashes(Planner(journeys = listOf(a, b)), today).isEmpty())
    }

    @Test fun bookedTripWithoutReturnIsFlaggedUnlessOneWay() {
        val out = trip("a", "AAA", "BBB", "2026-11-20", "21:00")
        assertEquals(listOf("a"), missingReturns(Planner(journeys = listOf(out)), today).map { it.id })
        val back = trip("b", "BBB", "AAA", "2026-11-25", "21:00", status = JourneyStatus.NeedsBooking)
        assertTrue(missingReturns(Planner(journeys = listOf(out, back)), today).isEmpty())
        assertTrue(missingReturns(Planner(journeys = listOf(out.copy(oneWay = true))), today).isEmpty())
    }

    @Test fun searchMatchesStationsDatesTrainAndPnr() {
        val j = trip("a", "AAA", "BBB", "2027-01-12", "21:00").copy(pnr = "1234567890", trainNumber = "12345", trainName = "SAMPLE EXP")
        val names = mapOf("AAA" to "Alpha Junction")
        assertTrue(matchesQuery(j, "alpha", names::get))
        assertTrue(matchesQuery(j, "12 jan"))
        assertTrue(matchesQuery(j, "january"))
        assertTrue(matchesQuery(j, "sample"))
        assertTrue(matchesQuery(j, "4567890"))
        assertFalse(matchesQuery(j, "feb"))
        assertTrue(matchesQuery(j, ""))
    }
}
