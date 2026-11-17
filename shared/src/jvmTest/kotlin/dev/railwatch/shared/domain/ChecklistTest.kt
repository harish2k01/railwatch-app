package dev.railwatch.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Synthetic trips only. */
class ChecklistTest {
    private val lists = listOf(
        Checklist("a", "Every Trip", listOf("Ticket saved offline", "ID proof")),
        Checklist("b", "Sleeper", listOf("Chain lock", "ID proof"), ChecklistRule("class", listOf("SL"))),
        Checklist("c", "Overnight", listOf("Toothbrush"), ChecklistRule("overnight")),
    )
    private val p = Planner(checklists = lists)
    private val j = Journey(id = "j", from = "AAA", to = "BBB", date = "2026-11-20", departure = "21:00", arrival = "06:00", arrivalDate = "2026-11-21", travelClass = "SL", status = JourneyStatus.Booked)

    @Test fun mergesMatchingListsWithoutDuplicates() {
        assertEquals(listOf("Every Trip" to listOf("Ticket saved offline", "ID proof"), "Sleeper" to listOf("Chain lock"), "Overnight" to listOf("Toothbrush")),
            tripChecklist(p, j).map { g -> g.name to g.items.map { it.text } })
        assertEquals(listOf("Every Trip"), tripChecklist(p, j.copy(travelClass = "3A", arrival = "23:00", arrivalDate = null)).map { it.name })
    }

    @Test fun tickSkipAndOneOffItems() {
        var t = setChecked(j, "ID proof", true)
        t = setSkipped(t, "Chain lock", true)
        t = addTripItem(t, "Umbrella")
        assertEquals(listOf("Ticket saved offline", "Toothbrush", "Umbrella"), checklistLeft(p, t))
        assertEquals("This Trip", tripChecklist(p, t).last().name)
        // Skipping a ticked item unticks it; ticking a skipped one un-skips it.
        t = setSkipped(t, "ID proof", true)
        assertTrue(tripChecklist(p, t).first().items.first { it.text == "ID proof" }.let { it.skipped && !it.done })
        assertEquals(listOf("Ticket saved offline", "ID proof", "Chain lock", "Toothbrush"), checklistLeft(p, removeTripItem(setChecked(setSkipped(j, "x", false), "Umbrella", false), "Umbrella")))
    }

    @Test fun defaultListAndNudge() {
        assertEquals(listOf("Every Trip"), tripChecklist(Planner(), j).map { it.name })
        val reminders = upcomingReminders(Planner(journeys = listOf(j)), "2026-11-18", kotlin.time.Instant.parse("2026-11-18T00:00:00Z"))
        assertTrue(reminders.any { it.kind == Reminder.Kind.Checklist })
    }
}
