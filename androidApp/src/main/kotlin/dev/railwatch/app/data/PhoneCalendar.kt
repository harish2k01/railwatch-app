package dev.railwatch.app.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import dev.railwatch.shared.data.PlannerCodec
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.effectiveTicketStatus
import dev.railwatch.shared.domain.tripTimes
import dev.railwatch.shared.domain.upcoming
import dev.railwatch.shared.todayIst
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Upcoming trips copied into a calendar on the phone the person picks. RailWatch owns only the events it made (their
 * ids are remembered here): they follow every change to the trip and are removed when it is cancelled or skipped.
 */
object PhoneCalendar {
    data class Cal(val id: Long, val name: String, val account: String)
    private val IST = ZoneId.of("Asia/Kolkata")
    private fun prefs(c: Context) = c.getSharedPreferences("calendar", Context.MODE_PRIVATE)

    fun allowed(c: Context) = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        .all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
    fun calendarId(c: Context): Long? = prefs(c).getLong("id", -1).takeIf { it >= 0 }
    fun includePlanned(c: Context) = prefs(c).getBoolean("planned", false)

    /** Calendars the person can add events to. */
    fun calendars(c: Context): List<Cal> {
        if (!allowed(c)) return emptyList()
        val out = mutableListOf<Cal>()
        c.contentResolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME),
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR}", null, null)?.use {
            while (it.moveToNext()) out += Cal(it.getLong(0), it.getString(1) ?: "Calendar", it.getString(2) ?: "")
        }
        return out
    }

    /** Turns syncing on (into that calendar) or off (null: RailWatch's events are removed). */
    fun choose(c: Context, id: Long?, planned: Boolean = includePlanned(c)) {
        if (id != calendarId(c)) clear(c)
        prefs(c).edit().putLong("id", id ?: -1).putBoolean("planned", planned).apply()
        syncAsync(c)
    }

    fun syncAsync(c: Context) { val app = c.applicationContext; Thread { runCatching { sync(app) } }.start() }

    @Synchronized private fun sync(c: Context) {
        val cal = calendarId(c) ?: return
        if (!allowed(c)) return
        val planner = AndroidPlannerFiles(c).read()?.let { runCatching { PlannerCodec.decode(it) }.getOrNull() } ?: return
        val wanted = upcoming(planner, todayIst().toString())
            .filter { it.status == JourneyStatus.Booked || (includePlanned(c) && it.status == JourneyStatus.NeedsBooking) }
            .associateBy { it.id }
        val known = map(c)
        val next = HashMap<String, Pair<Long, Int>>()
        // Remove events for trips no longer wanted.
        known.filterKeys { it !in wanted }.forEach { (_, e) -> delete(c, e.first) }
        for ((id, j) in wanted) {
            val values = values(j, cal); val hash = values.toString().hashCode()
            val old = known[id]
            if (old != null && old.second == hash && exists(c, old.first)) { next[id] = old; continue }
            if (old != null && exists(c, old.first)) { c.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, old.first), values, null, null); next[id] = old.first to hash }
            else c.contentResolver.insert(Events.CONTENT_URI, values)?.let { next[id] = ContentUris.parseId(it) to hash }
        }
        save(c, next)
    }

    private fun clear(c: Context) { val app = c.applicationContext; Thread { runCatching { map(app).values.forEach { delete(app, it.first) }; save(app, emptyMap()) } }.start() }

    private fun values(j: Journey, cal: Long) = ContentValues().apply {
        val t = tripTimes(j)
        val start = LocalDate.parse(j.date).atTime(runCatching { LocalTime.parse(j.departure) }.getOrDefault(LocalTime.of(9, 0))).atZone(IST)
        val end = t.arrive?.let { a -> runCatching { LocalDate.parse(t.arriveDate ?: j.date).atTime(LocalTime.parse(a)).atZone(IST) }.getOrNull() }
            ?.takeIf { it.isAfter(start) } ?: start.plusHours(1)
        val booked = j.status == JourneyStatus.Booked
        put(Events.CALENDAR_ID, cal)
        put(Events.TITLE, (if (booked) "" else "To book: ") + "${j.from} → ${j.to}" + (j.trainName ?: j.train.ifBlank { null })?.let { " · $it" }.orEmpty())
        put(Events.DTSTART, start.toInstant().toEpochMilli()); put(Events.DTEND, end.toInstant().toEpochMilli())
        put(Events.EVENT_TIMEZONE, "Asia/Kolkata")
        put(Events.EVENT_LOCATION, j.from)
        put(Events.DESCRIPTION, listOfNotNull(
            j.pnr.ifBlank { null }?.let { "PNR $it" }, effectiveTicketStatus(j) ?: j.currentStatus,
            listOfNotNull(j.coach, j.seat).joinToString(" ").ifBlank { null }, j.travelClass.ifBlank { null },
            "Added by RailWatch",
        ).joinToString("\n"))
        put(Events.HAS_ALARM, 0)
    }

    private fun exists(c: Context, id: Long) = c.contentResolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events._ID, Events.DELETED), null, null, null)
        ?.use { it.moveToFirst() && it.getInt(1) == 0 } ?: false
    private fun delete(c: Context, id: Long) { runCatching { c.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null) } }

    private fun map(c: Context): Map<String, Pair<Long, Int>> = prefs(c).getString("events", "")!!.split(';').filter { it.isNotBlank() }.mapNotNull { e ->
        val p = e.split(','); runCatching { p[0] to (p[1].toLong() to p[2].toInt()) }.getOrNull()
    }.toMap()
    private fun save(c: Context, m: Map<String, Pair<Long, Int>>) =
        prefs(c).edit().putString("events", m.entries.joinToString(";") { "${it.key},${it.value.first},${it.value.second}" }).apply()
}
