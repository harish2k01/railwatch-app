package dev.railwatch.shared.parsing

import dev.railwatch.shared.domain.Fare
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a ticket says. All optional: a field is set only when it was read with confidence. */
@Serializable
data class TicketDetails(
    val from: String? = null, val to: String? = null, val date: String? = null, val trainNumber: String? = null, val trainName: String? = null,
    val travelClass: String? = null, val pnr: String? = null, val coach: String? = null, val seat: String? = null, val berth: String? = null,
    val departure: String? = null, val arrival: String? = null, val arrivalDate: String? = null, val ticketStatus: String? = null,
    val bookingType: String? = null, val fare: Fare? = null,
)

/** Ticket reading from QR JSON, PDF text or OCR, ported from the web app's src/lib/ticket-details.ts. */
object TicketParser {
    private const val I = "(?i)"
    private val BERTHS = """SIDE\s+LOWER|SIDE\s+UPPER|LOWER|MIDDLE|UPPER|WINDOW\s+SIDE|AISLE|LB|MB|UB|SL|SU|WS|CHAIR"""
    private val SEAT = Regex("""(?:CNF|CONFIRMED)\s*[,/ ]+\s*([A-Z]{1,2}\d{1,2})\s*[,/ ]+\s*(\d{1,3})\s*[,/ ]+\s*($BERTHS)\b""", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun isDay(v: String) = runCatching { LocalDate.parse(v).toString() == v }.getOrDefault(false)
    private fun Regex.first(text: String) = find(text)?.groupValues?.get(1)?.trim()
    private fun rx(p: String) = Regex(I + p)

    fun parse(raw: String): TicketDetails {
        val text = raw.replace("\r", "")
            .replace(rx("""[,;]\s*(?=(?:PNR|TXN\s*ID|Passenger\s*Name|Gender|Age|Status|Quota|Train\s*(?:No|Number|Name)|Date\s*Of\s*Journey|Boarding\s*Station|From|To|Class|Journey\s*Date|Ticket\s*Fare|Departure|Coach|Seat|Berth)[^:=\n,]{0,4}[:=])"""), "\n")
            .replace(Regex("""\b(TrainNo|TrainName|JourneyDate|DepartureTime|CoachNo|SeatNo|BerthType)\b""")) { m -> m.value.replace(Regex("([a-z])([A-Z])"), "$1 $2") }
            .replace(rx("""\b(From|To|Class|Coach|Seat No|Berth Type|Train No|Train Name|Journey Date|Departure Time)\s*=""")) { m -> m.groupValues[1] + ":" }
        val d = mutableMapOf<String, String?>()
        val json = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        if (json != null) {
            val normalized = mutableMapOf<String, String>()
            fun norm(k: String) = k.lowercase().replace(Regex("[^a-z0-9]"), "")
            fun flatten(o: JsonObject, depth: Int = 0) {
                if (depth > 8) return
                for ((k, v) in o) when {
                    v is JsonPrimitive && (v.isString || v.content.toDoubleOrNull() != null) -> normalized[norm(k)] = v.content
                    v is JsonObject -> flatten(v, depth + 1)
                    v is JsonArray && v.firstOrNull() is JsonObject -> flatten(v.first() as JsonObject, depth + 1)
                }
            }
            flatten(json)
            val aliases = mapOf(
                "from" to listOf("from", "source", "boardingStation"), "to" to listOf("to", "destination"), "date" to listOf("date", "journeyDate", "travelDate"),
                "trainNumber" to listOf("trainNumber", "trainNo", "train_no"), "trainName" to listOf("trainName", "train_name"), "travelClass" to listOf("travelClass", "class"),
                "pnr" to listOf("pnr", "PNR", "pnrNumber", "pnrNo"), "coach" to listOf("coach", "coachNumber", "coachNo"), "seat" to listOf("seat", "seatNumber", "seatNo", "berthNumber", "berthNo"),
                "berth" to listOf("berth", "berthType"), "departure" to listOf("departure", "departureTime", "scheduledDeparture", "boardingTime"),
            )
            for ((field, keys) in aliases) keys.firstNotNullOfOrNull { normalized[norm(it)] }?.let { d[field] = it.trim() }
        } else {
            fun m(p: String) = rx(p).first(text)
            d["pnr"] = m("""\bPNR(?:\s*(?:No\.?|Number))?\s*[:#\-]?\s*(\d{10})\b""")
            d["trainNumber"] = m("""\bTrain\s*(?:No\.?|Number)(?:\s*/\s*Name)?\s*[:#\-]?\s*(\d{5})\b""")
            d["trainName"] = m("""\bTrain\s*Name\s*[:\-]\s*([^\n]+)""") ?: m("""\bTrain\s*(?:No\.?|Number)\s*/\s*Name\s*[:\-]?\s*\d{5}\s*/\s*([^\n]+)""")
            d["travelClass"] = (m("""\bClass\s*[:\-]?\s*(1A|2A|3A|3E|SL|CC|EC|2S)\b""") ?: m("""\bClass\s*[:\-]?[^\n]*\n?[^\n]*?\((1A|2A|3A|3E|SL|CC|EC|2S)\)"""))?.uppercase()
            d["date"] = m("""\b(?:Date of Journey|Journey Date|Travel Date|Boarding Date)\s*\*?\s*[:\-]?\s*(\d{4}-\d{2}-\d{2}|\d{2}[-/]\d{2}[-/]\d{4})""")
            d["from"] = m("""(?:^|\n)\s*(?:Boarding At|Boarding Station)\s*[:\-]?\s*\n?\s*([^\n]+)""") ?: m("""(?:^|\n)\s*(?:Booked From|From)\s*[:\-]?\s*\n?\s*([^\n]+)""")
            d["to"] = m("""(?:^|\n)\s*(?:To|Destination|Reservation Upto)\s*[:\-]?\s*\n?\s*([^\n]+)""")
            d["coach"] = m("""\bCoach(?:\s*(?:No\.?|Number))?\s*[:\-]\s*([A-Z0-9-]{1,10})\b""")
            d["seat"] = m("""\b(?:Seat|Berth)(?:\s*(?:No\.?|Number))\s*[:\-]\s*(\d{1,3}(?:\s*[,/]\s*\d{1,3})*)""")
            d["berth"] = m("""\bBerth(?:\s*Type)?\s*[:\-]\s*(Lower|Middle|Upper|Side Lower|Side Upper|LB|MB|UB|SL|SU|Chair)\b""")
            d["departure"] = m("""\b(?:Departure(?: Time)?|Boarding Time)\s*[:\-]\s*((?:[01]\d|2[0-3]):[0-5]\d)""")
            // IRCTC ERS prints a row of labels, then a row of values in the same columns.
            fun valuesUnder(label: String) = rx(label + """[^\n]*\n([^\n]+)""").find(text)?.groupValues?.get(1)
            val stationRow = valuesUnder("""Booked\s+From""")?.let { r -> Regex("""\(([A-Z]{2,5})\)""").findAll(r).map { it.groupValues[1] }.toList() }
            if (stationRow != null && stationRow.size >= 2) { d["from"] = if (stationRow.size >= 3) stationRow[1] else stationRow[0]; d["to"] = stationRow.last() }
            valuesUnder("""\bPNR\b""")?.let { idRow ->
                if (d["pnr"] == null) d["pnr"] = Regex("""\b(\d{10})\b""").first(idRow)
                rx("""\b(\d{5})\s*/\s*(.+?)(?=\s+(?:SLEEPER|AC\b|FIRST|SECOND|THIRD|EXECUTIVE|CHAIR|GENERAL)\b|\s*\(|$)""").find(idRow)?.let { t ->
                    if (d["trainNumber"] == null) d["trainNumber"] = t.groupValues[1]; if (d["trainName"] == null) d["trainName"] = t.groupValues[2].trim()
                }
            }
            // OCR can misread a PNR digit: the label, the table row under "PNR" and the GST invoice number all vote.
            val votes = rx("""\bPNR(?:\s*(?:No\.?|Number))?\s*[:#\-]?\s*(\d{10})\b""").findAll(text).map { it.groupValues[1] } +
                rx("""\bPNR\b[^\n]*\n[^\n]*?\b(\d{10})\b""").findAll(text).map { it.groupValues[1] } +
                rx("""\bInvoice\s*(?:Number|No\.?)\s*[:\-]?\s*[A-Z]{2}\d{2}(\d{10})\d{1,4}\b""").findAll(text).map { it.groupValues[1] }
            votes.toList().takeIf { it.isNotEmpty() }?.let { v -> d["pnr"] = v.groupingBy { it }.eachCount().entries.maxByOrNull { it.value }!!.key }
            if (d["trainNumber"] == null || d["trainName"] == null) {
                Regex("""(?:^|[\s:])(\d{5})\s*/\s*([A-Z][A-Z0-9 .()'-]{2,60}?)(?=\s{2,}|\s+(?:SLEEPER|AC\b|FIRST|SECOND|THIRD|EXECUTIVE|CHAIR|GENERAL)\b|\s*\n|\s*$)""", RegexOption.MULTILINE).find(text)?.let { l ->
                    if (d["trainNumber"] == null) d["trainNumber"] = l.groupValues[1]; if (d["trainName"] == null) d["trainName"] = l.groupValues[2].trim()
                }
            }
            SEAT.find(text)?.let { a -> d.putIfAbsentOrNull("coach", a.groupValues[1]); d.putIfAbsentOrNull("seat", a.groupValues[2]); d.putIfAbsentOrNull("berth", a.groupValues[3]) }
        }
        val allocationSource = json?.toString() ?: text
        SEAT.find(allocationSource)?.let { a -> d.putIfAbsentOrNull("coach", a.groupValues[1]); d.putIfAbsentOrNull("seat", a.groupValues[2]); d.putIfAbsentOrNull("berth", a.groupValues[3]) }
        rx("""current[ _]*status["\s]*[:=]["\s]*((?:CNF|CONFIRMED)[^"\n]+)""").first(allocationSource)?.let { cs ->
            SEAT.find(cs)?.let { c -> d["coach"] = c.groupValues[1]; d["seat"] = c.groupValues[2]; d["berth"] = c.groupValues[3] }
        }
        rx("""(?:Booking Status|Current Status)[^\n]*\n[^\n]*?\b([A-Z]{1,2}\d{1,2})\s+(\d{1,3})\s+(SIDE\s+LOWER|SIDE\s+UPPER|LOWER|MIDDLE|UPPER|WINDOW\s+SIDE|AISLE|LB|MB|UB|SL|SU|WS)\b""").find(text)?.let { p ->
            d.putIfAbsentOrNull("coach", p.groupValues[1]); d.putIfAbsentOrNull("seat", p.groupValues[2]); d.putIfAbsentOrNull("berth", p.groupValues[3])
        }
        // Every passenger row: each row's current (last) allocation; OCR can repeat a row, keep one per passenger number.
        val byPassenger = linkedMapOf<String, MatchResult>()
        for (row in Regex("""^\s*(\d+)\.\s[^\n]*$""", RegexOption.MULTILINE).findAll(text)) {
            val last = SEAT.findAll(row.value).lastOrNull(); if (last != null && row.groupValues[1] !in byPassenger) byPassenger[row.groupValues[1]] = last
        }
        val seats = byPassenger.values.toList()
        if (seats.size > 1) {
            d["coach"] = seats.map { it.groupValues[1].uppercase() }.distinct().joinToString(", ").take(20)
            d["seat"] = seats.joinToString(", ") { it.groupValues[2] }.take(30)
            d["berth"] = seats.joinToString(", ") { it.groupValues[3].uppercase().replace(Regex("""\s+"""), " ") }.take(30)
        }
        if (d["departure"] == null) rx("""(?:Scheduled Departure|Departure(?: Time)?|Boarding Time)\s*\*?\s*[:\-]?\s*(?:\d{2}[-/]\w{2,9}[-/]\d{4}\s+)?((?:[01]?\d|2[0-3]):[0-5]\d)(?:\s*(AM|PM))?""").find(text)?.let { g ->
            d["departure"] = listOf(g.groupValues[1], g.groupValues[2]).filter { it.isNotEmpty() }.joinToString(" ")
        }
        d["departure"]?.let { dep -> rx("""^(\d{1,2}):([0-5]\d)(?:\s*(AM|PM))?$""").find(dep)?.let { t ->
            var hour = t.groupValues[1].toInt(); val ap = t.groupValues[3]
            if (ap.isNotEmpty() && hour in 1..12) hour = hour % 12 + if (ap.uppercase() == "PM") 12 else 0
            d["departure"] = hour.toString().padStart(2, '0') + ":" + t.groupValues[2]
        } }
        if (d["date"] == null) d["date"] = rx("""\bDate\s*Of\s*Journey\s*[:\-]?\s*(\d{2}-[A-Za-z]{3}-\d{4})""").first(text)
        if (d["date"] == null) d["date"] = rx("""\bStart Date\s*\*?\s*[:\-]?\s*(\d{2}-[A-Za-z]{3}-\d{4})""").first(text)
        d["date"]?.let { v -> if (Regex("""^\d{2}-[A-Za-z]{3}-\d{4}$""").matches(v)) { val (day, mon, yr) = v.split("-"); d["date"] = "$yr-${(MONTHS.indexOf(mon.lowercase()) + 1).toString().padStart(2, '0')}-$day" } }
        d["date"]?.let { v -> if (Regex("""^\d{2}[-/]\d{2}[-/]\d{4}$""").matches(v)) { val (day, mon, yr) = v.split(Regex("[-/]")); d["date"] = "$yr-$mon-$day" } }
        for (k in listOf("from", "to", "trainName")) d[k]?.let { d[k] = it.replace(Regex("""[,;]\s*$"""), "").trim() }
        // QR stations read "CHENNAI EGMORE - MS"; tickets print "MADURAI JN (MDU)": keep the code.
        for (k in listOf("from", "to")) d[k]?.let { v -> Regex("""\s[-–]\s*([A-Z]{1,5})$""").first(v)?.let { d[k] = it } }
        for (k in listOf("from", "to")) d[k]?.let { v -> Regex("""\(([A-Z]{2,5})\)""").first(v)?.let { d[k] = it } }
        if (rx("""\bQuota\s*[:\-]?\s*\n?\s*(?:PREMIUM\s+)?TATKAL\b|\((?:TQ|PT)\)""").containsMatchIn(json?.toString() ?: text)) d["bookingType"] = "tatkal"
        val statusText = rx("""current[ _]*status["\s]*[:=]["\s]*([A-Z]+)""").first(allocationSource)
            ?: rx("""(?:Current Status|Booking Status)[^\n]*\n[^\n]*?\b(CNF|CONFIRMED|RAC|RLWL|PQWL|GNWL|TQWL|WL)\b""").first(text)
            ?: rx("""\b(RAC|RLWL|PQWL|GNWL|TQWL|WL|CNF|CONFIRMED)\s*[/ ]*\d*""").first(allocationSource)
        statusText?.uppercase()?.let { v -> d["ticketStatus"] = when { v == "RAC" -> "RAC"; v.endsWith("WL") -> "WL"; v == "CNF" || v == "CONFIRMED" -> "CNF"; else -> null } }
        if (d["date"]?.let(::isDay) == false) d["date"] = null
        if (d["pnr"]?.matches(Regex("""\d{10}""")) == false) d["pnr"] = null
        if (d["trainNumber"]?.matches(Regex("""\d{5}""")) == false) d["trainNumber"] = null
        if (d["departure"]?.matches(Regex("""([01]\d|2[0-3]):[0-5]\d""")) == false) d["departure"] = null
        // ERS: "Arrival* 07:10 18-Nov-2026" (the arrival day may be the next day).
        rx("""Arrival\s*\*?\s*[:\-]?\s*((?:[01]\d|2[0-3]):[0-5]\d)\s+(\d{1,2})[-/ ]([A-Za-z]{3})[A-Za-z]*[-/ ](\d{4})""").find(text)?.let { a ->
            val month = MONTHS.indexOf(a.groupValues[3].lowercase()) + 1
            val day = "${a.groupValues[4]}-${month.toString().padStart(2, '0')}-${a.groupValues[2].padStart(2, '0')}"
            if (month >= 1 && isDay(day)) { d["arrival"] = a.groupValues[1]; d["arrivalDate"] = day }
        }
        fun v(k: String) = d[k]?.takeIf { it.isNotEmpty() }
        return TicketDetails(from = v("from"), to = v("to"), date = v("date"), trainNumber = v("trainNumber"), trainName = v("trainName"), travelClass = v("travelClass"),
            pnr = v("pnr"), coach = v("coach"), seat = v("seat"), berth = v("berth"), departure = v("departure"), arrival = v("arrival"), arrivalDate = v("arrivalDate"),
            ticketStatus = v("ticketStatus"), bookingType = v("bookingType"), fare = FareParser.parse(text))
    }

    private fun MutableMap<String, String?>.putIfAbsentOrNull(k: String, v: String) { if (this[k] == null) this[k] = v }
}

/** How a shared ticket relates to the planner's journeys. */
sealed interface TicketMatch {
    data class Matched(val journey: Journey) : TicketMatch
    data class Check(val candidates: List<Journey>, val reason: String) : TicketMatch
    data object Create : TicketMatch
}

/** Matches ticket details to journeys on the same travel date (ported from src/lib/ticket-match.ts). */
fun matchTicket(d: TicketDetails, journeys: List<Journey>): TicketMatch {
    val date = d.date ?: return TicketMatch.Check(emptyList(), "No travel date was found on this ticket.")
    fun norm(v: String?) = (v ?: "").trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    fun same(ticket: String?, journey: String): Boolean { val a = norm(ticket); val b = norm(journey); return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.startsWith(b) || b.startsWith(a)) }
    val sameDay = journeys.filter { it.date == date && it.status !in setOf(JourneyStatus.Cancelled, JourneyStatus.Skipped, JourneyStatus.Completed) }
    val byPnr = if (d.pnr != null) sameDay.filter { it.pnr == d.pnr } else emptyList()
    if (byPnr.size == 1) return TicketMatch.Matched(byPnr[0])
    val byRoute = sameDay.filter { same(d.from, it.from) && same(d.to, it.to) }
    if (byRoute.size == 1) return TicketMatch.Matched(byRoute[0])
    if (byRoute.size > 1) return TicketMatch.Check(byRoute, "More than one journey on this date has this route.")
    if (sameDay.isNotEmpty()) return TicketMatch.Check(sameDay, "A journey on this date has a different route.")
    return TicketMatch.Create
}
