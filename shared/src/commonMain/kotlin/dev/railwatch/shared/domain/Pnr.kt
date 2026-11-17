package dev.railwatch.shared.domain

import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** PNR status from Indian Railways' enquiry (ported from the web's pnr-status.ts). Fare is never read. */
class PnrLookupFailed : Exception("PNR_LOOKUP_FAILED")

private fun JsonElement?.text(max: Int = 100): String = (this as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull?.toDoubleOrNull() != null }?.contentOrNull?.trim()?.take(max) ?: ""
private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

/** Reads the enquiry's JSON (pnrNumber/passengerList, or PNR/passengers) into a snapshot; throws if it isn't one. */
fun parsePnrStatus(data: JsonObject, pnr: String, now: Instant): PnrSnapshot {
    val number = (data["PNR"] ?: data["pnrNumber"] ?: data["pnrNo"]).text().replace(Regex("""\D"""), "")
    if (data["errorMessage"].text().isNotEmpty() || (number.isNotEmpty() && number != pnr)) throw PnrLookupFailed()
    val list = (data["passengers"] ?: data["passengerList"] ?: data["passengerDetails"]) as? JsonArray ?: JsonArray(emptyList())
    val other = data["other_info"]
    val info = if (other is JsonArray) JsonObject(other.flatMap { it.obj().entries }.associate { it.key to it.value }) else other.obj()
    val chartBool = (data["chartPrepared"] as? JsonPrimitive)?.booleanOrNull
    val chart = (info["chartStatus"] ?: data["chartStatus"]).text().ifEmpty { chartBool?.let { if (it) "Chart Prepared" else "Chart Not Prepared" } ?: "" }
    val passengers = list.mapIndexed { i, raw ->
        val p = raw.obj()
        val current = (p["currentStatusDetails"] ?: p["currentStatus"]).text()
        if (current.isEmpty()) throw PnrLookupFailed()
        PnrPassenger((p["passengerSerialNumber"].text().toIntOrNull() ?: (i + 1)).coerceIn(1, 100), (p["bookingStatusDetails"] ?: p["bookingStatus"]).text(), current,
            (p["currentCoachId"] ?: p["coach"]).text(20), (p["currentBerthNo"] ?: p["berth"]).text(30))
    }
    if (passengers.isEmpty() || passengers.size > 100) throw PnrLookupFailed()
    return PnrSnapshot(pnr, now.toString(), chart, passengers)
}

/** Conservative summary: a mixed PNR never shows as fully confirmed. */
fun pnrSummary(s: PnrSnapshot): String? {
    val v = s.passengers.map { it.current.uppercase() }
    return when {
        v.isNotEmpty() && v.all { Regex("^(CAN|CANCELLED|CANCELED)$").matches(it.trim()) } -> "CANCELLED"
        v.any { Regex("WL|WAIT").containsMatchIn(it) } -> "WL"
        v.any { it.contains("RAC") } -> "RAC"
        v.all { Regex("""^(CNF|CONFIRMED)(\b|[-/])""").containsMatchIn(it) } -> "CNF"
        else -> null
    }
}

/** Whether one passenger's current status is cancelled. */
fun isCancelled(p: PnrPassenger) = Regex("^(CAN|CANCELLED|CANCELED)\\b", RegexOption.IGNORE_CASE).containsMatchIn(p.current.trim())

/** "1 of 3 cancelled" when only some passengers are cancelled; null otherwise. */
fun partlyCancelled(s: PnrSnapshot): String? {
    val n = s.passengers.count(::isCancelled)
    return if (n in 1 until s.passengers.size) "$n of ${s.passengers.size} cancelled" else null
}

/** The status to show: a live check for this PNR first, then what the ticket said. */
fun effectiveTicketStatus(j: Journey): String? {
    j.pnrStatus?.let { return if (it.pnr == j.pnr) pnrSummary(it) else j.ticketStatus }
    j.currentStatus?.let { c ->
        if (Regex("WL|WAIT", RegexOption.IGNORE_CASE).containsMatchIn(c)) return "WL"
        if (c.contains("RAC", true)) return "RAC"
        if (Regex("^(CNF|CONFIRMED)", RegexOption.IGNORE_CASE).containsMatchIn(c)) return "CNF"
    }
    return j.ticketStatus
}

