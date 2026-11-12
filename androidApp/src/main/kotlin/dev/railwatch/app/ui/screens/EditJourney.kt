package dev.railwatch.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.hasTicket

/** The quick actions under a journey's title (canvas NativeJourneySheet). */
@Composable
fun QuickActions(j: Journey, model: AppModel, onTicket: () -> Unit, onPnr: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    // Share sends the ticket PDF itself, so it shows only once a ticket is attached.
    val pdf = j.attachments?.firstOrNull { it.type == "application/pdf" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Quick(RwIcons.Ticket, "Ticket", primary = true, Modifier.weight(1f), onTicket)
        Quick(RwIcons.Clock, "Check PNR", false, Modifier.weight(1f), onPnr)
        if (pdf != null) Quick(RwIcons.Share, "Share", false, Modifier.weight(1f)) {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".tickets", model.ticketFiles.file(pdf.id))
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ticket"))
        }
        Quick(RwIcons.Pencil, "Edit", false, Modifier.weight(1f), onEdit)
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 66.dp).clip(RoundedCornerShape(16.dp)).background(if (primary) Rw.colors.brand else Rw.colors.surface2).clickable(onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = if (primary) Rw.colors.brandInk else Rw.colors.text)
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (primary) Rw.colors.brandInk else Rw.colors.text, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Edit a journey's ticket details by hand: PNR, train, class, coach, seat, berth, departure and notes. */
@Composable
fun EditJourneySheet(j: Journey, model: AppModel, close: () -> Unit) {
    var pnr by remember { mutableStateOf(j.pnr) }
    var train by remember { mutableStateOf(j.trainNumber.orEmpty()) }
    var trainName by remember { mutableStateOf(j.trainName.orEmpty()) }
    var cls by remember { mutableStateOf(j.travelClass) }
    var coach by remember { mutableStateOf(j.coach.orEmpty()) }
    var seat by remember { mutableStateOf(j.seat.orEmpty()) }
    var berth by remember { mutableStateOf(j.berth.orEmpty()) }
    var departure by remember { mutableStateOf(if (j.departureConfirmed == true) j.departure else "") }
    var notes by remember { mutableStateOf(j.notes) }
    val pnrOk = pnr.isEmpty() || pnr.length == 10
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Edit ${j.from} → ${j.to}", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        Text(shortDay(j.date), style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        OutlinedTextField(pnr, { pnr = it.filter(Char::isDigit).take(10) }, label = { Text("PNR (10 digits)") }, singleLine = true, isError = !pnrOk,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(train, { train = it.filter(Char::isDigit).take(5) }, label = { Text("Train no.") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), colors = fieldColors())
            OutlinedTextField(cls, { cls = it.uppercase().take(3) }, label = { Text("Class") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.weight(1f), colors = fieldColors())
        }
        OutlinedTextField(trainName, { trainName = it.take(100) }, label = { Text("Train name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(coach, { coach = it.uppercase().take(20) }, label = { Text("Coach") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.weight(1f), colors = fieldColors())
            OutlinedTextField(seat, { seat = it.take(30) }, label = { Text("Seat") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
        }
        OutlinedTextField(berth, { berth = it.take(30) }, label = { Text("Berth (e.g. Upper, LB)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        TimeField("Departure", departure.ifEmpty { "—" }) { departure = it }
        OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        var reminders by remember { mutableStateOf(j.reminderOverride) }
        if (j.status == dev.railwatch.shared.domain.JourneyStatus.NeedsBooking) JourneyReminders(model.planner, j, reminders, model.today) { reminders = it }
        PrimaryButton("Save", {
            model.saveJourney(j.copy(reminderOverride = reminders, pnr = pnr, trainNumber = train.ifBlank { null }, trainName = trainName.ifBlank { null }, travelClass = cls, coach = coach.ifBlank { null },
                seat = seat.ifBlank { null }, berth = berth.ifBlank { null }, departure = departure.ifEmpty { j.departure }, departureConfirmed = if (departure.isNotEmpty()) true else j.departureConfirmed,
                notes = notes, manualOverride = if (j.ruleId != null) true else j.manualOverride))
            close()
        }, enabled = pnrOk)
    }
}
