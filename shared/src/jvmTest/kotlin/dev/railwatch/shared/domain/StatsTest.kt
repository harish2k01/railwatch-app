package dev.railwatch.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic trips only. */
class StatsTest {
    private val today = "2026-11-18"
    private fun t(id: String, date: String, s: JourneyStatus = JourneyStatus.Completed, tatkal: Boolean = false, from: String = "AAA", to: String = "BBB") =
        Journey(id = id, from = from, to = to, date = date, departure = "21:00", arrival = "06:30", arrivalDate = addDays(date, 1), status = s,
            bookingType = if (tatkal) "tatkal" else null, pnr = if (s == JourneyStatus.NeedsBooking) "" else "1234567890", travelClass = "3A", berth = "LB")

    @Test fun yearTotalsAndRoute() {
        val p = Planner(journeys = listOf(t("1", "2026-01-05"), t("2", "2026-01-07", from = "BBB", to = "AAA"), t("3", "2026-03-01", from = "AAA", to = "CCC"),
            t("4", "2025-12-30"), t("5", "2026-12-20", JourneyStatus.Booked)))
        val s = yearStats(p, 2026, today)
        assertEquals(3, s.trips)
        assertEquals("AAA" to "BBB", s.route); assertEquals(2, s.routeTrips)
        assertEquals(3 * 570, s.minutes)
        assertEquals(2, s.byMonth[0])
        assertEquals("LB" to 3, s.berth)
    }

    @Test fun tatkalStreakBreaksOnMissedWindowsOnly() {
        val p = Planner(journeys = listOf(t("1", "2026-01-05", tatkal = true), t("2", "2026-02-05", JourneyStatus.NeedsBooking, tatkal = true),
            t("3", "2026-03-05", tatkal = true), t("4", "2026-04-05", JourneyStatus.Skipped, tatkal = true), t("5", "2026-05-05", tatkal = true)))
        val s = yearStats(p, 2026, today)
        assertEquals(Streak(2, 2), s.tatkal)
        assertEquals(4, s.tatkalTries); assertEquals(3, s.tatkalBooked)
    }

    @Test fun weeklyStreakCountsBackFromThisWeek() {
        val p = Planner(journeys = listOf(t("1", "2026-11-16"), t("2", "2026-11-10"), t("3", "2026-11-03"), t("4", "2026-10-20")))
        assertEquals(3, yearStats(p, 2026, today).weeks)
    }
}
