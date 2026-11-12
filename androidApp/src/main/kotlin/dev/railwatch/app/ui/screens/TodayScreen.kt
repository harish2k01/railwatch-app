package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.berthLabel
import dev.railwatch.app.ui.components.Fact
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.Perforation
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.components.SectionHead
import dev.railwatch.app.ui.dayMonth
import dev.railwatch.app.ui.longDay
import dev.railwatch.app.ui.relative
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.statusLabel
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.DoNext
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.addDays
import dev.railwatch.shared.domain.doNext
import dev.railwatch.shared.domain.nextTrip
import dev.railwatch.shared.domain.tripTiming
import dev.railwatch.shared.domain.upcoming

/** Today (canvas NativeToday): large title, boarding-pass next trip, Do next cards, this week. */
@Composable
fun TodayScreen(model: AppModel, open: (Journey) -> Unit, showTicket: (Journey) -> Unit, inbox: () -> Unit = {}, unread: Int = 0, newRoutine: () -> Unit, seeAll: () -> Unit, contentPadding: PaddingValues) {
    val p = model.planner; val today = model.today
    val next = nextTrip(p, today)
    val cards = doNext(p, today)
    val week = upcoming(p, today).filter { it.date <= addDays(today, 6) && it.id != next?.id }.take(4)
    LazyColumn(contentPadding = contentPadding) {
        item { LargeTitle("Today", eyebrow = longDay(today)) { Bell(unread, inbox) } }
        item {
            if (next != null) BoardingPass(next, today, model.stations::name, { showTicket(next) }) { open(next) }
            else EmptyStart(hasRoutines = p.rules.isNotEmpty(), newRoutine = newRoutine)
        }
        if (cards.isNotEmpty()) {
            item { SectionHead("Do Next", "See All", seeAll) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(cards, key = { it::class.simpleName + it.journey.id }) { c -> DoCard(c) { if (c is DoNext.BookMany) seeAll() else open(c.journey) } } }
            }
        }
        // This week: trips in the next 7 days, and the days booking opens for later trips.
        val end = addDays(today, 6)
        val live = p.journeys.filter { it.status == JourneyStatus.NeedsBooking || it.status == JourneyStatus.Booked || it.status == JourneyStatus.CancellationNeeded }
        val entries = (live.filter { it.date in today..end }.map { Triple(it.date, it, false) } +
            live.filter { it.status == JourneyStatus.NeedsBooking && dev.railwatch.shared.domain.bookingDay(it) in today..end && it.date > end }.map { Triple(dev.railwatch.shared.domain.bookingDay(it), it, true) })
            .sortedBy { it.first }.take(6)
        item { SectionHead("This Week") }
        item {
            if (entries.isEmpty()) Text("Nothing in the next 7 days.", color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp))
            else Grouped {
                entries.forEachIndexed { i, (day, j, booking) ->
                    if (i > 0) RowDivider()
                    WeekRow(day, j, booking, today) { open(j) }
                }
            }
        }
    }
}

