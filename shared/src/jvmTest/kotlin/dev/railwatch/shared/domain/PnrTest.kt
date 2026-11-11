package dev.railwatch.shared.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Dummy PNRs and statuses only. */
class PnrTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun readsTheEnquiryShape() {
        val s = parsePnrStatus(json("""{"pnrNumber":"1234567890","chartStatus":"Chart Not Prepared","passengerList":[{"passengerSerialNumber":1,"bookingStatusDetails":"WL/12","currentStatusDetails":"RAC/4","currentCoachId":"S3","currentBerthNo":"0"}]}"""), "1234567890", now)
        assertEquals(PnrSnapshot("1234567890", "2026-11-12T10:00:00Z", "Chart Not Prepared", listOf(PnrPassenger(1, "WL/12", "RAC/4", "S3", "0"))), s)
        assertEquals("RAC", pnrSummary(s))
    }

    @Test fun rejectsAnotherPnrOrAnError() {
        assertFailsWith<PnrLookupFailed> { parsePnrStatus(json("""{"pnrNumber":"1111111111","passengerList":[{"currentStatus":"CNF"}]}"""), "1234567890", now) }
        assertFailsWith<PnrLookupFailed> { parsePnrStatus(json("""{"errorMessage":"No data"}"""), "1234567890", now) }
    }

    @Test fun mixedNeverShowsConfirmed() {
        fun snap(vararg c: String) = PnrSnapshot("1234567890", now.toString(), "", c.mapIndexed { i, s -> PnrPassenger(i + 1, "", s, "", "") })
        assertEquals("CNF", pnrSummary(snap("CNF/S7/14/UB", "CNF/S7/15/MB")))
        assertEquals("WL", pnrSummary(snap("CNF/S7/14", "WL/3")))
        assertEquals("CANCELLED", pnrSummary(snap("CAN", "CAN")))
    }

    @Test fun applyLogsChangesAndNeverCancels() {
        val j = Journey(id = "j", from = "AAA", to = "BBB", date = "2026-11-14", departure = "22:00", status = JourneyStatus.Booked, pnr = "1234567890", ticketStatus = "WL")
        val after = applyPnrStatus(j, PnrSnapshot("1234567890", now.toString(), "", listOf(PnrPassenger(1, "WL/3", "CAN", "", ""))))
        assertEquals(JourneyStatus.Booked, after.status)
        assertEquals(listOf(StatusEntry(now.toString(), "CANCELLED")), after.statusLog)
    }

    @Test fun suggestsChecksCloseToTravel() {
        val j = Journey(id = "j", from = "AAA", to = "BBB", date = "2026-11-14", departure = "22:00", status = JourneyStatus.Booked, pnr = "1234567890", ticketStatus = "CNF")
        assertTrue(suggestPnrCheck(j, "2026-11-12", now))
        assertFalse(suggestPnrCheck(j.copy(date = "2026-11-20"), "2026-11-12", now))
        assertFalse(suggestPnrCheck(applyPnrStatus(j, PnrSnapshot("1234567890", now.toString(), "", listOf(PnrPassenger(1, "", "CNF/S1/2", "", "")))), "2026-11-12", now))
    }
}
