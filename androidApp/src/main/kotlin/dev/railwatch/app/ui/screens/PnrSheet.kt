package dev.railwatch.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.data.PnrClient
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.SecondaryButton
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.PnrSnapshot
import dev.railwatch.shared.domain.applyPnrStatus
import dev.railwatch.shared.domain.pnrSummary
import kotlinx.coroutines.launch

/** Check PNR: the enquiry's CAPTCHA picture, the person's answer, then the status. A cancelled PNR asks first. */
@Composable
fun PnrSheet(j: Journey, model: AppModel, confirm: (String, () -> Unit) -> Unit, close: () -> Unit) {
    val client = remember { PnrClient() }
    val scope = rememberCoroutineScope()
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PnrSnapshot?>(j.pnrStatus?.takeIf { false }) }
    LaunchedEffect(round) {
        loading = true; answer = ""
        picture = client.start()
        if (picture == null) error = "Indian Railways' PNR enquiry isn't answering right now. Try again in a little while."
        loading = false
    }
    val submit: () -> Unit = {
        if (answer.isNotBlank() && !loading) scope.launch {
            loading = true; error = null
            when (val r = client.finish(j.pnr, answer)) {
                is PnrClient.Result.Ok -> {
                    val updated = applyPnrStatus(j, r.snapshot); result = r.snapshot
                    // Whole PNR cancelled: the trip moves to Cancelled. Some passengers cancelled: flagged here and on the trip.
                    if (pnrSummary(r.snapshot) == "CANCELLED" && j.status != JourneyStatus.Cancelled) model.setStatus(updated, JourneyStatus.Cancelled)
                    else model.saveJourney(updated)
                }
                PnrClient.Result.WrongAnswer -> { error = "That answer didn't match. Try the new picture."; round++ }
                is PnrClient.Result.Failed -> { error = r.message; model.saveJourney(j.copy(pnrLastAttemptAt = kotlinx.datetime.Clock.System.now().toString(), pnrLastError = r.message)) }
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Check PNR", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        Text("${j.from} → ${j.to} · PNR ${j.pnr}", color = Rw.colors.muted)
        val r = result
        if (r != null) {
            val summary = pnrSummary(r)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (summary == "CNF") Rw.colors.bookedBg else Rw.colors.attentionBg).padding(18.dp)) {
                val partly = dev.railwatch.shared.domain.partlyCancelled(r)
                val ink = if (summary == "CNF") Rw.colors.bookedInk else Rw.colors.attentionInk
                Text(when { partly != null -> partly.replaceFirstChar { it.uppercase() }; summary == "CNF" -> "Confirmed"; summary == "RAC" -> "RAC: a shared berth"; summary == "WL" -> "Waitlisted"; summary == "CANCELLED" -> "Cancelled"; else -> "Mixed: see passengers" },
                    style = MaterialTheme.typography.headlineSmall, color = ink)
                Text(when {
                    summary == "CANCELLED" -> "Every passenger is cancelled, so the trip moved to Cancelled."
                    partly != null -> "The other passengers are still booked."
                    summary == "WL" -> "Not confirmed yet. Check again nearer the chart."
                    summary == "RAC" -> "You can travel; a full berth may come when the chart is prepared."
                    else -> r.chartStatus
                }, color = ink)
                if (r.chartStatus.isNotEmpty() && summary != "CANCELLED") Text(r.chartStatus, color = ink, style = MaterialTheme.typography.bodyMedium)
            }
            r.passengers.forEach { p ->
                val gone = dev.railwatch.shared.domain.isCancelled(p)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (gone) Rw.colors.cancelBg else Rw.colors.surface2).padding(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Passenger ${p.number}", color = if (gone) Rw.colors.cancelInk else Rw.colors.muted)
                        if (p.booking.isNotEmpty()) Text("Booked ${p.booking}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(if (gone) "Cancelled" else listOf(p.current, listOf(p.coach, p.berth).filter { it.isNotEmpty() && it != "0" }.joinToString(" / ")).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = if (gone) Rw.colors.cancelInk else Rw.colors.text, fontWeight = FontWeight.SemiBold)
                }
            }
            PrimaryButton("Done", close, Modifier.fillMaxWidth())
            return@Column
        }
        Text("Answer the question in the picture to check with Indian Railways.", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
            val pic = picture
            if (pic != null) Image(pic.asImageBitmap(), "CAPTCHA picture", contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().height(90.dp).padding(8.dp))
            else if (loading) CircularProgressIndicator(color = Rw.colors.brand)
        }
        Text("New picture", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(enabled = !loading) { error = null; round++ }.padding(vertical = 4.dp))
        OutlinedTextField(answer, { answer = it.take(12) }, label = { Text("Answer") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), keyboardActions = KeyboardActions(onGo = { submit() }))
        error?.let { Text(it, color = Rw.colors.danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(if (loading && picture != null) "Checking…" else "Check status", submit, Modifier.weight(1f), enabled = answer.isNotBlank() && !loading && picture != null)
            SecondaryButton("Cancel", close)
        }
    }
}
