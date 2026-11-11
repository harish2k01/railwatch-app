package dev.railwatch.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeOffTest {
    @Test fun icsExpandsAllDayEventsAndSkipsTimedOnes() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART;VALUE=DATE:20261225\r\nDTEND;VALUE=DATE:20261227\r\nSUMMARY:Christmas\\, br\r\n eak\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nDTSTART:20261110T090000Z\r\nSUMMARY:Meeting\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nDTSTART:20261109\r\nSUMMARY:Diwali\r\nEND:VEVENT\r\nEND:VCALENDAR"
        assertEquals(listOf(TimeOffDay("2026-12-25", "Christmas, break", "company"), TimeOffDay("2026-12-26", "Christmas, break", "company"), TimeOffDay("2026-11-09", "Diwali", "company")), parseTimeOffIcs(ics))
    }

    @Test fun csvNeedsTheExactColumns() {
        assertEquals(listOf(TimeOffDay("2027-01-01", "New Year, company", "company")), parseTimeOffCsv("date,name,type\n2027-01-01,\"New Year, company\",company\n"))
        assertFailsWith<IllegalArgumentException> { parseTimeOffCsv("name,date\nx,2027-01-01") }
        assertFailsWith<IllegalArgumentException> { parseTimeOffCsv("date,name,type\n2027-1-1,x,company") }
    }

    @Test fun conflictsAreOpenTripsOnUpcomingTimeOffUnlessKept() {
        val a = Journey(id = "a", from = "MDU", to = "MS", date = "2026-12-25", departure = "22:00")
        val p = Planner(journeys = listOf(a, a.copy(id = "b", timeOffKept = true), a.copy(id = "c", status = JourneyStatus.Skipped), a.copy(id = "d", date = "2026-12-24")),
            holidays = listOf(Holiday("h", "Christmas", "2026-12-25", "company")))
        assertEquals(listOf("a"), timeOffConflicts(p, "2026-11-12").map { it.id })
    }

    @Test fun addingSkipsDatesAlreadyOff() {
        val p = Planner(holidays = listOf(Holiday("h", "X", "2026-12-25", "company")))
        val (q, n) = addTimeOff(p, listOf(TimeOffDay("2026-12-25", "Y", "leave"), TimeOffDay("2026-12-24", "Z", "leave")), { "n" })
        assertEquals(1, n); assertEquals(listOf("2026-12-24", "2026-12-25"), q.holidays.map { it.date })
    }

    @Test fun spendIsFareLessRefundByMonth() {
        val j = Journey(id = "a", from = "MDU", to = "MS", date = "2026-11-17", departure = "23:10", status = JourneyStatus.Booked, fare = Fare(total = 352.25))
        val p = Planner(journeys = listOf(j, j.copy(id = "b", status = JourneyStatus.Cancelled, fare = Fare(total = 400.0, refund = 300.0)), j.copy(id = "c", date = "2026-12-01")))
        assertEquals(listOf(Spend("2026-12", 352.25, 1), Spend("2026-11", 452.25, 2)), spendByMonth(p))
    }
}

class LongWeekendTest {
    @Test fun aFridayHolidayMakesALongWeekend() {
        // 2026-12-25 is a Friday: Fri–Sun is 3 days off.
        val p = Planner(holidays = listOf(Holiday("h", "Christmas", "2026-12-25", "company")))
        val w = longWeekends(p, "2026-11-12").single()
        assertEquals(LongWeekend("2026-12-25", "2026-12-27", 3, listOf("Christmas"), "2026-10-26", true), w)
    }

    @Test fun rangesJoinConsecutiveDaysOfTheSameLeave() {
        val p = Planner(holidays = listOf(Holiday("a", "Trip", "2026-12-01", "leave"), Holiday("b", "Trip", "2026-12-02", "leave"), Holiday("c", "Diwali", "2026-12-04", "company")))
        assertEquals(listOf(TimeOffRange("2026-12-01", "2026-12-02", "Trip", "leave", listOf("a", "b")), TimeOffRange("2026-12-04", "2026-12-04", "Diwali", "company", listOf("c"))), timeOffRanges(p, "2026-11-12"))
    }
}
