package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.berthLabel
import dev.railwatch.app.ui.components.Fact
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.components.SecondaryButton
import dev.railwatch.app.ui.longDay
import dev.railwatch.app.ui.relative
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.statusLabel
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.Rule
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.bookingDay
import dev.railwatch.shared.domain.tripTiming
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Journey sheet (canvas NativeJourneySheet): route, times, facts, progress and the actions for its state. */
@Composable
fun JourneySheet(j: Journey, model: AppModel, close: () -> Unit, confirm: (String, () -> Unit) -> Unit, edit: () -> Unit = {}, ticket: () -> Unit = {}, pnr: () -> Unit = {}) {
    val today = model.today
    val name = model.stations::name
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text("${shortDay(j.date)}${j.leg?.let { if (it == "return") " · Return" else " · Onward" } ?: ""}", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${j.from} → ${j.to}", style = MaterialTheme.typography.headlineLarge, color = Rw.colors.text, modifier = Modifier.weight(1f))
                val (pillBg, pillInk) = when (toneOf(j)) {
                    dev.railwatch.app.ui.components.Tone.Ok -> Rw.colors.bookedBg to Rw.colors.bookedInk
                    dev.railwatch.app.ui.components.Tone.Info -> Rw.colors.openBg to Rw.colors.openInk
                    dev.railwatch.app.ui.components.Tone.Warn -> Rw.colors.attentionBg to Rw.colors.attentionInk
                    dev.railwatch.app.ui.components.Tone.Muted -> Rw.colors.surface2 to Rw.colors.muted
                }
                Text(statusLabel(j), style = MaterialTheme.typography.labelMedium, color = pillInk, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(pillBg).padding(horizontal = 12.dp, vertical = 5.dp))
            }
            Text(listOfNotNull(name(j.from), name(j.to)).joinToString(" to "), style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted)
        }
        QuickActions(j, model, onTicket = ticket, onPnr = pnr, onEdit = edit)
        val times = dev.railwatch.shared.domain.tripTimes(j)
        // Departs and arrives, each with its date above the time (times come from the ticket).
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Rw.colors.surface2).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DateTime(times.departDate, times.depart, Rw.colors.text, Alignment.Start, Modifier.weight(1f))
            TripSpan(times.span, Rw.colors.text, Modifier.padding(horizontal = 8.dp))
            DateTime(times.arriveDate ?: j.date, times.arrive, Rw.colors.text, Alignment.End, Modifier.weight(1f))
        }
        if (j.status == JourneyStatus.NeedsBooking) {
            val opens = bookingDay(j)
            Text("${if (opens <= today) "Booking Opened" else "Booking Opens"} ${shortDay(opens)} at ${bookingClock(j)}", color = Rw.colors.muted)
        }
        val facts = listOfNotNull(j.trainNumber?.takeIf { it.isNotBlank() }?.let { "Train" to it }, j.coach?.let { "Coach" to it },
            listOfNotNull(j.seat, j.berth?.let(::berthLabel)).joinToString(" ").takeIf { it.isNotEmpty() }?.let { "Berth" to it },
            j.pnr.takeIf { it.isNotEmpty() }?.let { "PNR" to "••" + it.takeLast(4) })
        if (facts.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { facts.forEach { (k, v) -> Fact(k, v, Rw.colors.text) } }
        when (j.status) {
            JourneyStatus.NeedsBooking -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("I’ve Booked It", { model.setStatus(j, JourneyStatus.Booked); close() }, Modifier.weight(1f))
                SecondaryButton("Skip", { confirm("Skip this trip? You won’t be reminded to book it.") { model.setStatus(j, JourneyStatus.Skipped); close() } })
            }
            JourneyStatus.Booked -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Needs Cancelling", { model.setStatus(j, JourneyStatus.CancellationNeeded); close() }, Modifier.weight(1f))
                // Undo a mistaken "booked": back to To book.
                if (j.pnr.isEmpty() && !dev.railwatch.shared.domain.hasTicket(j))
                    SecondaryButton("Not Booked", { confirm("Move this trip back to Planned?") { model.setStatus(j, JourneyStatus.NeedsBooking); close() } }, Modifier.weight(1f))
            }
            JourneyStatus.CancellationNeeded -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Cancelled on IRCTC", { model.setStatus(j, JourneyStatus.Cancelled); close() }, Modifier.weight(1f))
                SecondaryButton("Keep Ticket", { model.setStatus(j, JourneyStatus.Booked); close() }, Modifier.weight(1f))
            }
            JourneyStatus.Skipped, JourneyStatus.Cancelled -> SecondaryButton("Restore to Planned", { model.setStatus(j, JourneyStatus.NeedsBooking); close() })
            JourneyStatus.Completed -> {}
        }
        if (j.ruleId == null) Text("Delete Trip", style = MaterialTheme.typography.titleMedium, color = Rw.colors.danger,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirm("Delete this trip? This can’t be undone.") { model.deleteJourney(j); close() } }.padding(vertical = 8.dp))
    }
}

