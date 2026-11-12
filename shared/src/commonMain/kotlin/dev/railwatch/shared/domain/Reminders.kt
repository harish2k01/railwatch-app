package dev.railwatch.shared.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime

/** One reminder to deliver on the phone. */
data class Reminder(val key: String, val journeyId: String, val at: Instant, val kind: Kind) {
    enum class Kind { Booking, Cancellation }
}

private val DEFAULT_TATKAL = listOf(ScheduleRow(1, "20:00"), ScheduleRow(0, "09:30"), ScheduleRow(0, "09:55"))

private fun instant(day: String, time: String) = Instant.parse("${day}T$time:00+05:30")
private fun schedule(day: String, rows: List<ScheduleRow>) = rows.map { instant(addDays(day, -it.daysBefore), it.time) }.distinct().sorted()

/** When to remind about a journey (ported from the web's scheduledReminders): booking, or cancellation follow-up. */
fun reminderTimes(p: Planner, j: Journey, today: String): List<Instant> {
    val s = p.settings
    if (j.status == JourneyStatus.CancellationNeeded) return if (s.cancellationEnabled) listOf(instant(today, s.cancellationTime)) else emptyList()
    if (j.status != JourneyStatus.NeedsBooking) return emptyList()
    // The journey's own choice wins, then its routine's, then the settings (web effectiveReminders).
    val routine = p.rules.firstOrNull { it.id == j.ruleId }?.reminderOverride
    val chosen = j.reminderOverride?.takeIf { it.mode != "inherit" } ?: routine?.takeIf { it.mode != "inherit" }
    if (chosen?.mode == "off") return emptyList()
    val day = bookingDay(j)
    if (chosen?.mode == "custom") {
        chosen.schedule?.let { return schedule(day, it) }
        return byClock(day, chosen.times, chosen.clock)
    }
    if (j.bookingType == "tatkal") return schedule(day, tatkalSchedule(s, j.tatkalClass == "non_ac"))
    s.bookingSchedule?.let { return schedule(day, it) }
    return byClock(day, s.reminderTimes, s.reminderClock)
}

/** The Tatkal schedule in use: the saved one, or the default (non-AC opens an hour later, so it shifts by an hour). */
fun tatkalSchedule(s: Settings, nonAc: Boolean): List<ScheduleRow> =
    if (nonAc) s.tatkalNonAcSchedule ?: DEFAULT_TATKAL.map { if (it.daysBefore == 0) it.copy(time = it.time.replace("09:", "10:")) else it } else s.tatkalSchedule ?: DEFAULT_TATKAL

private fun byClock(day: String, times: List<String>, clock: ReminderClock) = times.mapNotNull { t ->
    when (t) { "previous_evening" -> instant(addDays(day, -1), clock.previous_evening); "morning" -> instant(day, clock.morning); "opening" -> instant(day, clock.opening); else -> null }
}.distinct().sorted()

/** Moves a time inside quiet hours to their end (IST), the same day or the next. */
fun outsideQuietHours(at: Instant, q: QuietHours?): Instant {
    if (q == null || !q.enabled || q.start == q.end) return at
    val local = at.toLocalDateTime(dev.railwatch.shared.IST)
    val minutes = local.hour * 60 + local.minute
    fun m(t: String) = t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt()
    val start = m(q.start); val end = m(q.end)
    val inside = if (start < end) minutes in start until end else minutes >= start || minutes < end
    if (!inside) return at
    val day = local.date.toString()
    val endDay = if (start < end || minutes < end) day else addDays(day, 1)
    return instant(endDay, q.end)
}

/** Every reminder still ahead, across all upcoming journeys, after quiet hours. */
fun upcomingReminders(p: Planner, today: String, now: Instant): List<Reminder> =
    upcoming(p, today).flatMap { j ->
        val kind = if (j.status == JourneyStatus.CancellationNeeded) Reminder.Kind.Cancellation else Reminder.Kind.Booking
        reminderTimes(p, j, today).map { outsideQuietHours(it, p.settings.quietHours) }.distinct()
            .filter { it > now }.map { Reminder("${j.id}@$it", j.id, it, kind) }
    }.sortedBy { it.at }
