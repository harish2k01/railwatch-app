package dev.railwatch.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IstTest {
    private fun at(iso: String) = object : Clock { override fun now() = Instant.parse(iso) }

    @Test
    fun todayFollowsIstNotUtc() {
        // 20:00 UTC is already 01:30 the next day in IST.
        assertEquals(LocalDate(2026, 11, 12), todayIst(at("2026-11-11T20:00:00Z")))
        assertEquals(LocalDate(2026, 11, 11), todayIst(at("2026-11-11T18:00:00Z")))
    }
}