/** One This-week row: a trip (with a Book pill while booking is open) or a booking day for a later trip. */
@Composable
private fun WeekRow(day: String, j: Journey, booking: Boolean, today: String, onClick: () -> Unit) {
    val c = Rw.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.padding(end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dev.railwatch.app.ui.dayNum(day), color = c.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(dev.railwatch.app.ui.weekdayShort(day), color = c.muted, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(if (booking) "Booking Day" else "${j.from} → ${j.to}", color = c.text, style = MaterialTheme.typography.titleMedium)
            Text(if (booking) "${dev.railwatch.shared.domain.bookingClock(j)} · ${dev.railwatch.app.ui.dayMonth(j.date)} trip" else dev.railwatch.app.ui.journeyLine(j, today), color = c.muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (!booking && j.status == JourneyStatus.NeedsBooking && dev.railwatch.shared.domain.bookingDay(j) <= today)
            Text("Book", color = c.attentionInk, style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(c.attentionBg, androidx.compose.foundation.shape.RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 5.dp))
        else androidx.compose.material3.Icon(RwIcons.ChevronRight, null, tint = c.muted)
    }
}

/** The next 7 days: travel (purple dot), booking opens (blue dot), time off (shaded). */
@Composable
private fun WeekStrip(p: dev.railwatch.shared.domain.Planner, today: String, pick: (String) -> Unit) {
    val c = Rw.colors
    val live = p.journeys.filter { it.status == JourneyStatus.NeedsBooking || it.status == JourneyStatus.Booked || it.status == JourneyStatus.CancellationNeeded }
    val off = p.holidays.map { it.date }.toSet()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until 7).forEach { i ->
            val d = addDays(today, i)
            val travel = live.any { it.date == d }
            val opens = live.any { it.status == JourneyStatus.NeedsBooking && dev.railwatch.shared.domain.bookingDay(it) == d }
            val bg = when { i == 0 -> c.brand; d in off -> c.timeoffBg; else -> c.surface }
            val ink = if (i == 0) c.brandInk else c.text
            Column(Modifier.weight(1f).background(bg, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { pick(d) }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dev.railwatch.app.ui.weekdayShort(d).take(1), color = ink.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                Text(dev.railwatch.app.ui.dayNum(d).trimStart('0'), color = ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (travel) Spacer(Modifier.size(5.dp).background(if (i == 0) ink else c.brand, androidx.compose.foundation.shape.CircleShape))
                    if (opens) Spacer(Modifier.size(5.dp).background(if (i == 0) ink else c.open, androidx.compose.foundation.shape.CircleShape))
                }
            }
        }
    }
}

/** The trip length over a dashed arrow, between the two stations. */
@Composable
fun TripSpan(span: String?, ink: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (span != null) Text(span, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ink.copy(alpha = .85f))
        androidx.compose.foundation.Canvas(Modifier.padding(top = 4.dp).size(width = 64.dp, height = 12.dp)) {
            val y = size.height / 2; val c = ink.copy(alpha = .7f); val w = 2.dp.toPx()
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width - 2.dp.toPx(), y), w,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), y - 5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - w / 2, y), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(c, androidx.compose.ui.geometry.Offset(size.width - 7.dp.toPx(), y + 5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - w / 2, y), w, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** A date in small type above its time (the time alone when unknown): "Wed 18 Nov" over "07:10". */
@Composable
fun DateTime(day: String, time: String?, ink: androidx.compose.ui.graphics.Color, align: Alignment.Horizontal, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = align) {
        Text(shortDay(day), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = .7f))
        Text(time ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ink)
    }
}

/** The next trip as a boarding pass; booked trips use the accent colour. */
@Composable
private fun BoardingPass(j: Journey, today: String, name: (String) -> String?, ticket: () -> Unit, onClick: () -> Unit) {
    val booked = j.status == JourneyStatus.Booked
    val bg = if (booked) Rw.colors.brand else Rw.colors.surface
    val ink = if (booked) Rw.colors.brandInk else Rw.colors.text
    val times = dev.railwatch.shared.domain.tripTimes(j)
    val span = times.span
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick)
        .semantics { contentDescription = "Next trip ${j.from} to ${j.to}, ${longDay(j.date)}, ${statusLabel(j)}" }) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NEXT TRIP · ${relative(today, j.date).uppercase()}", style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = .75f), modifier = Modifier.weight(1f))
                Text(statusLabel(j), style = MaterialTheme.typography.labelMedium, color = if (booked) Rw.colors.brand else Rw.colors.text,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (booked) Rw.colors.brandInk else Rw.colors.surface2).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Stop(j.from, name(j.from), ink, Modifier.weight(1f), Alignment.Start)
                TripSpan(span, ink, Modifier.padding(bottom = 18.dp, start = 6.dp, end = 6.dp))
                Stop(j.to, name(j.to), ink, Modifier.weight(1f), Alignment.End)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                DateTime(times.departDate, times.depart, ink, Alignment.Start, Modifier.weight(1f))
                if (times.arrive != null) DateTime(times.arriveDate!!, times.arrive, ink, Alignment.End)
            }
        }
        val facts = listOfNotNull(j.trainNumber?.takeIf { it.isNotBlank() }?.let { "Train" to it }, j.coach?.let { "Coach" to it },
            listOfNotNull(j.seat, j.berth?.let(::berthLabel)).joinToString(" ").takeIf { it.isNotEmpty() }?.let { "Berth" to it }, j.travelClass.takeIf { it.isNotBlank() }?.let { "Class" to it })
        if (facts.isNotEmpty()) {
            Perforation(notch = Rw.colors.ground, line = ink.copy(alpha = .3f))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) { facts.forEach { (k, v) -> Fact(k, v, ink) } }
            if (dev.railwatch.shared.domain.hasTicket(j)) androidx.compose.material3.Text("Show Ticket", color = Rw.colors.brand, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).background(ink, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable(onClick = ticket).padding(vertical = 12.dp))
        } else Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Stop(code: String, name: String?, ink: Color, modifier: Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align) {
        Text(code, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = ink, style = MaterialTheme.typography.displayLarge)
        if (name != null) Text(name, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = .75f), maxLines = 1)
    }
}

