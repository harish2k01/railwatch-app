package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.statYears
import dev.railwatch.shared.domain.yearStats

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

/** Stats: the year in trains (totals, streaks, records) and Spend, as two tabs. */
@Composable
fun StatsPage(model: AppModel, spend: @Composable () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface).padding(4.dp)) {
            listOf("Year", "Spend").forEachIndexed { i, l ->
                Text(l, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (tab == i) Rw.colors.brandInk else Rw.colors.text,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (tab == i) Rw.colors.brand else Rw.colors.surface)
                        .clickable(role = Role.Tab) { tab = i }.heightIn(min = 44.dp).padding(vertical = 12.dp))
            }
        }
        if (tab == 0) YearTab(model) else spend()
    }
}

@Composable
private fun YearTab(model: AppModel) {
    val p = model.planner; val today = model.today
    val years = remember(p, today) { statYears(p, today) }
    var year by rememberSaveable { mutableIntStateOf(years.first()) }
    val s = remember(p, year, today) { yearStats(p, year, today) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow("‹", "Previous year", years.any { it < year }) { year = years.filter { it < year }.max() }
            Text("$year in Trains", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Rw.colors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Arrow("›", "Next year", years.any { it > year }) { year = years.filter { it > year }.min() }
        }
        if (s.trips == 0 && s.spent == 0.0) {
            Text("No trips travelled in $year yet. Trips count here once their date has passed.", color = Rw.colors.muted, modifier = Modifier.padding(4.dp))
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Big(s.trips.toString(), if (s.trips == 1) "trip taken" else "trips taken", Modifier.weight(1f))
            Big(rupees(s.spent), "spent on tickets", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Big(if (s.minutes > 0) "${s.minutes / 60} h" else "—", "on trains, by timetable", Modifier.weight(1f))
            Big(s.stations.toString(), "stations", Modifier.weight(1f))
        }
        s.route?.let { (a, b) ->
            Card {
                Label("Most-Used Route")
                Text("$a ⇄ $b · ${s.routeTrips} trip${if (s.routeTrips == 1) "" else "s"}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Rw.colors.text)
                listOfNotNull(s.routeTrain, s.routeClass).joinToString(", ").takeIf { it.isNotEmpty() }?.let { Text("Usually $it", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Card {
            Label("Trips by Month")
            val max = s.byMonth.max().coerceAtLeast(1)
            val busiest = s.byMonth.indexOf(s.byMonth.max())
            Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp).semantics(mergeDescendants = true) {
                contentDescription = s.byMonth.mapIndexed { i, n -> "${MONTHS[i]} $n" }.joinToString(", ")
            }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                s.byMonth.forEachIndexed { i, n ->
                    Box(Modifier.weight(1f).height((4 + 80 * n / max).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (i == busiest && n > 0) Rw.colors.brand else if (n > 0) Rw.colors.brandSoft else Rw.colors.surface2))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MONTHS.forEach { Text(it.take(1), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted) }
            }
            if (s.byMonth[busiest] > 0) Text("Busiest: ${MONTHS[busiest]}, ${s.byMonth[busiest]} trip${if (s.byMonth[busiest] == 1) "" else "s"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        }

        Text("Streaks", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        if (s.tatkalTries > 0 || s.tatkal.best > 0) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.attentionBg).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tatkal Streak", fontWeight = FontWeight.ExtraBold, color = Rw.colors.attentionInk, modifier = Modifier.weight(1f))
                Text("${s.tatkal.current} in a row", style = MaterialTheme.typography.labelMedium, color = Rw.colors.attentionBg,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Rw.colors.attentionInk).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Text("${s.tatkalBooked} of ${s.tatkalTries} Tatkal tries booked in $year. Best run: ${s.tatkal.best}.", color = Rw.colors.attentionInk, style = MaterialTheme.typography.bodyMedium)
        }
        val tiles = listOfNotNull(
            s.windows.current.takeIf { s.windows.best > 0 }?.let { "$it" to "booking windows in a row booked in time (best ${s.windows.best})" },
            s.weeks.takeIf { it > 1 }?.let { "$it wk" to "in a row with at least one trip" },
            s.withStatus.takeIf { it > 0 }?.let { "${s.confirmedFirst}/$it" to "confirmed straight away" },
            s.waitlisted.takeIf { it > 0 }?.let { "${s.waitlistCleared}/$it" to "waitlists that cleared" },
        )
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (v, l) -> Big(v, l, Modifier.weight(1f)) }; if (row.size == 1) Box(Modifier.weight(1f)) }
        }
        if (tiles.isEmpty() && s.tatkalTries == 0) Text("Streaks start once you've booked a few trips.", color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp))

        val records = listOfNotNull(
            s.longest?.let { "Longest ride" to "${it.from} → ${it.to} · ${s.longestMinutes / 60} h ${s.longestMinutes % 60} m" },
            s.earliest?.let { "Earliest start" to it },
            s.byMonth.max().takeIf { it > 0 }?.let { "Most in a month" to "$it · ${MONTHS[s.byMonth.indexOf(it)]}" },
            s.berth?.let { (b, n) -> "Favourite berth" to "${dev.railwatch.app.ui.berthLabel(b)} · $n×" },
        )
        if (records.isNotEmpty()) {
            Text("Records", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Grouped {
                records.forEachIndexed { i, (k, v) ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, color = Rw.colors.muted, modifier = Modifier.weight(1f))
                        Text(v, color = Rw.colors.text, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Arrow(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) =
    Text(glyph, fontSize = 28.sp, color = if (enabled) Rw.colors.brand else Rw.colors.line, textAlign = TextAlign.Center,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label }.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 4.dp))

@Composable
private fun Card(content: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }

@Composable
private fun Label(text: String) = Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted)

@Composable
private fun Big(value: String, label: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Rw.colors.surface).padding(14.dp)) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Rw.colors.text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Rw.colors.muted)
    }
