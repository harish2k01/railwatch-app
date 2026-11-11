package dev.railwatch.shared.parsing

import kotlin.math.abs

/**
 * Rebuilds visible rows from positioned text (PDF text items, or OCR words) and pairs each header with the value
 * under it, ported from the web app's src/lib/ticket-pdf-text.ts. y grows upwards (PDF space); pass OCR words
 * with y negated.
 */
data class TextItem(val str: String, val x: Double, val y: Double)

object TicketText {
    private val LABELS = mapOf(
        "bookedfrom" to "From", "boardingat" to "Boarding Station", "currentstatus" to "Current Status", "pnr" to "PNR", "pnrnumber" to "PNR", "pnrno" to "PNR",
        "trainnoname" to "Train No./Name", "trainnumbername" to "Train No./Name", "trainname" to "Train Name", "trainno" to "Train No", "class" to "Class",
        "dateofjourney" to "Date of Journey", "journeydate" to "Journey Date", "boardingdate" to "Boarding Date", "from" to "From", "to" to "To",
        "boardingstation" to "Boarding Station", "scheduleddeparture" to "Scheduled Departure", "departuretime" to "Departure Time", "boardingtime" to "Boarding Time",
        "coach" to "Coach", "coachno" to "Coach", "coachnumber" to "Coach", "seatno" to "Seat No", "seatnumber" to "Seat No", "berthtype" to "Berth",
    )
    private fun key(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun rows(items: List<TextItem>): String {
        data class Line(val y: Double, val items: MutableList<Pair<Double, String>>)
        val lines = mutableListOf<Line>()
        for (item in items) {
            val line = lines.firstOrNull { abs(it.y - item.y) <= 3 } ?: Line(item.y, mutableListOf()).also { lines += it }
            line.items += item.x to item.str
        }
        lines.sortByDescending { it.y }; lines.forEach { l -> l.items.sortBy { it.first } }
        val fields = mutableListOf<String>()
        for ((index, line) in lines.withIndex()) {
            val headers = mutableListOf<Pair<Double, String>>()
            var start = 0
            while (start < line.items.size) {
                var found: Pair<String, Int>? = null
                for (end in start until minOf(start + 4, line.items.size)) {
                    if (end > start && line.items[end].first - line.items[end - 1].first > 90) break
                    LABELS[key(line.items.subList(start, end + 1).joinToString("") { it.second })]?.let { found = it to end }
                }
                val f = found
                if (f != null) { headers += line.items[start].first to f.first; start = f.second }
                start++
            }
            val next = lines.getOrNull(index + 1) ?: continue
            if (line.y - next.y > 36) continue
            for ((column, header) in headers.withIndex()) {
                val left = if (column == 0) header.first - 24 else (headers[column - 1].first + header.first) / 2
                val right = headers.getOrNull(column + 1)?.let { (header.first + it.first) / 2 } ?: Double.POSITIVE_INFINITY
                val values = next.items.filter { it.first >= left && it.first < right && LABELS[key(it.second)] == null }
                if (values.isNotEmpty()) fields += "${header.second}: ${values.joinToString(" ") { it.second }}"
            }
        }
        return (fields + lines.map { l -> l.items.joinToString(" ") { it.second } }).joinToString("\n")
    }
}
