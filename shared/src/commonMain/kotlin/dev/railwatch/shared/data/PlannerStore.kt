package dev.railwatch.shared.data

import dev.railwatch.shared.domain.Planner
import dev.railwatch.shared.domain.extendRoutines
import dev.railwatch.shared.domain.reconcileLifecycle
import kotlinx.serialization.json.Json

/**
 * The planner is one JSON document, the same shape as the web app's planner and backups. Reading tolerates unknown
 * fields (a web backup carries settings the app doesn't use) and fills defaults for missing ones.
 */
object PlannerCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; coerceInputValues = true }
    fun decode(text: String): Planner = json.decodeFromString(Planner.serializer(), text)
    fun encode(planner: Planner): String = json.encodeToString(Planner.serializer(), planner)
}

/** Where the planner lives: on Android a private file written atomically. */
interface PlannerFiles {
    /** The saved document, or null on first run. */
    fun read(): String?
    /** Replaces the saved document in one step (write a temporary file, then rename). */
    fun write(text: String)
}

/** Loads, keeps routines topped up and lifecycle current, and saves the planner. Every change goes through [update]. */
class PlannerStore(private val files: PlannerFiles, private val today: () -> String) {
    var planner: Planner = load(); private set

    private fun load(): Planner {
        val saved = files.read()?.let { runCatching { PlannerCodec.decode(it) }.getOrNull() } ?: Planner()
        val current = reconcileLifecycle(extendRoutines(saved, today()), today())
        if (current != saved) files.write(PlannerCodec.encode(current))
        return current
    }

    /** Applies a change, normalises it (routines and lifecycle) and saves it. */
    fun update(change: (Planner) -> Planner): Planner {
        val next = reconcileLifecycle(change(planner), today())
        files.write(PlannerCodec.encode(next))
        planner = next
        return next
    }
}
