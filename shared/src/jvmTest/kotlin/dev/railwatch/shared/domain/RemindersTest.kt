package dev.railwatch.shared.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RemindersTest {
    private val j = Journey(id = "j", from = "MDU", to = "MS", date = "2027-01-12", departure = "23:10") // booking opens 13 Nov 2026

    @Test fun defaultBookingRemindersAreEveningMorningAndOpening() {
        assertEquals(listOf("2026-11-12T14:30:00Z", "2026-11-13T01:30:00Z", "2026-11-13T02:25:00Z"),
            reminderTimes(Planner(), j, "2026-11-11").map { it.toString() })
    }

    @Test fun tatkalUsesItsOwnScheduleAndNonAcShiftsAnHour() {
        val t = j.copy(bookingType = "tatkal", tatkalClass = "non_ac", trainOriginDate = "2027-01-12")
        assertEquals(listOf("2027-01-10T14:30:00Z", "2027-01-11T05:00:00Z", "2027-01-11T05:25:00Z") /* 20:00 the day before, 10:30 and 10:55 IST */, reminderTimes(Planner(), t, "2026-11-11").map { it.toString() })
    }

    @Test fun aJourneyCanTurnRemindersOffOrUseItsOwn() {
        assertEquals(emptyList(), reminderTimes(Planner(), j.copy(reminderOverride = ReminderOverride("off")), "2026-11-11"))
        val own = ReminderOverride("custom", schedule = listOf(ScheduleRow(2, "18:00")))
        assertEquals(listOf("2026-11-11T12:30:00Z"), reminderTimes(Planner(), j.copy(reminderOverride = own), "2026-11-11").map { it.toString() })
        // A routine's choice applies to its trips unless the trip has its own.
        val p = Planner(rules = listOf(Rule(id = "r", name = "R", from = "A", to = "B", start = "2026-11-01", weekdays = listOf(2), departure = "22:00", reminderOverride = ReminderOverride("off"))))
        assertEquals(emptyList(), reminderTimes(p, j.copy(ruleId = "r"), "2026-11-11"))
        assertEquals(3, reminderTimes(p, j.copy(ruleId = "r", reminderOverride = ReminderOverride("custom", times = listOf("previous_evening", "morning", "opening"))), "2026-11-11").size)
    }

    @Test fun bookedTripsGetNoBookingReminders() { assertEquals(emptyList(), reminderTimes(Planner(), j.copy(status = JourneyStatus.Booked), "2026-11-11")) }

    @Test fun cancellationFollowUpIsTodayAtTheSetTime() {
        assertEquals(listOf(Instant.parse("2026-11-11T03:30:00Z")), reminderTimes(Planner(), j.copy(status = JourneyStatus.CancellationNeeded), "2026-11-11"))
    }

    @Test fun quietHoursMoveToTheirEnd() {
        val q = QuietHours(enabled = true, start = "22:00", end = "07:00")
        assertEquals(Instant.parse("2026-11-13T01:30:00Z"), outsideQuietHours(Instant.parse("2026-11-12T17:00:00Z"), q)) // 22:30 IST → 07:00 next day
        assertEquals(Instant.parse("2026-11-13T01:30:00Z"), outsideQuietHours(Instant.parse("2026-11-12T20:00:00Z"), q)) // 01:30 IST → 07:00 same day
        assertEquals(Instant.parse("2026-11-12T14:30:00Z"), outsideQuietHours(Instant.parse("2026-11-12T14:30:00Z"), q)) // 20:00 IST stays
    }
}
