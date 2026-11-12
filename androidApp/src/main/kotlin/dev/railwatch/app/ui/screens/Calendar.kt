package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.JourneyRow
import dev.railwatch.app.ui.components.RoundIcon
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.dayNum
import dev.railwatch.app.ui.journeyLine
import dev.railwatch.app.ui.longDay
import dev.railwatch.app.ui.monthLabel
import dev.railwatch.app.ui.statusLabel
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.app.ui.weekdayShort
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.addDays
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.bookingDay
import dev.railwatch.shared.domain.weekday

/**
 * Calendar: a month of travel (purple), booking days (blue) and time off (shaded), the week starting on the chosen
 * day; tapping a day lists what happens on it.
 */
@Composable
fun CalendarView(model: AppModel, open: (Journey) -> Unit) {
    val today = model.today
    val p = model.planner
    var month by rememberSaveable { mutableStateOf(today.take(7)) }
    var picked by rememberSaveable { mutableStateOf(today) }
    val active = p.journeys.filter { it.status in setOf(JourneyStatus.NeedsBooking, JourneyStatus.Booked, JourneyStatus.CancellationNeeded, JourneyStatus.Completed) }
    val travel = active.groupBy { it.date }
    val opens = active.filter { it.status == JourneyStatus.NeedsBooking }.groupBy { bookingDay(it) }
    val off = p.holidays.associateBy { it.date }
    val start = p.settings.weekStartsOn
    val c = Rw.colors

    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel(month, today), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f).padding(start = 4.dp))
            RoundIcon(RwIcons.ChevronLeft, "Previous month") { month = shiftMonth(month, -1) }
            Spacer(Modifier.size(8.dp))
            RoundIcon(RwIcons.ChevronRight, "Next month") { month = shiftMonth(month, 1) }
        }
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i -> Text(listOf("S", "M", "T", "W", "T", "F", "S")[(start + i) % 7], color = c.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val first = "$month-01"
        val lead = (weekday(first) - start + 7) % 7
        var day = addDays(first, -lead)
        repeat(6) {
            if (day.take(7) > month) return@repeat
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val d = day
                    val inMonth = d.take(7) == month
                    val bg = when { d == picked -> c.brand; off[d] != null -> c.timeoffBg; else -> Color.Transparent }
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                        .then(if (d == today && d != picked) Modifier.border(1.5.dp, c.brand, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { picked = d; if (!inMonth) month = d.take(7) }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.takeLast(2).trimStart('0'), color = when { d == picked -> c.brandInk; !inMonth -> c.muted.copy(alpha = 0.5f); else -> c.text }, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                                if (travel[d] != null) Dot(if (d == picked) c.brandInk else c.brand)
                                if (opens[d] != null) Dot(if (d == picked) c.brandInk else c.open)
                            }
                        }
                    }
                    day = addDays(day, 1)
                }
            }
        }
        Row(Modifier.padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(c.brand, "Travel"); Legend(c.open, "Booking Opens"); Legend(c.timeoffBg, "Time Off")
        }
        Text(longDay(picked), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp))
        val trips = travel[picked].orEmpty(); val booking = opens[picked].orEmpty(); val dayOff = off[picked]
        if (trips.isEmpty() && booking.isEmpty() && dayOff == null) Text("Nothing on this day.", color = c.muted, modifier = Modifier.padding(start = 4.dp))
        else Grouped {
            var any = false
            dayOff?.let { Text("${it.name} · ${if (it.type == "leave") "Leave" else "Holiday"}", color = c.timeoffInk, modifier = Modifier.fillMaxWidth().background(c.timeoffBg).padding(16.dp)); any = true }
            trips.forEach { j -> if (any) RowDivider(); any = true; JourneyRow(dayNum(j.date), weekdayShort(j.date), "${j.from} → ${j.to}", journeyLine(j, today), toneOf(j), statusLabel(j)) { open(j) } }
            booking.forEach { j -> if (any) RowDivider(); any = true; JourneyRow(dayNum(j.date), weekdayShort(j.date), "Book ${j.from} → ${j.to}", "Opens ${bookingClock(j)} for ${dayNum(j.date)} ${monthLabel(j.date.take(7), today).take(3)}", dev.railwatch.app.ui.components.Tone.Info, "Opens") { open(j) } }
        }
    }
}

@Composable private fun Dot(color: Color) = Box(Modifier.size(5.dp).background(color, CircleShape))
@Composable private fun Legend(color: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).background(color, CircleShape)); Text(label, color = Rw.colors.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 5.dp))
}

private fun shiftMonth(m: String, by: Int): String {
    val total = m.take(4).toInt() * 12 + m.substring(5, 7).toInt() - 1 + by
    return "%04d-%02d".format(total / 12, total % 12 + 1)
}

/** The Calendar tab. */
@Composable
fun CalendarScreen(model: AppModel, open: (Journey) -> Unit, contentPadding: androidx.compose.foundation.layout.PaddingValues) {
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = contentPadding) { item { dev.railwatch.app.ui.components.LargeTitle("Calendar") }; item { CalendarView(model, open) } }
}
