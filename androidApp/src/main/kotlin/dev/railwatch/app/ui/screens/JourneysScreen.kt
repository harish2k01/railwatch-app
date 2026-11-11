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
    var list by rememberSaveable { mutableStateOf<JourneyList?>(JourneyList.Upcoming) } // null: calendar
    val today = model.today
    val items = list?.let { journeyList(model.planner, today, it) }.orEmpty()
    LazyColumn(contentPadding = contentPadding) {
        item { LargeTitle("Journeys") }
        item { Segmented(listOf(JourneyList.Upcoming to "Upcoming", JourneyList.ToBook to "To book", null to "Calendar", JourneyList.History to "History"), list) { list = it } }
        if (list == null) { item { CalendarView(model, open) }; return@LazyColumn }
        if (items.isEmpty()) item {
            Text(when (list) { JourneyList.ToBook -> "Nothing to book right now."; JourneyList.History -> "No past trips yet."; else -> "No upcoming trips. Add one with +." },
                style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted, modifier = Modifier.padding(top = 24.dp, start = 4.dp))
        }
        byMonth(items).forEach { (month, trips) ->
            item(key = "m$month") { MonthHeader(monthLabel(month, today)) }
            items(trips, key = { it.id }) { j ->
                val first = j == trips.first(); val last = j == trips.last()
                val shape = RoundedCornerShape(topStart = if (first) 18.dp else 0.dp, topEnd = if (first) 18.dp else 0.dp, bottomStart = if (last) 18.dp else 0.dp, bottomEnd = if (last) 18.dp else 0.dp)
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clip(shape)) {
                    SwipeRow(actions(j, model, confirm)) {
                        JourneyRow(dayNum(j.date), weekdayShort(j.date), "${j.from} → ${j.to}", journeyLine(j, today), toneOf(j), statusLabel(j)) { open(j) }
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