/** A station field with suggestions from the bundled list. Stores the code. */
@Composable
fun StationField(label: String, code: String, model: AppModel, onPick: (String) -> Unit) {
    var query by remember(code) { mutableStateOf(code) }
    var focused by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(query, { query = it; focused = true }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { model.stations.name(code)?.let { Text(it) } }, colors = fieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        if (focused && query.isNotBlank() && query.uppercase() != code) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface2)) {
                model.stations.search(query, 6).forEach { (c, n) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c); query = c; focused = false }.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text, modifier = Modifier.padding(end = 10.dp)); Text(n, style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Rw.colors.text, unfocusedTextColor = Rw.colors.text, focusedBorderColor = Rw.colors.brand,
    unfocusedBorderColor = Rw.colors.line, focusedLabelColor = Rw.colors.brand, unfocusedLabelColor = Rw.colors.muted, cursorColor = Rw.colors.brand)

/** A field that opens the app's date picker; value is "YYYY-MM-DD". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, min: String? = null) {
    var open by remember { mutableStateOf(false) }
    PickerRow(label, if (value.isEmpty()) "Choose" else longDay(value)) { open = true }
    if (open) {
        val millis = (value.ifEmpty { min ?: LocalDate.now().toString() }).let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        val state = rememberDatePickerState(initialSelectedDateMillis = millis)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = { TextButton({ state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

/** A field that opens the app's 24-hour time picker; value is "HH:MM". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerRow(label, value) { open = true }
    if (open) {
        val state = rememberTimePickerState(value.take(2).toIntOrNull() ?: 8, value.drop(3).toIntOrNull() ?: 0, is24Hour = true)
        androidx.compose.material3.AlertDialog(onDismissRequest = { open = false }, confirmButton = { TextButton({ onChange("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text("Done") } },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } }, text = { TimeInput(state) })
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface2).clickable(onClickLabel = label, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
    }
}

/** New one-off journey (bottom sheet). */
@Composable
fun NewJourneySheet(model: AppModel, close: () -> Unit) {
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val time = "00:00" // the real departure comes from the ticket
    val ok = from.isNotBlank() && to.isNotBlank() && from != to && date.isNotEmpty()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New Journey", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        StationField("From", from, model) { from = it }
        StationField("To", to, model) { to = it }
        DateField("Travel date", date, { date = it }, min = model.today)
        var reminders by remember { mutableStateOf<dev.railwatch.shared.domain.ReminderOverride?>(null) }
        if (date.isNotEmpty()) {
            val draft = Journey(id = "", from = from, to = to, date = date, departure = time, windowDays = model.planner.settings.bookingWindowDays)
            Text("Booking opens ${shortDay(bookingDay(draft))} at 08:00", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            JourneyReminders(model.planner, draft, reminders, model.today) { reminders = it }
        }
        PrimaryButton("Save Journey", {
            model.saveJourney(Journey(id = UUID.randomUUID().toString(), from = from, to = to, date = date, departure = time, windowDays = model.planner.settings.bookingWindowDays, reminderOverride = reminders)); close()
        }, enabled = ok)
    }
}

