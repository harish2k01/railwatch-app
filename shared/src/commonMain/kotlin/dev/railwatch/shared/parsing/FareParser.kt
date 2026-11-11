package dev.railwatch.shared.parsing

import dev.railwatch.shared.domain.Fare
import kotlin.math.abs

/** Fare reading, ported from the web app's src/lib/ticket-fare.ts (checked by the parity fixtures). */
object FareParser {
    private fun near(a: Double, b: Double) = abs(a - b) < 0.02

    /** Parses an amount; a scanned "2045" (dot lost) is 20.45. */
    private fun money(raw: String): Double { val s = raw.replace(",", ""); return (if (s.contains('.')) s else s.dropLast(2) + "." + s.takeLast(2)).toDouble() }

    /** The amount as read, and with its first digit dropped (OCR reads ₹ as a leading digit). */
    private fun readings(raw: String): List<Double> {
        val out = listOf(money(raw)); val rest = raw.replaceFirst(Regex("""^\d,?"""), "")
        if (rest == raw || !rest.first().isDigit()) return out
        // IRCTC writes ₹1,000 and up with a comma, so four or more digits with no comma start with the misread ₹.
        if (!raw.contains(',') && Regex("""^\d{4,}\.""").containsMatchIn(raw)) return listOf(money(rest))
        return out + money(rest)
    }

    /** True when the parts add up to the total (a fare without a ticket part can't be checked). */
    fun addsUp(f: Fare?): Boolean {
        if (f == null || f.total == 0.0 || f.ticket == null) return false
        return near((f.ticket) + (f.convenience ?: 0.0) + (f.wallet ?: 0.0) + (f.insurance ?: 0.0), f.total)
    }

    fun parse(raw: String): Fare? {
        val text = raw.replace("\r", "")
        val amount = """(?:Rs\.?|INR|₹)?\s*[X$%]?\s*(\d[\d,]*\.?\d{2})(?![\d])"""
        fun pick(label: String) = Regex(label + """[^\n\d]*?""" + amount, RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val found = mutableMapOf(
            "ticket" to pick("""Ticket\W{1,4}Fare"""), "convenience" to pick("""Convenience\s+Fee(?:\s*\(Incl\.?\s*of\s*GST\))?"""),
            "wallet" to pick("""(?:e-?Wallet|Payment\s+Gateway)\s+(?:Transaction\s+)?Charges?"""), "insurance" to pick("""Travel\s+Insurance\s+Premium(?:\s*\(Incl\.?\s*of\s*GST\))?"""),
            "total" to pick("""Total\s+Fare(?:\s*\(all\W+inclusive\))?"""),
        )
        // Email layout: labels on one row, "Rs. …" values on the next, in the same order.
        if (found["total"] == null) {
            val row = Regex("""Ticket\s+Fare[^\n]*Total\s+Fare[^\n]*\n([^\n]*)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            val values = row?.let { r -> Regex("""(?:Rs\.?|₹)\s*([\d,]+\.\d{2})""").findAll(r).map { it.groupValues[1] }.toList() }.orEmpty()
            if (values.size >= 2) { found.clear(); found["ticket"] = values[0]; found["convenience"] = values.getOrNull(1).takeIf { values.size > 2 }; found["wallet"] = values.getOrNull(2).takeIf { values.size > 3 }; found["insurance"] = values.getOrNull(3).takeIf { values.size > 4 }; found["total"] = values.last() }
        }
        val totalRaw = found["total"] ?: return null
        val keys = listOf("total", "ticket", "convenience", "wallet", "insurance").filter { found[it] != null }
        val options = keys.map { readings(found[it]!!) }
        var best: Pair<Map<String, Double>, Int>? = null
        fun walk(i: Int, fare: Map<String, Double>, fixes: Int) {
            if (i == keys.size) {
                val f = fare.toFare()
                if (f.total > 0 && addsUp(f) && (best == null || fixes < best!!.second)) best = fare to fixes
                return
            }
            options[i].forEachIndexed { n, v -> walk(i + 1, fare + (keys[i] to v), fixes + n) }
        }
        walk(0, emptyMap(), 0)
        best?.let { return it.first.toFare() }
        // Nothing adds up: keep the total (and the ticket fare if plausible), as read.
        val total = money(totalRaw); if (total <= 0) return null
        val ticket = found["ticket"]?.let { readings(it).firstOrNull { v -> v <= total } }
        return Fare(total = total, ticket = ticket)
    }

    private fun Map<String, Double>.toFare() = Fare(total = this["total"] ?: 0.0, ticket = this["ticket"], convenience = this["convenience"], wallet = this["wallet"], insurance = this["insurance"])
}
