package dev.railwatch.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.data.ImportRow
import dev.railwatch.app.data.readShared
import dev.railwatch.app.ui.berthLabel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.JourneyRow
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.MonthHeader
import dev.railwatch.app.ui.components.Perforation
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.SecondaryButton
import dev.railwatch.app.ui.dayNum
import dev.railwatch.app.ui.monthLabel
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.app.ui.weekdayShort
import dev.railwatch.shared.domain.Attachment
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.byMonth
import dev.railwatch.shared.domain.hasTicket
import dev.railwatch.shared.domain.tripTiming
import dev.railwatch.shared.parsing.TicketMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tickets: every journey with a stored PDF, newest trips first, by month. Tap opens the pass. */
@Composable
fun TicketsScreen(model: AppModel, open: (Journey) -> Unit, contentPadding: PaddingValues, header: @Composable () -> Unit = { LargeTitle("Tickets") }) {
    val today = model.today
    val list = model.planner.journeys.filter { hasTicket(it) }.sortedWith(compareByDescending<Journey> { it.date >= today }.thenBy { if (it.date >= today) it.date else "" }.thenByDescending { it.date })
    LazyColumn(contentPadding = contentPadding) {
        item { header() }
        if (list.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Rw.colors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No tickets yet", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
                Text("Open the IRCTC ticket PDF in Gmail or Files, tap Share and choose RailWatch.", style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted)
            }
        }
        byMonth(list).forEach { (month, trips) ->
            item(key = "m$month") { MonthHeader(monthLabel(month, today)) }
            item(key = "g$month") {
                Grouped { trips.forEachIndexed { i, j -> if (i > 0) RowDivider(); JourneyRow(dayNum(j.date), weekdayShort(j.date), "${j.from} → ${j.to}", listOfNotNull(j.trainNumber, listOfNotNull(j.coach, j.seat).joinToString(" / ").ifEmpty { null }).joinToString(" · "), toneOf(j), "ticket") { open(j) } } }
            }
        }
    }
}