/** New or edited routine (bottom sheet): name, stations, days, start; an optional return; optionally linked to another routine. */
@Composable
fun RoutineSheet(model: AppModel, existing: Rule?, close: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "Weekly Office Travel") }
    var from by remember { mutableStateOf(existing?.from ?: "") }; var to by remember { mutableStateOf(existing?.to ?: "") }
    var days by remember { mutableStateOf(existing?.weekdays?.toSet() ?: setOf(2)) }
    var withReturn by remember { mutableStateOf(existing?.returnAfterDays != null) }
    var returnAfter by remember { mutableStateOf(existing?.returnAfterDays?.toString() ?: "2") }
    var start by remember { mutableStateOf(existing?.start ?: model.today) }
    var linked by remember { mutableStateOf(existing?.linkedRuleId) }
    val others = model.planner.rules.filter { it.id != existing?.id }
    val ok = name.isNotBlank() && from.isNotBlank() && to.isNotBlank() && from != to && days.isNotEmpty() && (!withReturn || returnAfter.isNotEmpty())
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (existing == null) "New Routine" else "Edit Routine", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        StationField("From", from, model) { from = it }
        StationField("To", to, model) { to = it }
        Text("Days", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 0 to "S").forEach { (d, l) ->
                val on = d in days
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) Rw.colors.brand else Rw.colors.surface2).clickable(onClickLabel = "Day $d") { days = if (on) days - d else days + d }, contentAlignment = Alignment.Center) {
                    Text(l, style = MaterialTheme.typography.titleMedium, color = if (on) Rw.colors.brandInk else Rw.colors.text)
                }
            }
        }
        DateField("Starts", start, { start = it })
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface2).clickable { withReturn = !withReturn }.heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Return Trip", style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
                Text("Plan the way back with each trip", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            }
            androidx.compose.material3.Switch(withReturn, { withReturn = it }, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Rw.colors.brand, checkedThumbColor = Rw.colors.brandInk))
        }
        if (withReturn) OutlinedTextField(returnAfter, { returnAfter = it.filter(Char::isDigit).take(2) }, label = { Text("Days after (0 = same day)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        if (others.isNotEmpty()) {
            Text("Linked Routine", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            Text("Link a separate return routine (for example onward Tue, back Thu) so they show together.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<Rule?>(null) + others).forEach { r ->
                    val on = linked == r?.id
                    Text(r?.name ?: "None", style = MaterialTheme.typography.titleMedium, color = if (on) Rw.colors.brandSoftInk else Rw.colors.text,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Rw.colors.brandSoft else Rw.colors.surface2).clickable { linked = r?.id }.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        PrimaryButton("Save Routine", {
            val id = existing?.id ?: UUID.randomUUID().toString()
            model.saveRoutine(Rule(id = id, name = name.trim(), from = from, to = to, start = start, weekdays = days.sorted(), linkedRuleId = linked,
                // Times come from the ticket; the routine keeps a placeholder departure only because the shared model needs one.
                departure = existing?.departure ?: "00:00", returnAfterDays = if (withReturn) returnAfter.toIntOrNull() else null,
                returnDeparture = existing?.returnDeparture?.ifEmpty { null } ?: "00:00", windowDays = model.planner.settings.bookingWindowDays))
            // Linking is two-way: the other routine points back at this one.
            if (linked != null) model.planner.rules.firstOrNull { it.id == linked && it.linkedRuleId != id }?.let { model.saveRoutine(it.copy(linkedRuleId = id)) }
            close()
        }, enabled = ok)
        if (existing != null) Text("Delete Routine", style = MaterialTheme.typography.titleMedium, color = Rw.colors.danger,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { model.deleteRoutine(existing.id); close() }.padding(vertical = 8.dp))
        Text("Booked trips and trips you changed are kept when you edit a routine.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
    }
}
