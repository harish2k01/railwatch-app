package dev.railwatch.shared.data

import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.Rule
import dev.railwatch.shared.domain.saveRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerStoreTest {
    private class Memory(var text: String? = null) : PlannerFiles { override fun read() = text; override fun write(text: String) { this.text = text } }

    @Test fun readsAWebBackupWithExtraFields() {
        val web = """{"version":1,"journeys":[{"id":"j1","from":"MDU","to":"MS","date":"2027-01-12","departure":"23:10","train":"","travelClass":"SL","windowDays":60,"originOffset":0,"status":"booked","pnr":"1234567890","notes":"","pnrStatus":{"pnr":"1234567890"},"coach":"S7"}],"rules":[],"holidays":[],"settings":{"bookingWindowDays":60,"emailEnabled":true,"telegramChatId":"","reminderClock":{"previous_evening":"20:00","morning":"07:00","opening":"07:55"}}}"""
        val p = PlannerCodec.decode(web)
        assertEquals(JourneyStatus.Booked, p.journeys.single().status)
        assertEquals("S7", p.journeys.single().coach)
    }

    @Test fun firstRunIsEmptyAndChangesPersist() {
        val files = Memory()
        val store = PlannerStore(files) { "2026-11-11" }
        assertTrue(store.planner.journeys.isEmpty())
        val rule = Rule(id = "r1", name = "Office", from = "MDU", to = "MS", start = "2026-11-02", weekdays = listOf(2), departure = "23:10")
        store.update { saveRule(it, rule, "2026-11-11") }
        val reopened = PlannerStore(files) { "2026-11-11" }
        assertEquals(store.planner.journeys.map { it.id }, reopened.planner.journeys.map { it.id })
        assertTrue(reopened.planner.journeys.isNotEmpty())
    }

    @Test fun reopeningLaterTopsUpRoutinesAndCompletesPastTrips() {
        val files = Memory()
        val rule = Rule(id = "r1", name = "Office", from = "MDU", to = "MS", start = "2026-11-02", weekdays = listOf(2), departure = "23:10")
        PlannerStore(files) { "2026-11-11" }.update { p -> saveRule(p, rule, "2026-11-11").let { s -> s.copy(journeys = s.journeys.mapIndexed { i, j -> if (i == 0) j.copy(status = JourneyStatus.Booked) else j }) } }
        val later = PlannerStore(files) { "2026-12-20" }.planner
        assertEquals(JourneyStatus.Completed, later.journeys.first { it.id == "r1:2026-11-17:outbound" }.status)
        assertTrue(later.journeys.any { it.date > "2027-05-20" }, "horizon moved forward")
    }
}