/** Import sheet: reads every shared PDF, shows what it found and where it goes, then attaches them in one go. */
@Composable
fun ImportSheet(model: AppModel, uris: List<android.net.Uri>, close: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<ImportRow>?>(null) }
    var progress by remember { mutableStateOf("Reading ticket 1 of ${uris.size}…") }
    var rejected by remember { mutableStateOf(0) }
    LaunchedEffect(uris) {
        val (read, bad) = readShared(uris, model.ticketFiles, model.reader, { model.planner.journeys }) { i, n -> progress = "Reading ticket $i of $n…" }
        rows = read; rejected = bad
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (uris.size == 1) "Add ticket" else "Add ${uris.size} tickets", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        val list = rows
        if (list == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = Rw.colors.brand, modifier = Modifier.padding(end = 12.dp)); Text(progress, color = Rw.colors.text) }
        else {
            if (rejected > 0) Text("$rejected file${if (rejected > 1) "s weren’t" else " wasn’t"} an IRCTC PDF under 10 MB.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.attentionInk)
            list.forEachIndexed { i, r ->
                ImportCard(r, model) { choice -> rows = list.toMutableList().also { it[i] = r.copy(choice = choice) } }
            }
            val ready = list.count { it.ready }
            PrimaryButton(if (ready == 1) "Attach ticket" else "Attach $ready tickets", { val n = model.attach(list); close(if (n == 1) "Ticket added." else "$n tickets added.") }, enabled = ready > 0)
            SecondaryButton("Cancel", { list.forEach { model.ticketFiles.delete(it.file.id) }; close("") }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ImportCard(r: ImportRow, model: AppModel, choose: (String?) -> Unit) {
    val d = r.details
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rw.colors.surface2).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(listOfNotNull(d.date?.let(::shortDay), listOfNotNull(d.from, d.to).joinToString(" → ").ifEmpty { null }, d.ticketStatus).joinToString(" · ").ifEmpty { r.file.name },
            style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
        Text(listOfNotNull(d.trainNumber, d.coach?.let { c -> c + (d.seat?.let { "/$it" } ?: "") }, d.pnr?.let { "PNR ••" + it.takeLast(4) }, d.fare?.let { "₹%.2f".format(it.total) }).joinToString(" · ").ifEmpty { "Couldn’t read the details" },
            style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        val options: List<Pair<String?, String>> = when (val m = r.match) {
            is TicketMatch.Matched -> listOf<Pair<String?, String>>(m.journey.id to "Attach to ${m.journey.from} → ${m.journey.to}", null to "Skip")
            is TicketMatch.Check -> m.candidates.map { it.id to "${it.from} → ${it.to}" } + listOfNotNull(("create" to "New journey").takeIf { d.date != null && d.from != null && d.to != null }, null to "Skip")
            TicketMatch.Create -> listOfNotNull(("create" to "New booked journey").takeIf { d.date != null && d.from != null && d.to != null }, null to "Skip")
        }
        if (r.match is TicketMatch.Check) Text((r.match as TicketMatch.Check).reason, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.attentionInk)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val on = r.choice == value
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Rw.colors.brandInk else Rw.colors.text,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Rw.colors.brand else Rw.colors.surface).clickable { choose(value) }.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}

/** Ticket pass (canvas NativeTicket): large coach and berth, readable at a glance and offline; brightness turns up. */
@Composable
fun TicketPass(j: Journey, file: Attachment, model: AppModel, openPdf: () -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        val w = activity?.window; val before = w?.attributes?.screenBrightness
        w?.attributes = w?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = before ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
    val ink = Color(0xFF0B0F17); val paper = Color(0xFFF7F7FB); val muted = Color(0xFF4B5563)
    val context = LocalContext.current
    val timing = tripTiming(j)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(paper)) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row { Text(shortDay(j.date).uppercase(), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f)); j.ticketStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF15803D)) } }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) { Text(j.from, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ink, style = MaterialTheme.typography.displayLarge); Text(model.stations.name(j.from).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text(j.to, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ink, style = MaterialTheme.typography.displayLarge); Text(model.stations.name(j.to).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1) }
                }
                val times = dev.railwatch.shared.domain.tripTimes(j)
                if (times.depart != null) Row(verticalAlignment = Alignment.Bottom) {
                    DateTime(times.departDate, times.depart, ink, Alignment.Start, Modifier.weight(1f))
                    times.span?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(bottom = 4.dp)) }
                    if (times.arrive != null) DateTime(times.arriveDate!!, times.arrive, ink, Alignment.End, Modifier.weight(1f))
                }
            }
            if (j.coach != null || j.seat != null) Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ink).padding(vertical = 16.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("COACH", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA3B2)); Text(j.coach ?: "—", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = MaterialTheme.typography.displayLarge) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BERTH", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA3B2))
                    Text(j.seat ?: "—", fontSize = if ((j.seat?.length ?: 0) > 6) 24.sp else 46.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, style = MaterialTheme.typography.displayLarge, maxLines = 1)
                    j.berth?.let { Text(berthLabel(it), style = MaterialTheme.typography.titleMedium, color = Rw.colors.brand) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOfNotNull(j.trainNumber?.let { "TRAIN" to it }, j.travelClass.takeIf { it.isNotBlank() }?.let { "CLASS" to it }, j.pnr.takeIf { it.isNotEmpty() }?.let { "PNR" to it }).forEach { (k, v) ->
                    Column { Text(k, style = MaterialTheme.typography.labelSmall, color = muted); Text(v, style = MaterialTheme.typography.titleMedium, color = ink) }
                }
            }
            Perforation(notch = Rw.colors.surface, line = Color(0xFFD1D5DB))
            Text("Show this to the ticket checker with your photo ID. The PDF has the QR code.", style = MaterialTheme.typography.bodyMedium, color = muted, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 20.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Open PDF", openPdf, Modifier.weight(1f))
            SecondaryButton("Share", {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".tickets", model.ticketFiles.file(file.id))
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share ticket"))
            }, Modifier.weight(1f))
        }
    }
}

/** The original PDF, rendered page by page inside the app (pinch-free: full width, scroll). */
@Composable
fun PdfPages(model: AppModel, file: Attachment) {
    val pages by produceState(emptyList<Bitmap>(), file.id) { value = withContext(Dispatchers.IO) { (0 until minOf(model.ticketFiles.pageCount(file.id), 6)).mapNotNull { model.ticketFiles.render(file.id, it, 1400) } } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (pages.isEmpty()) Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Rw.colors.brand) }
        pages.forEach { Image(it.asImageBitmap(), contentDescription = "Ticket page", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) }
    }
}
