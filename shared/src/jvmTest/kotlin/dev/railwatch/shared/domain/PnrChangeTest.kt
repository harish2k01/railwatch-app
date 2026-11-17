package dev.railwatch.shared.domain

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Dummy PNRs and statuses only. */
class PnrChangeTest {
    private fun snap(vararg p: Pair<String, String>) = PnrSnapshot("1234567890", "2026-11-12T10:00:00Z", "", p.mapIndexed { i, (b, c) -> PnrPassenger(i + 1, b, c) })

    @Test fun showsWhatMoved() {
        assertEquals("WL 12 → RAC 3", pnrChange(snap("WL/40" to "WL/12"), snap("WL/40" to "RAC/3")))
        assertEquals("WL 40 → WL 12", pnrChange(null, snap("WL/40" to "WL/12")))
        assertNull(pnrChange(snap("WL/40" to "WL/12"), snap("WL/40" to "WL/12")))
        assertEquals("P2 WL 13 → RAC 4", pnrChange(snap("WL/1" to "CNF/S1/1", "WL/2" to "WL/13"), snap("WL/1" to "CNF/S1/1", "WL/2" to "RAC/4")))
    }

    @Test fun logsMovesWithinWaitlist() {
        val j = Journey(id = "a", from = "AAA", to = "BBB", date = "2026-11-20", departure = "21:00", pnr = "1234567890", status = JourneyStatus.Booked)
        val first = applyPnrStatus(j, snap("WL/40" to "WL/12"))
        val second = applyPnrStatus(first, snap("WL/40" to "WL/5").copy(checkedAt = "2026-11-13T10:00:00Z"))
        assertEquals(listOf("WL 40 → WL 12", "WL 12 → WL 5"), second.statusLog?.map { it.detail })
    }

    @Test fun chartTimeFollowsTheEightHourRule() {
        fun at(dep: String) = chartTime(Journey(id = "a", from = "AAA", to = "BBB", date = "2026-11-20", departure = dep))
        assertEquals(Instant.parse("2026-11-20T13:00:00+05:30"), at("21:00"))
        assertEquals(Instant.parse("2026-11-19T21:00:00+05:30"), at("09:30"))
        assertEquals(Instant.parse("2026-11-19T21:00:00+05:30"), at("14:00"))
        assertEquals(Instant.parse("2026-11-19T20:00:00+05:30"), at("04:00"))
    }
}
