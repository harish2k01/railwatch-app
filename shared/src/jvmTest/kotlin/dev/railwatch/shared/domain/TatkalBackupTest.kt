package dev.railwatch.shared.domain

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Synthetic trips only. */
class TatkalBackupTest {
    private val base = Journey(id = "a", from = "AAA", to = "BBB", date = "2026-11-20", departure = "21:00", travelClass = "3A", status = JourneyStatus.Booked, currentStatus = "WL 12")

    @Test fun remindsBeforeTatkalOpensForWaitlistAndRac() {
        assertEquals(Instant.parse("2026-11-19T09:45:00+05:30"), tatkalBackupTime(base))
        assertEquals(Instant.parse("2026-11-19T10:45:00+05:30"), tatkalBackupTime(base.copy(travelClass = "SL", currentStatus = "RAC 3")))
        assertEquals(Instant.parse("2026-11-18T09:45:00+05:30"), tatkalBackupTime(base.copy(trainOriginDate = "2026-11-19")))
    }

    @Test fun notForConfirmedOrTatkalTickets() {
        assertNull(tatkalBackupTime(base.copy(currentStatus = "CNF S4 24")))
        assertNull(tatkalBackupTime(base.copy(bookingType = "tatkal")))
        assertNull(tatkalBackupTime(base.copy(status = JourneyStatus.NeedsBooking)))
    }
}