/** Records a check and its history. A cancelled PNR never cancels the journey by itself: the person confirms it. */
fun applyPnrStatus(j: Journey, s: PnrSnapshot): Journey {
    require(j.pnr == s.pnr) { "PNR_CHANGED" }
    val summary = pnrSummary(s)
    val previous = if (j.pnrStatus?.pnr == j.pnr) pnrSummary(j.pnrStatus!!) else j.currentStatus ?: j.ticketStatus
    val detail = pnrChange(j.pnrStatus?.takeIf { it.pnr == j.pnr }, s)
    // Logged when the summary changes (WL → RAC) or a position moves within it (WL 12 → WL 5).
    val log = if (summary != null && (summary != previous || detail != null)) ((j.statusLog ?: emptyList()) + StatusEntry(s.checkedAt, summary, detail)).takeLast(40) else j.statusLog
    // Passengers who are still travelling decide the summary when only some are cancelled.
    val live = s.copy(passengers = s.passengers.filterNot(::isCancelled)).takeIf { partlyCancelled(s) != null }?.let(::pnrSummary)
    val shown = partlyCancelled(s)?.let { p -> listOfNotNull(live, p).joinToString(" · ") } ?: summary ?: "See passengers"
    return j.copy(pnrStatus = s, pnrLastAttemptAt = s.checkedAt, pnrLastError = null, currentStatus = shown, statusLog = log)
}

/** Suggest a check (never run one): booked, has a PNR, travels within 3 days, and is WL/RAC or not checked in 12 hours. */
fun suggestPnrCheck(j: Journey, today: String, now: Instant): Boolean {
    if (j.pnr.isEmpty() || j.status != JourneyStatus.Booked) return false
    val days = daysBetween(today, j.date)
    if (days < 0 || days > 3) return false
    val snap = j.pnrStatus?.takeIf { it.pnr == j.pnr }
    val summary = snap?.let(::pnrSummary) ?: effectiveTicketStatus(j)
    if (summary == "CANCELLED") return false
    return summary == "WL" || summary == "RAC" || snap == null || runCatching { now - Instant.parse(snap.checkedAt) > kotlin.time.Duration.parse("12h") }.getOrDefault(true)
}

/** "WL/12" or "RLWL 12" → "WL 12": the readable form of one passenger's status. */
fun readableStatus(s: String): String = s.trim().replace(Regex("""\s*/\s*"""), " ").replace(Regex("""\s+"""), " ")

/**
 * What moved since the last check, e.g. "WL 12 → RAC 3", or "P1 WL 12 → RAC 3 · P2 WL 13 → RAC 4" for several
 * passengers. Without an earlier check the booking status is the starting point. Null when nothing moved.
 */
fun pnrChange(before: PnrSnapshot?, after: PnrSnapshot): String? {
    val moved = after.passengers.mapNotNull { p ->
        val was = readableStatus(before?.passengers?.firstOrNull { it.number == p.number }?.current ?: p.booking)
        val now = readableStatus(p.current)
        if (was.isEmpty() || was.equals(now, ignoreCase = true)) null else Triple(p.number, was, now)
    }
    if (moved.isEmpty()) return null
    return if (after.passengers.size == 1) moved.single().let { "${it.second} → ${it.third}" }
    else moved.joinToString(" · ") { "P${it.first} ${it.second} → ${it.third}" }
}

/**
 * When Indian Railways usually prepares the first reservation chart (rule since July 2025): 8 hours before
 * departure, or at 21:00 the evening before for trains leaving between 05:01 and 14:00. Worked out from the
 * boarding time, so for a train that started earlier upstream the chart can come a little sooner.
 */
fun chartTime(j: Journey): Instant? = runCatching {
    val (h, m) = j.departure.split(":").map { it.trim().toInt() }
    val minutes = h * 60 + m
    if (minutes in 301..840) Instant.parse("${addDays(j.date, -1)}T21:00:00+05:30")
    else Instant.parse("${j.date}T${j.departure.trim()}:00+05:30") - kotlin.time.Duration.parse("8h")
}.getOrNull()