/** One Do next card. */
@Composable
private fun DoCard(c: DoNext, onClick: () -> Unit) {
    val (bg, ink, icon, title, line) = when (c) {
        is DoNext.BookNow -> Quint(Rw.colors.attentionBg, Rw.colors.attentionInk, RwIcons.Ticket, "Book Now", "Booking window is open")
        is DoNext.BookMany -> Quint(Rw.colors.attentionBg, Rw.colors.attentionInk, RwIcons.Ticket, "${c.count} trips to book", "Booking Is Open · soonest")
        is DoNext.OpensSoon -> Quint(Rw.colors.openBg, Rw.colors.openInk, RwIcons.Clock, "Booking Opens", shortDay(c.opens) + " · 08:00")
        is DoNext.ConfirmCancel -> Quint(Rw.colors.cancelBg, Rw.colors.cancelInk, RwIcons.Close, "Confirm cancellation", "Cancelled on IRCTC?")
        is DoNext.NeedsTicket -> Quint(Rw.colors.attentionBg, Rw.colors.attentionInk, RwIcons.Ticket, "Share the Ticket", "Then it’s one tap away")
    }
    Column(Modifier.width(168.dp).heightIn(min = 136.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, contentDescription = null, tint = ink)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = ink)
            Text("${c.journey.from} → ${c.journey.to} · ${dayMonth(c.journey.date)}", style = MaterialTheme.typography.bodyMedium, color = ink)
        }
    }
}

private data class Quint(val bg: Color, val ink: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val line: String)

/** First run / nothing planned: start with a routine. */
@Composable
private fun EmptyStart(hasRoutines: Boolean, newRoutine: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Rw.colors.surface).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(RwIcons.Calendar, contentDescription = null, tint = Rw.colors.brand)
        Text(if (hasRoutines) "No trips coming up" else "Start with your regular trip", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        Text(if (hasRoutines) "Your routines have nothing planned ahead. Plan a one-off trip with +." else "Set up your weekly route once. RailWatch plans each journey and reminds you before booking opens.",
            style = MaterialTheme.typography.bodyLarge, color = Rw.colors.muted)
        if (!hasRoutines) PrimaryButton("Set Up a Routine", newRoutine)
    }
}

/** Status dot colour for a journey. */
fun toneOf(j: Journey) = when (j.status) {
    JourneyStatus.Booked -> if (dev.railwatch.shared.domain.effectiveTicketStatus(j) in setOf("WL", "RAC") || j.pnrStatus?.let { dev.railwatch.shared.domain.partlyCancelled(it) } != null) dev.railwatch.app.ui.components.Tone.Warn else dev.railwatch.app.ui.components.Tone.Ok
    JourneyStatus.NeedsBooking -> dev.railwatch.app.ui.components.Tone.Info
    JourneyStatus.CancellationNeeded -> dev.railwatch.app.ui.components.Tone.Warn
    else -> dev.railwatch.app.ui.components.Tone.Muted
}
