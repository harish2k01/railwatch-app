package dev.railwatch.app.data

import android.net.Uri
import dev.railwatch.shared.domain.Attachment
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.parsing.TicketDetails
import dev.railwatch.shared.parsing.TicketMatch
import dev.railwatch.shared.parsing.matchTicket

/** One shared PDF being imported: what was read and which journey it fits. */
data class ImportRow(val file: Attachment, val details: TicketDetails, val match: TicketMatch, val choice: String?) {
    /** The journey to attach to, "create" for a new booked journey, or null to leave it out. */
    val ready get() = choice != null
}

/** Reads shared PDFs one by one and proposes a journey for each. */
suspend fun readShared(uris: List<Uri>, files: TicketFiles, reader: TicketReader, journeys: () -> List<Journey>, progress: (Int, Int) -> Unit): Pair<List<ImportRow>, Int> {
    val rows = mutableListOf<ImportRow>(); var rejected = 0
    for ((i, uri) in uris.take(30).withIndex()) {
        progress(i + 1, uris.size)
        val file = runCatching { files.save(uri) }.getOrNull()
        if (file == null) { rejected++; continue }
        val details = runCatching { reader.read(file.id) }.getOrDefault(TicketDetails())
        val match = matchTicket(details, journeys())
        rows += ImportRow(file, details, match, when (match) { is TicketMatch.Matched -> match.journey.id; TicketMatch.Create -> if (details.date != null && details.from != null && details.to != null) "create" else null; is TicketMatch.Check -> null })
    }
    return rows to rejected
}
