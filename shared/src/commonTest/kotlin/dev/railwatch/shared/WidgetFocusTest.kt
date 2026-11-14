package dev.railwatch.shared

import dev.railwatch.shared.domain.DoNext
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.Planner
import dev.railwatch.shared.domain.WidgetFocus
import dev.railwatch.shared.domain.doNext
import dev.railwatch.shared.domain.widgetFocus
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Synthetic trips only. A trip on 12 Jan 2027 with a 60-day window opens 13 Nov 2026 at 08:00 IST. */
class WidgetFocusTest {
    private val trip = Journey(id = "a", from = "AAA", to = "BBB", date = "2027-01-12", departure = "21:00", status = JourneyStatus.NeedsBooking)
    private val planner = Planner(journeys = listOf(trip))
    private fun at(t: String) = Instant.parse("2026-11-13T$t:00+05:30")

    @Test fun notOpenBeforeTheOpeningMinute() {
        assertIs<WidgetFocus.OpensSoon>(widgetFocus(planner, "2026-11-13", at("00:50")))
        assertIs<DoNext.OpensSoon>(doNext(planner, "2026-11-13", now = at("00:50")).single())
    }

    @Test fun countdownInTheLastHour() {
        val f = widgetFocus(planner, "2026-11-13", at("07:20"))
        assertIs<WidgetFocus.Opening>(f); assertEquals(at("08:00"), f.at)
    }

    @Test fun openFromTheOpeningMinute() {
        assertIs<WidgetFocus.Book>(widgetFocus(planner, "2026-11-13", at("08:00")))
        assertIs<DoNext.BookNow>(doNext(planner, "2026-11-13", now = at("08:01")).single())
    }

    @Test fun allSetWhenNothingIsClose() {
        val f = widgetFocus(planner, "2026-11-01", Instant.parse("2026-11-01T09:00:00+05:30"))
        assertIs<WidgetFocus.AllSet>(f); assertEquals("2026-11-13", f.nextBookingDay)
    }
}
