package dev.railwatch.shared.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the Kotlin rules against cases exported from the web app (fixtures/rules.json). */
class ParityTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixtures: JsonObject = json.parseToJsonElement(File("../fixtures/rules.json").readText()).jsonObject

    private fun JsonElement.str(key: String) = jsonObject[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    private inline fun <reified T> JsonElement.decode(key: String): T = json.decodeFromJsonElement(jsonObject.getValue(key))

    @Test fun generateJourneys() {
        for (case in fixtures.getValue("generate").jsonArray) {
            val name = case.str("name")
            val got = generateJourneys(case.decode<Rule>("rule"), case.str("today")!!, case.decode<Settings>("settings"))
            val want = case.jsonObject.getValue("expected").jsonArray
            assertEquals(want.size, got.size, "$name: count")
            want.zip(got).forEach { (w, g) ->
                assertEquals(listOf(w.str("id"), w.str("date"), w.str("from"), w.str("to"), w.str("departure"), w.str("leg"), w.str("train")),
                    listOf(g.id, g.date, g.from, g.to, g.departure, g.leg, g.train), "$name: ${w.str("id")}")
            }
        }
    }

    @Test fun bookingDayAndInstant() {
        for (case in fixtures.getValue("booking").jsonArray) {
            val j = case.decode<Journey>("journey")
            val want = case.jsonObject.getValue("expected")
            assertEquals(want.str("bookingDay"), bookingDay(j), j.toString())
            assertEquals(Instant.parse(want.str("bookingInstant")!!), bookingInstant(j), j.toString())
        }
    }

    @Test fun lifecycle() {
        for (case in fixtures.getValue("lifecycle").jsonArray) {
            val name = case.str("name")
            val archive = case.str("archiveDays")?.toInt()
            val planner = Planner(journeys = json.decodeFromJsonElement(case.jsonObject.getValue("journeys")), settings = Settings(cancelledArchiveDays = archive))
            val got = reconcileLifecycle(planner, case.str("today")!!).journeys
            (case.jsonObject.getValue("expected") as JsonArray).zip(got).forEach { (w, g) ->
                assertEquals(listOf(w.str("status"), w.str("completedAt"), w.str("cancelledAt"), w.str("archivedAt")),
                    listOf(json.encodeToJsonElement(JourneyStatus.serializer(), g.status).jsonPrimitive.content, g.completedAt, g.cancelledAt, g.archivedAt), "$name: ${g.id}")
            }
        }
    }

    @Test fun saveRuleKeepsBooked() {
        val case = fixtures.getValue("saveRule")
        val got = saveRule(case.decode<Planner>("initial"), case.decode<Rule>("rule"), case.str("today")!!).journeys
        val want = case.jsonObject.getValue("expected").jsonArray
        assertEquals(want.map { it.str("id") to it.str("departure") }, got.map { it.id to it.departure })
        assertEquals(want.map { it.str("status") }, got.map { json.encodeToJsonElement(JourneyStatus.serializer(), it.status).jsonPrimitive.content })
    }

    @Test fun timing() {
        for (case in fixtures.getValue("timing").jsonArray) {
            val j = case.decode<Journey>("journey")
            assertEquals(case.str("expected"), tripTiming(j), j.toString())
        }
    }

    @Test fun repairsStationSuffix() {
        val p = Planner(journeys = listOf(Journey(id = "x", from = "MADURAI JN. - MDU", to = "CHENNAI EGMORE - MS", date = "2027-01-01", departure = "20:00")))
        assertEquals("MDU" to "MS", reconcileLifecycle(p, "2026-11-11").journeys[0].let { it.from to it.to })
    }
}
