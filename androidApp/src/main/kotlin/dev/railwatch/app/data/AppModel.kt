package dev.railwatch.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.railwatch.shared.data.PlannerFiles
import dev.railwatch.shared.data.PlannerStore
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.Planner
import dev.railwatch.shared.domain.Rule
import dev.railwatch.shared.domain.removeRule
import dev.railwatch.shared.domain.saveRule
import dev.railwatch.shared.domain.transition
import dev.railwatch.shared.todayIst
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** The planner document in the app's private files, replaced atomically on every save. */
class AndroidPlannerFiles(context: Context) : PlannerFiles {
    private val file = AtomicFile(File(context.filesDir, "planner.json"))
    override fun read(): String? = runCatching { file.readFully().decodeToString() }.getOrNull()
    override fun write(text: String) {
        val out = file.startWrite()
        try { out.write(text.encodeToByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
}

/** Station codes and names from the bundled NTES-derived list. */
class Stations(context: Context) {
    private val all: List<Pair<String, String>> by lazy {
        val text = context.assets.open("stations.json").use { it.readBytes().decodeToString() }
        Json.parseToJsonElement(text).jsonArray.map { it.jsonArray.let { a -> a[0].jsonPrimitive.content to a[1].jsonPrimitive.content } }
    }
    private val byCode by lazy { all.toMap() }
    fun name(code: String): String? = byCode[code.trim().uppercase()]
    /** Code matches first (exact, then prefix), then names containing the query. */
    fun search(query: String, limit: Int = 8): List<Pair<String, String>> {
        val q = query.trim(); if (q.isEmpty()) return emptyList()
        val up = q.uppercase()
        val exact = all.filter { it.first == up }
        val code = all.filter { it.first.startsWith(up) && it.first != up }
        val named = all.filter { it.second.contains(q, ignoreCase = true) && !it.first.startsWith(up) }
        return (exact + code + named).take(limit)
    }
}

/** App state: the planner (observable by Compose) and the changes the screens make. */
class AppModel(context: Context) {
    private val store = PlannerStore(AndroidPlannerFiles(context)) { todayIst().toString() }
    val stations = Stations(context)
    var planner: Planner by mutableStateOf(store.planner); private set
    val today: String get() = todayIst().toString()

    private fun update(change: (Planner) -> Planner) { planner = store.update(change) }

    fun saveJourney(j: Journey) = update { p -> p.copy(journeys = p.journeys.filter { it.id != j.id } + j) }
    fun setStatus(j: Journey, status: JourneyStatus) = saveJourney(transition(j, status, today))
    fun deleteJourney(j: Journey) = update { p -> p.copy(journeys = p.journeys.filter { it.id != j.id }) }
    fun saveRoutine(rule: Rule) = update { saveRule(it, rule, today) }
    fun deleteRoutine(id: String) = update { removeRule(it, id, today) }
    fun setSettings(change: (dev.railwatch.shared.domain.Settings) -> dev.railwatch.shared.domain.Settings) = update { p -> p.copy(settings = change(p.settings)) }

    val ticketFiles = TicketFiles(context)
    val reader by lazy { TicketReader(ticketFiles) }

    /** Attaches imported tickets in one save: each to its chosen journey, or as a new booked journey. Unused files are deleted. */
    fun attach(rows: List<ImportRow>): Int {
        var attached = 0
        update { p ->
            var journeys = p.journeys
            for (r in rows) {
                val choice = r.choice
                when {
                    choice == null -> ticketFiles.delete(r.file.id)
                    choice == "create" -> dev.railwatch.shared.domain.journeyFromTicket(java.util.UUID.randomUUID().toString(), r.details, r.file, p.settings, today)?.let { journeys = journeys + it; attached++ } ?: ticketFiles.delete(r.file.id)
                    else -> journeys.firstOrNull { it.id == choice }?.let { j -> journeys = journeys.map { if (it.id == j.id) dev.railwatch.shared.domain.applyTicket(j, r.details, r.file, today) else it }; attached++ } ?: ticketFiles.delete(r.file.id)
                }
            }
            p.copy(journeys = journeys)
        }
        return attached
    }

    /** Removes a ticket PDF from a journey (and the file). */
    fun removeTicket(j: Journey, id: String) { ticketFiles.delete(id); saveJourney(j.copy(attachments = j.attachments?.filter { it.id != id }?.ifEmpty { null })) }

    init {
        // Ticket PDFs are kept for the retention period after a trip ends, then deleted (the journey stays).
        val (cleaned, expired) = dev.railwatch.shared.domain.expiredTicketFiles(planner, today)
        if (expired.isNotEmpty()) { expired.forEach(ticketFiles::delete); planner = store.update { cleaned } }
    }
}
