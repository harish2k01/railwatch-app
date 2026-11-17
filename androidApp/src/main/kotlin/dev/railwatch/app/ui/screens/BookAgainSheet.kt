package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.addDays
import dev.railwatch.shared.domain.bookAgain
import dev.railwatch.shared.domain.bookAgainDates
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.bookingDay
import dev.railwatch.shared.domain.daysBetween
import dev.railwatch.shared.domain.returnOf
import java.util.UUID

/**
 * Book Again from a past trip: the route, train, class and times are filled in; pick a date (the same weekday is
 * suggested) and, if the old trip had a way back, add that too. Saves as Needs Booking.
 */
@Composable
fun BookAgainSheet(source: Journey, model: AppModel, done: (Journey) -> Unit) {
    val today = model.today
    val suggestions = remember(source.id, today) { bookAgainDates(source, today) }
    val back = remember(model.planner, source.id) { returnOf(model.planner, source) }
    var date by remember { mutableStateOf("") }
    var withReturn by remember { mutableStateOf(back != null) }
    val window = model.planner.settings.bookingWindowDays
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Book Again", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.surface2).padding(14.dp)) {
            Text("${source.from} → ${source.to}", style = MaterialTheme.typography.titleLarge, color = Rw.colors.text, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(source.trainNumber?.takeIf { it.isNotBlank() }?.let { listOfNotNull(it, source.trainName).joinToString(" ") }, source.travelClass.takeIf { it.isNotBlank() },
                source.departure.takeIf { source.departureConfirmed == true }?.let { "departs $it" }, if (source.bookingType == "tatkal") "Tatkal" else null).joinToString(" · ").ifEmpty { "Same route" },
                color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
        DateField("Travel date", date, { date = it }, min = today)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { d ->
                val on = d == date
                Text(shortDay(d), style = MaterialTheme.typography.labelLarge, color = if (on) Rw.colors.brandInk else Rw.colors.text,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Rw.colors.brand else Rw.colors.surface2).clickable(role = Role.RadioButton) { date = d }
                        .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        if (back != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Switch) { withReturn = !withReturn }.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Add the Return Too", color = Rw.colors.text)
                Text("${back.from} → ${back.to}, ${daysBetween(source.date, back.date).let { if (it == 0) "the same day" else "$it day${if (it == 1) "" else "s"} later" }}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(withReturn, { withReturn = it }, colors = SwitchDefaults.colors(checkedTrackColor = Rw.colors.brand, checkedThumbColor = Rw.colors.brandInk))
        }
        if (date.isNotEmpty()) {
            val draft = bookAgain(source, date, "", window)
            val opens = bookingDay(draft)
            Text(if (opens <= today) "Booking for ${shortDay(date)} is open now. It shows in Do Next as Book Now." else "Booking opens ${shortDay(opens)} at ${bookingClock(draft)}. You'll get the usual reminders.",
                color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(if (withReturn && back != null) "Save Both Trips" else "Save Trip", {
            val trip = bookAgain(source, date, UUID.randomUUID().toString(), window)
            model.saveJourney(trip)
            if (withReturn && back != null) model.saveJourney(bookAgain(back, addDays(date, daysBetween(source.date, back.date)), UUID.randomUUID().toString(), window))
            done(trip)
        }, Modifier.fillMaxWidth(), enabled = date.isNotEmpty())
    }
}
