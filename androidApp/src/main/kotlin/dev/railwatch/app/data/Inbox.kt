package dev.railwatch.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One item in the in-app inbox: a reminder that was sent, a PNR change, a cancellation. */
@Serializable data class InboxItem(val id: String, val at: String, val title: String, val body: String, val journeyId: String? = null, val read: Boolean = false)

/** The inbox behind the bell on Today: the last 100 items, kept on the phone. */
object Inbox {
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(c: Context) = c.getSharedPreferences("inbox", Context.MODE_PRIVATE)

    fun items(c: Context): List<InboxItem> = runCatching { json.decodeFromString<List<InboxItem>>(prefs(c).getString("items", "[]")!!) }.getOrDefault(emptyList())

    fun add(c: Context, title: String, body: String, journeyId: String? = null) {
        val item = InboxItem(java.util.UUID.randomUUID().toString(), kotlinx.datetime.Clock.System.now().toString(), title, body, journeyId)
        save(c, (listOf(item) + items(c)).take(100))
    }

    fun markAllRead(c: Context) = save(c, items(c).map { it.copy(read = true) })
    fun clear(c: Context) = save(c, emptyList())
    private fun save(c: Context, list: List<InboxItem>) = prefs(c).edit().putString("items", json.encodeToString(list)).apply()
}
