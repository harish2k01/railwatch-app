package dev.railwatch.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.JourneyRow
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.MonthHeader
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.components.Segmented
import dev.railwatch.app.ui.components.SwipeAction
import dev.railwatch.app.ui.components.SwipeRow
import dev.railwatch.app.ui.dayNum
import dev.railwatch.app.ui.journeyLine
import dev.railwatch.app.ui.monthLabel
import dev.railwatch.app.ui.statusLabel
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.app.ui.weekdayShort
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyList
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.byMonth
import dev.railwatch.shared.domain.journeyList

/** Journeys (canvas NativeJourneys): Upcoming / To book / History, month headings, status dots, swipe actions. */
@Composable
fun JourneysScreen(model: AppModel, open: (Journey) -> Unit, confirm: (String, () -> Unit) -> Unit, contentPadding: PaddingValues) {
    var list by rememberSaveable { mutableStateOf(JourneyList.Upcoming) }
    val today = model.today
    val planner = model.planner
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    // Searching looks through every trip (booked, planned, cancelled and past), newest travel date first.
    val items = androidx.compose.runtime.remember(planner, today, list, query) {
        if (searching) planner.journeys.filter { it.archivedAt == null && dev.railwatch.shared.domain.matchesQuery(it, query, model.stations::name) }.sortedByDescending { it.date }
        else journeyList(planner, today, list)
    }
    LazyColumn(contentPadding = contentPadding) {
        item { LargeTitle("Journeys") }
        item { SearchField(query) { query = it } }
        if (searching) item {
            Text("${items.size} trip${if (items.size == 1) "" else "s"} found", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
        if (!searching) item { Segmented(listOf(JourneyList.Upcoming to "Upcoming", JourneyList.Planned to "Planned", JourneyList.Cancelled to "Cancelled", JourneyList.History to "History"), list) { list = it } }
        if (items.isEmpty()) item {
            Text(if (searching) "No trips match. Try a station, train, PNR or a date like 12 Jan." else when (list) { JourneyList.Planned -> "Every trip is booked."; JourneyList.Cancelled -> "No cancelled trips."; JourneyList.History -> "No past trips yet."; else -> "No booked trips coming up." },
                style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted, modifier = Modifier.padding(top = 24.dp, start = 4.dp))
        }
        // Every list is split by travel month; planned rows still say when booking opens.
        val groups = byMonth(items).map { (m, t) -> monthLabel(m, today) to t }
        groups.forEach { (month, trips) ->
            item(key = "m$month") { MonthHeader(month) }
            items(trips, key = { it.id }) { j ->
                val first = j == trips.first(); val last = j == trips.last()
                val shape = RoundedCornerShape(topStart = if (first) 18.dp else 0.dp, topEnd = if (first) 18.dp else 0.dp, bottomStart = if (last) 18.dp else 0.dp, bottomEnd = if (last) 18.dp else 0.dp)
                // Rows slide into place when a trip moves between lists or changes status.
                androidx.compose.foundation.layout.Column(Modifier.animateItem().fillMaxWidth().clip(shape)) {
                    SwipeRow(actions(j, model, confirm)) {
                        // Planned is grouped by booking day, not month, so its rows say which month they travel.
                        val detail = if (list == JourneyList.Planned && !searching) "Travel ${dev.railwatch.app.ui.dayMonth(j.date)} · opens ${dev.railwatch.app.ui.shortDay(dev.railwatch.shared.domain.bookingDay(j))} ${dev.railwatch.shared.domain.bookingClock(j)}" else journeyLine(j, today)
                        JourneyRow(dayNum(j.date), weekdayShort(j.date), "${j.from} → ${j.to}", detail, toneOf(j), statusLabel(j)) { open(j) }
                    }
                    if (!last) RowDivider()
                }
            }
        }
    }
}

/** Swipe actions: booked trips Cancel; planned trips Booked and Skip; trips to cancel Cancelled. */
@Composable
private fun actions(j: Journey, model: AppModel, confirm: (String, () -> Unit) -> Unit): List<SwipeAction> {
    val c = Rw.colors
    return when (j.status) {
        JourneyStatus.Booked -> listOf(SwipeAction("Cancel", RwIcons.Close, c.danger, c.ground) { confirm("Mark ${j.from} → ${j.to} as needing cancellation?") { model.setStatus(j, JourneyStatus.CancellationNeeded) } })
        JourneyStatus.NeedsBooking -> listOf(
            SwipeAction("Booked", RwIcons.Check, c.brand, c.brandInk) { model.setStatus(j, JourneyStatus.Booked) },
            SwipeAction("Skip", RwIcons.Close, c.surface2, c.text) { confirm("Skip ${j.from} → ${j.to}? You won’t be reminded to book it.") { model.setStatus(j, JourneyStatus.Skipped) } },
        )
        JourneyStatus.CancellationNeeded -> listOf(SwipeAction("Cancelled", RwIcons.Check, c.danger, c.ground) { confirm("Have you cancelled this ticket on IRCTC?") { model.setStatus(j, JourneyStatus.Cancelled) } })
        else -> emptyList()
    }
}

/** Search box for Journeys: stations, train, PNR, coach or a date ("12 Jan", "Jan"). */
@Composable
private fun SearchField(query: String, change: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(query, { change(it.take(60)) }, singleLine = true,
        placeholder = { Text("Search station, train, PNR or date") },
        leadingIcon = { androidx.compose.material3.Icon(dev.railwatch.app.ui.components.RwIcons.Search, contentDescription = null, tint = Rw.colors.muted) },
        trailingIcon = {
            if (query.isNotEmpty()) androidx.compose.material3.IconButton({ change("") }) {
                androidx.compose.material3.Icon(dev.railwatch.app.ui.components.RwIcons.Close, contentDescription = "Clear search", tint = Rw.colors.muted)
            }
        },
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = Rw.colors.brand, unfocusedBorderColor = Rw.colors.line,
            focusedContainerColor = Rw.colors.surface, unfocusedContainerColor = Rw.colors.surface, focusedTextColor = Rw.colors.text, unfocusedTextColor = Rw.colors.text))
}
