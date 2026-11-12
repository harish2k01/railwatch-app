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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

    private val appContext = context.applicationContext
    private fun update(change: (Planner) -> Planner) { planner = store.update(change); Alarms.reschedule(appContext) }

    fun saveJourney(j: Journey) = update { p -> p.copy(journeys = p.journeys.filter { it.id != j.id } + j) }
    fun setStatus(j: Journey, status: JourneyStatus) = saveJourney(transition(j, status, today))
    fun deleteJourney(j: Journey) = update { p -> p.copy(journeys = p.journeys.filter { it.id != j.id }) }
    fun saveRoutine(rule: Rule) = update { saveRule(it, rule, today) }
    fun deleteRoutine(id: String) = update { removeRule(it, id, today) }
    fun setSettings(change: (dev.railwatch.shared.domain.Settings) -> dev.railwatch.shared.domain.Settings) = update { p -> p.copy(settings = change(p.settings)) }

    /** Imports time off from an .ics or date,name,type .csv file; returns a message for the person. */
    fun importTimeOff(text: String, fileName: String, type: String): String = runCatching {
        val days = if (fileName.endsWith(".csv", true) || !text.contains("BEGIN:VCALENDAR", true)) dev.railwatch.shared.domain.parseTimeOffCsv(text) else dev.railwatch.shared.domain.parseTimeOffIcs(text, type)
        var n = 0
        update { p -> dev.railwatch.shared.domain.addTimeOff(p, days) { java.util.UUID.randomUUID().toString() }.also { n = it.second }.first }
        if (n == 0) "Those days were already in your time off." else "Added $n day${if (n == 1) "" else "s"} of time off."
    }.getOrElse { it.message ?: "Couldn't read that file." }
    fun addTimeOff(date: String, name: String, type: String) = update { p -> dev.railwatch.shared.domain.addTimeOff(p, listOf(dev.railwatch.shared.domain.TimeOffDay(date, name, type))) { java.util.UUID.randomUUID().toString() }.first }
    /** Replaces the given days (none when adding) with one day per date from start to end. */
    fun setTimeOff(replace: List<String>, start: String, end: String, name: String, type: String) = update { p ->
        val days = generateSequence(start) { dev.railwatch.shared.domain.addDays(it, 1) }.takeWhile { it <= end }.take(366)
            .map { dev.railwatch.shared.domain.TimeOffDay(it, name, type) }.toList()
        dev.railwatch.shared.domain.addTimeOff(p.copy(holidays = p.holidays.filter { it.id !in replace }), days) { java.util.UUID.randomUUID().toString() }.first
    }
    fun removeTimeOff(id: String) = update { p -> p.copy(holidays = p.holidays.filter { it.id != id }) }
    fun keepOnTimeOff(j: Journey) = saveJourney(j.copy(timeOffKept = true))

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

    /**
     * A backup in the website's format: the planner JSON plus `attachmentFiles` ([{id, data: "data:<type>;base64,…"}]),
     * so a file made here restores on the website and the other way round.
     */
    fun backupJson(): String {
        val base = dev.railwatch.shared.data.PlannerCodec.json.encodeToJsonElement(Planner.serializer(), planner).jsonObject
        val seen = HashSet<String>()
        val files = planner.journeys.flatMap { it.attachments.orEmpty() }.filter { seen.add(it.id) }.mapNotNull { a ->
            val f = ticketFiles.file(a.id); if (!f.exists()) return@mapNotNull null
            kotlinx.serialization.json.buildJsonObject {
                put("id", JsonPrimitive(a.id))
                put("data", JsonPrimitive("data:${a.type};base64," + android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)))
            }
        }
        return JsonObject(base + ("attachmentFiles" to JsonArray(files))).toString()
    }

    /** Checks a backup without changing anything: the planner it holds, and its ticket files checked against their metadata. */
    fun readBackup(text: String): Pair<Planner, List<Pair<String, ByteArray>>> {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: error("That file isn't a RailWatch backup.")
        if (root["journeys"] !is JsonArray) error("That file isn't a RailWatch backup.")
        val next = runCatching { dev.railwatch.shared.data.PlannerCodec.json.decodeFromJsonElement(Planner.serializer(), JsonObject(root - "attachmentFiles")) }
            .getOrElse { error("That backup couldn't be read.") }
        val meta = next.journeys.flatMap { it.attachments.orEmpty() }.associateBy { it.id }
        val files = (root["attachmentFiles"] as? JsonArray).orEmpty().map { item ->
            val o = item.jsonObject; val id = o["id"]!!.jsonPrimitive.content; val data = o["data"]!!.jsonPrimitive.content
            val m = meta[id] ?: error("A ticket file in the backup doesn't match any trip.")
            if (!data.startsWith("data:${m.type};base64,") || id.contains('/') || id.contains("..")) error("A ticket file in the backup is damaged.")
            id to android.util.Base64.decode(data.substringAfter(','), android.util.Base64.DEFAULT)
        }
        return next to files
    }

    /** Replaces everything with a checked backup: its ticket files are written first, then old files no longer used are removed. */
    fun restore(backup: Pair<Planner, List<Pair<String, ByteArray>>>) {
        val (next, files) = backup
        files.forEach { (id, bytes) -> ticketFiles.file(id).writeBytes(bytes) }
        val keep = next.journeys.flatMap { it.attachments.orEmpty() }.map { it.id }.toSet()
        planner.journeys.flatMap { it.attachments.orEmpty() }.filter { it.id !in keep }.forEach { ticketFiles.delete(it.id) }
        update { next }
    }

    /** Removes a ticket PDF from a journey (and the file). */
    fun removeTicket(j: Journey, id: String) { ticketFiles.delete(id); saveJourney(j.copy(attachments = j.attachments?.filter { it.id != id }?.ifEmpty { null })) }

    init {
        // Ticket PDFs are kept for the retention period after a trip ends, then deleted (the journey stays).
        val (cleaned, expired) = dev.railwatch.shared.domain.expiredTicketFiles(planner, today)
        if (expired.isNotEmpty()) { expired.forEach(ticketFiles::delete); planner = store.update { cleaned } }
        Alarms.channels(appContext); Alarms.reschedule(appContext)
    }
}
