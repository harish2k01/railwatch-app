package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.chartTime
import dev.railwatch.shared.domain.readableStatus
import dev.railwatch.shared.domain.waitlistedAfterChart
import dev.railwatch.shared.domain.watchesChart
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val IST = java.time.ZoneId.of("Asia/Kolkata")
private fun clock(i: Instant) = java.time.Instant.ofEpochMilli(i.toEpochMilliseconds()).atZone(IST).toLocalTime().toString().take(5)
private fun day(i: Instant) = java.time.Instant.ofEpochMilli(i.toEpochMilliseconds()).atZone(IST).toLocalDate().toString()

/** "3 h 20 m", "45 m". */
fun spanText(d: Duration): String {
    val m = d.inWholeMinutes.coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${m % 60} m" else "$m m"
}

/** "Today", "Tomorrow" or "Thu 19 Nov" for when the chart comes. */
private fun dayWord(i: Instant, today: String): String = when (val d = day(i)) { today -> "Today"; dev.railwatch.shared.domain.addDays(today, 1) -> "Tomorrow"; else -> shortDay(d) }

/** The current time, ticking each minute, so countdowns stay right while the screen is open. */
@Composable
private fun nowEachMinute(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Clock.System.now() } }
    return now
}

/** (headline, explanation) for the chart on a waitlisted or RAC trip; null when it doesn't apply. */
private fun chartWords(j: Journey, now: Instant, today: String): Pair<String, String>? {
    if (!watchesChart(j)) return null
    val at = chartTime(j) ?: return null
    return if (now < at) "Chart ${dayWord(at, today).let { if (it == "Today") "" else "$it " }}Around ${clock(at)} · in ${spanText(at - now)}" to
        "Your status becomes final then. We'll remind you to check."
    else "The Chart Should Be Out" to "Check your PNR to see your final status."
}

/** One line on the boarding pass: when the chart comes out. */
@Composable
fun ChartLine(j: Journey, today: String, ink: Color) {
    val (head, sub) = chartWords(j, nowEachMinute(), today) ?: return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(head, color = ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
        Text(sub, color = ink.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
    }
}

/** The chart line after a check that hasn't seen the chart yet. */
fun chartNote(j: Journey, today: String): String? {
    val at = chartTime(j) ?: return null
    val now = Clock.System.now()
    if (now >= at) return "Chart not prepared yet. It usually comes out around ${clock(at)}; check again shortly."
    return "Chart not prepared yet. It's usually ready around ${clock(at)}${dayWord(at, today).let { if (it == "Today") "" else " ${it.lowercase().takeIf { w -> w == "tomorrow" } ?: "on $it"}" }}; we'll remind you at ${clock(at + 15.minutes)}."
}

/**
 * Status So Far on the trip: each move from booking to the last check (solid), then the chart and departure
 * still to come (dashed). Only for booked trips with a PNR that were waitlisted or RAC at some point.
 */
@Composable
fun StatusTimeline(j: Journey, today: String) {
    if (j.status != JourneyStatus.Booked || j.pnr.isEmpty()) return
    val log = j.statusLog.orEmpty()
    val booking = j.pnrStatus?.takeIf { it.pnr == j.pnr }?.passengers?.firstOrNull()?.booking?.let(::readableStatus)?.takeIf { it.isNotEmpty() }
    val watching = watchesChart(j)
    if (log.isEmpty() && !watching) return
    val now = nowEachMinute()
    val chart = chartTime(j)
    data class Step(val title: String, val sub: String?, val done: Boolean, val accent: Boolean = false)
    val steps = buildList {
        if (booking != null && log.firstOrNull()?.detail?.startsWith(booking) != false) add(Step("Booked · $booking", null, true))
        log.forEach { e ->
            val at = runCatching { Instant.parse(e.at) }.getOrNull()
            add(Step(e.detail ?: e.status, at?.let { "Checked ${dayWord(it, today)}, ${clock(it)}" }, true))
        }
        if (watching && chart != null) add(Step(if (now < chart) "Chart Around ${clock(chart)} ${dayWord(chart, today)}" else "Chart Should Be Out", if (now < chart) "Status becomes final. Reminder at ${clock(chart + 15.minutes)}." else "Check your PNR for the final status.", false, accent = true))
        add(Step("Departs ${j.departure}", null, false))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Rw.colors.surface2).padding(16.dp)) {
        Text("STATUS SO FAR", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(bottom = 8.dp))
        steps.forEachIndexed { i, s ->
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(if (s.done) null else "Expected", s.title, s.sub).joinToString(", ") }) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.width(12.dp)) {
                    val dot = Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape)
                    Box(if (s.done) dot.background(if (i == steps.indexOfLast { it.done }) Rw.colors.attentionInk else Rw.colors.muted)
                        else dot.border(2.dp, if (s.accent) Rw.colors.brand else Rw.colors.line, CircleShape))
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).height(if (s.sub != null) 30.dp else 18.dp).background(if (steps[i + 1].done) Rw.colors.line else Rw.colors.line.copy(alpha = .5f)))
                }
                Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                    Text(s.title, color = if (s.accent) Rw.colors.brand else if (s.done) Rw.colors.text else Rw.colors.muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    s.sub?.let { Text(it, color = Rw.colors.muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** The chart is out and the ticket is still fully waitlisted: say what happens and offer to book again. */
@Composable
fun WaitlistedAfterChart(j: Journey, model: AppModel) {
    if (!waitlistedAfterChart(j)) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.cancelBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Still Waitlisted After the Chart", color = Rw.colors.cancelInk, fontWeight = FontWeight.Bold)
        Text("A fully waitlisted e-ticket is cancelled once the chart is prepared, and the fare is refunded to your account. Look at other trains, or Tatkal for a later date.",
            color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
        Text("Move to Needs Booking", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { model.setStatus(j, JourneyStatus.NeedsBooking) }.padding(vertical = 12.dp))
    }
}
