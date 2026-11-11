package dev.railwatch.shared.parsing

import dev.railwatch.shared.domain.Fare
import dev.railwatch.shared.domain.Journey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks ticket reading against the web app's results on the same synthetic inputs (fixtures/rules.json). */
class ParsingParityTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val fixtures: JsonObject = json.parseToJsonElement(File("../fixtures/rules.json").readText()).jsonObject

    private fun fields(d: TicketDetails): Map<String, String> = listOfNotNull(
        d.from?.let { "from" to it }, d.to?.let { "to" to it }, d.date?.let { "date" to it }, d.trainNumber?.let { "trainNumber" to it }, d.trainName?.let { "trainName" to it },
        d.travelClass?.let { "travelClass" to it }, d.pnr?.let { "pnr" to it }, d.coach?.let { "coach" to it }, d.seat?.let { "seat" to it }, d.berth?.let { "berth" to it },
        d.departure?.let { "departure" to it }, d.arrival?.let { "arrival" to it }, d.arrivalDate?.let { "arrivalDate" to it }, d.ticketStatus?.let { "ticketStatus" to it },
        d.bookingType?.let { "bookingType" to it },
    ).toMap()

    @Test fun parse() {
        for (case in fixtures.getValue("parse").jsonArray) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val want = case.jsonObject.getValue("expected").jsonObject
            val got = TicketParser.parse(input)
            val wantFields = want.filterKeys { it != "fare" }.mapValues { it.value.jsonPrimitive.content }
            assertEquals(wantFields, fields(got), "fields for: ${input.take(80)}")
            val wantFare = want["fare"]?.takeIf { it !is JsonNull }?.jsonObject?.let { f -> Fare(f.getValue("total").jsonPrimitive.double, f["ticket"]?.jsonPrimitive?.double, f["convenience"]?.jsonPrimitive?.double, f["wallet"]?.jsonPrimitive?.double, f["insurance"]?.jsonPrimitive?.double) }
            assertEquals(wantFare, got.fare, "fare for: ${input.take(80)}")
        }
    }

    @Test fun rows() {
        for (case in fixtures.getValue("pdfText").jsonArray) {
            val items = case.jsonObject.getValue("items").jsonArray.map { i -> i.jsonObject.let { TextItem(it.getValue("str").jsonPrimitive.content, it.getValue("x").jsonPrimitive.double, it.getValue("y").jsonPrimitive.double) } }
            assertEquals(case.jsonObject.getValue("expected").jsonPrimitive.content, TicketText.rows(items))
        }
    }

    @Test fun match() {
        val pool: List<Journey> = json.decodeFromJsonElement(fixtures.getValue("journeys"))
        for (case in fixtures.getValue("match").jsonArray) {
            val d: TicketDetails = json.decodeFromJsonElement(case.jsonObject.getValue("details"))
            val want = case.jsonObject.getValue("expected").jsonObject
            val got = matchTicket(d, pool)
            val kind = when (got) { is TicketMatch.Matched -> "matched"; is TicketMatch.Check -> "check"; TicketMatch.Create -> "create" }
            assertEquals(want.getValue("kind").jsonPrimitive.content, kind, d.toString())
            assertEquals(want["journey"]?.jsonPrimitive?.contentOrNull, (got as? TicketMatch.Matched)?.journey?.id, d.toString())
            assertEquals(want.getValue("candidates").jsonArray.map { it.jsonPrimitive.content }, (got as? TicketMatch.Check)?.candidates?.map { it.id }.orEmpty(), d.toString())
        }
    }
}
