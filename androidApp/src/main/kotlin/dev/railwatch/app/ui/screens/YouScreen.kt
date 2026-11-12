package dev.railwatch.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.components.Segmented
import dev.railwatch.app.ui.monthLabel
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.QuietHours
import dev.railwatch.shared.domain.Rule
import dev.railwatch.shared.domain.ScheduleRow
import dev.railwatch.shared.domain.hasTicket
import dev.railwatch.shared.domain.spendByMonth
import dev.railwatch.shared.domain.timeOffConflicts

private enum class Page(val title: String, val icon: ImageVector) {
    Routines("Routines", RwIcons.List), TimeOff("Holidays & Leave", RwIcons.Calendar), Tickets("Tickets", RwIcons.Ticket),
    Spend("Spend", RwIcons.Ticket), Reminders("Reminders", RwIcons.Bell), Planning("Planning", RwIcons.Clock), Backup("Backup & Restore", RwIcons.Ticket), PhoneCal("Phone Calendar", RwIcons.Calendar), Help("Help & Tour", RwIcons.List), About("About RailWatch", RwIcons.Person), Permissions("Permissions", RwIcons.Check),
    Settings("Settings", RwIcons.Clock);
    /** Pages opened from Settings go back to Settings; the rest go back to More. */
    val parent: Page? get() = if (this in setOf(Reminders, Planning, Backup, PhoneCal, Permissions)) Settings else null
}

/** A single-choice popup, as in phone settings. */
private data class Choice(val title: String, val options: List<Pair<String, String>>, val selected: String, val pick: (String) -> Unit)

/** You: a list of setting categories; each opens its own page, and choices open as popups. */
@Composable
fun YouScreen(model: AppModel, editRoutine: (Rule?) -> Unit, open: (Journey) -> Unit, showTicket: (Journey) -> Unit, info: (String) -> Unit, contentPadding: PaddingValues,
              editTimeOff: (dev.railwatch.shared.domain.TimeOffRange?) -> Unit = {}, tour: () -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    BackHandler(enabled = page != null) { page = page?.parent }
    val p = model.planner; val today = model.today

    val header: @Composable () -> Unit = {
        val pg = page
        if (pg == null) LargeTitle("More")
        else Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RwIcons.ChevronLeft, "Back", tint = Rw.colors.brand, modifier = Modifier.size(44.dp).clickable { page = page?.parent }.padding(8.dp))
            Text(pg.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text)
        }
    }
    if (page == Page.Tickets) { TicketsScreen(model, showTicket, contentPadding, header); return }

    LazyColumn(contentPadding = contentPadding) {
        item { header() }
        when (page) {
            null -> item {
                val conflicts = timeOffConflicts(p, today).size
                val nextWeekend = dev.railwatch.shared.domain.longWeekends(p, today).firstOrNull()
                val month = spendByMonth(p).firstOrNull { it.month == today.take(7) }
                // Your travel at a glance: four tiles, each opening its own screen.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Page.Routines, p.rules.size.toString(), if (p.rules.isEmpty()) "Plan a regular trip" else if (p.rules.size == 1) "routine" else "routines", Modifier.weight(1f)) { page = Page.Routines }
                        Tile(Page.TimeOff, if (conflicts > 0) conflicts.toString() else p.holidays.count { it.date >= today }.toString(),
                            if (conflicts > 0) "trip${if (conflicts == 1) "" else "s"} on time off" else nextWeekend?.let { "days off · long weekend ${dev.railwatch.app.ui.dayMonth(it.start)}" } ?: "days off ahead", Modifier.weight(1f), alert = conflicts > 0) { page = Page.TimeOff }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(Page.Tickets, p.journeys.count { hasTicket(it) }.toString(), "tickets saved", Modifier.weight(1f)) { page = Page.Tickets }
                        Tile(Page.Spend, rupees(month?.amount ?: 0.0), "this month", Modifier.weight(1f)) { page = Page.Spend }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Grouped {
                    Category(Page.Settings.icon, "Settings", "Reminders, planning, appearance, permissions, calendar and backup", alert = false) { page = Page.Settings }
                }
                Spacer(Modifier.height(16.dp))
                Grouped {
                    Category(Page.Help.icon, "Help & Tour", "What each part of RailWatch does, and how to use it", alert = false) { tour() }
                    RowDivider()
                    Category(Page.About.icon, "About RailWatch", "Version, developer, licence and privacy policy", alert = false) { page = Page.About }
                }
            }
            Page.Settings -> item {
                Text("ALERTS", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp))
                Grouped {
                    Category(Page.Reminders.icon, "Reminders", "When booking, Tatkal, PNR and cancellation alerts reach you", alert = false) { page = Page.Reminders }
                    RowDivider()
                    Category(Page.Permissions.icon, "Permissions", "Notifications, alarms, calendar and battery", alert = false) { page = Page.Permissions }
                }
                Text("PLANS", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                Grouped {
                    Category(Page.Planning.icon, "Planning", "How far ahead routines plan, how long tickets are kept", alert = false) { page = Page.Planning }
                    RowDivider()
                    Category(Page.PhoneCal.icon, "Phone Calendar", "Add your trips to the calendar on this phone", alert = false) { page = Page.PhoneCal }
                }
                Text("APP", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                Grouped {
                    Category(Page.Planning.icon, "Appearance", when (p.settings.theme) { "light" -> "Light"; "dark" -> "Dark"; else -> "Match the phone" }, alert = false) {
                        choice = Choice("Appearance", listOf("system" to "Match the Phone", "light" to "Light", "dark" to "Dark"), p.settings.theme) { v -> model.setSettings { it.copy(theme = v) } }
                    }
                    RowDivider()
                    Category(Page.Backup.icon, "Backup & Restore", "Save everything to one file, or bring it back", alert = false) { page = Page.Backup }
                }
            }
            Page.Routines -> routines(model, editRoutine)
            Page.TimeOff -> item { TimeOffPage(model, open, info, editTimeOff) }
            Page.Spend -> item { SpendPage(model, open) }
            Page.Reminders -> item { RemindersPage(model) { choice = it } }
            Page.Planning -> item { PlanningPage(model) { choice = it } }
            Page.Backup -> item { BackupPage(model, info) }
            Page.PhoneCal -> item { PhoneCalendarPage(info) }
            Page.Help -> {}
            Page.About -> item { AboutPage() }
            Page.Permissions -> item { dev.railwatch.app.ui.PermissionsList() }
            Page.Tickets -> {}
        }
    }
    choice?.let { c ->
        AlertDialog(onDismissRequest = { choice = null }, containerColor = Rw.colors.surface,
            title = { Text(c.title, color = Rw.colors.text) },
            text = {
                Column {
                    c.options.forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { c.pick(value); choice = null }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(value == c.selected, { c.pick(value); choice = null }, colors = RadioButtonDefaults.colors(selectedColor = Rw.colors.brand))
                            Text(label, color = Rw.colors.text)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choice = null }) { Text("Cancel") } })
    }
}

private fun rupees(v: Double) = "₹" + "%,.0f".format(java.util.Locale.forLanguageTag("en-IN"), v)

/** Routines as cards: route, the days it runs, what's next, and how many trips it has planned. */
private fun LazyListScope.routines(model: AppModel, editRoutine: (Rule?) -> Unit) = item {
    val p = model.planner; val today = model.today
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.rules.isEmpty()) Text("A routine plans a regular trip months ahead, so every booking day reaches you without adding trips one by one.", color = Rw.colors.muted)
        p.rules.forEach { r ->
            val trips = p.journeys.filter { it.ruleId == r.id && it.date >= today }
            val next = trips.minByOrNull { it.date }
            val booked = trips.count { it.status == dev.railwatch.shared.domain.JourneyStatus.Booked }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Rw.colors.surface).clickable { editRoutine(r) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, color = Rw.colors.muted, modifier = Modifier.weight(1f))
                    if (r.paused) Text("Paused", style = MaterialTheme.typography.labelMedium, color = Rw.colors.attentionInk, modifier = Modifier.background(Rw.colors.attentionBg, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text("${r.from} → ${r.to}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..6).map { (it + 1) % 7 }.forEach { d ->
                        val on = d in r.weekdays
                        Box(Modifier.size(30.dp).background(if (on) Rw.colors.brand else Rw.colors.surface2, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                            Text(letters[d], style = MaterialTheme.typography.labelMedium, color = if (on) Rw.colors.brandInk else Rw.colors.muted)
                        }
                    }
                }
                Text(listOfNotNull(next?.let { "Next ${shortDay(it.date)}" }, "${trips.size} planned", "$booked booked", r.returnAfterDays?.let { "return after $it day${if (it == 1) "" else "s"}" }).joinToString(" · "),
                    color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PrimaryButton("New Routine", { editRoutine(null) }, Modifier.fillMaxWidth())
    }
}

/** Spend: this year's total, the average per trip, refunds, a 12-month bar chart; tap a month to see its trips. */
@Composable
private fun SpendPage(model: AppModel, open: (Journey) -> Unit) {
    val p = model.planner; val today = model.today
    val paid = p.journeys.filter { it.fare != null && it.status != dev.railwatch.shared.domain.JourneyStatus.Skipped }
    fun net(j: Journey) = j.fare!!.let { it.total - (it.refund ?: 0.0) }
    // The last 8 months and the next 4 (booked trips already have their fares).
    val months = (7 downTo -4).map { back -> val t = today.take(4).toInt() * 12 + today.substring(5, 7).toInt() - 1 - back; "%04d-%02d".format(t / 12, t % 12 + 1) }
    val byMonth = paid.groupBy { it.date.take(7) }
    var picked by remember { mutableStateOf(today.take(7)) }
    val year = paid.filter { it.date.take(4) == today.take(4) }
    val refunds = paid.sumOf { it.fare!!.refund ?: 0.0 }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("This Year", rupees(year.sumOf(::net)), Modifier.weight(1f))
            Stat("Per Trip", if (year.isEmpty()) "—" else rupees(year.sumOf(::net) / year.size), Modifier.weight(1f))
            Stat("Refunds", rupees(refunds), Modifier.weight(1f))
        }
        // Bars for the last 12 months; the chosen month is highlighted.
        val max = months.maxOf { m -> byMonth[m].orEmpty().sumOf(::net) }.coerceAtLeast(1.0)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Rw.colors.surface).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                months.forEach { m ->
                    val amount = byMonth[m].orEmpty().sumOf(::net)
                    val frac = (amount / max).toFloat()
                    Box(Modifier.weight(1f).height((8 + 140 * frac).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (m == picked) Rw.colors.brand else if (amount > 0) Rw.colors.brandSoft else Rw.colors.surface2).clickable { picked = m })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                months.forEach { m -> Text(monthLabel(m, today).take(1), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (m == picked) Rw.colors.text else Rw.colors.muted) }
            }
        }
        val trips = byMonth[picked].orEmpty().sortedBy { it.date }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(monthLabel(picked, today), style = MaterialTheme.typography.titleLarge, color = Rw.colors.text, modifier = Modifier.weight(1f))
            Text(rupees(trips.sumOf(::net)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Rw.colors.text)
        }
        if (trips.isEmpty()) Text("No fares this month. Fares are read from the tickets you add.", color = Rw.colors.muted)
        else Grouped {
            trips.forEachIndexed { i, j ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { open(j) }.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${j.from} → ${j.to}", color = Rw.colors.text, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(shortDay(j.date), j.travelClass.takeIf { it.isNotBlank() }, j.fare!!.refund?.let { "refund ${rupees(it)}" }, j.status.takeIf { it == dev.railwatch.shared.domain.JourneyStatus.Cancelled }?.let { "cancelled" }).joinToString(" · "),
                            color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(rupees(net(j)), color = Rw.colors.text, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Rw.colors.surface).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Rw.colors.muted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Rw.colors.text, maxLines = 1)
    }
}

/** One area of More as a tile: icon, a big number and what it counts. */
@Composable
private fun Tile(page: Page, value: String, sub: String, modifier: Modifier, alert: Boolean = false, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 132.dp).clip(RoundedCornerShape(20.dp)).background(if (alert) Rw.colors.attentionBg else Rw.colors.surface).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Rw.colors.brandSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(page.icon, null, tint = Rw.colors.brandSoftInk, modifier = Modifier.size(18.dp)) }
            Text(page.title, style = MaterialTheme.typography.titleSmall, color = Rw.colors.text, modifier = Modifier.padding(start = 10.dp), maxLines = 1)
        }
        Spacer(Modifier.size(6.dp))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (alert) Rw.colors.attentionInk else Rw.colors.text, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (alert) Rw.colors.attentionInk else Rw.colors.muted, maxLines = 2)
    }
}

@Composable
private fun TimeOffPage(model: AppModel, open: (Journey) -> Unit, info: (String) -> Unit, edit: (dev.railwatch.shared.domain.TimeOffRange?) -> Unit) {
    val p = model.planner; val today = model.today
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(2_000_000).toByteArray().decodeToString() } }.getOrNull()
        info(if (text == null) "Couldn't open that file." else model.importTimeOff(text, uri.lastPathSegment.orEmpty(), "company"))
    }
    var adding by remember { mutableStateOf(false) }
    val conflicts = timeOffConflicts(p, today)
    val ranges = dev.railwatch.shared.domain.timeOffRanges(p, today)
    val weekends = dev.railwatch.shared.domain.longWeekends(p, today)
    fun span(a: String, b: String) = if (a == b) shortDay(a) else "${dev.railwatch.app.ui.dayMonth(a)} – ${dev.railwatch.app.ui.dayMonth(b)}"
    Column {
        if (conflicts.isNotEmpty()) {
            Grouped {
                Text("${conflicts.size} trip${if (conflicts.size == 1) "" else "s"} on your time off", fontWeight = FontWeight.SemiBold, color = Rw.colors.attentionInk, modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp))
                conflicts.take(5).forEach { j ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${shortDay(j.date)} · ${j.from} → ${j.to}", color = Rw.colors.text, modifier = Modifier.weight(1f).clickable { open(j) })
                        Text(if (j.status == dev.railwatch.shared.domain.JourneyStatus.Booked) "Cancel" else "Skip", color = Rw.colors.danger, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { model.setStatus(j, if (j.status == dev.railwatch.shared.domain.JourneyStatus.Booked) dev.railwatch.shared.domain.JourneyStatus.CancellationNeeded else dev.railwatch.shared.domain.JourneyStatus.Skipped) }.padding(8.dp))
                        Text("Keep", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { model.keepOnTimeOff(j) }.padding(8.dp))
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
            Spacer(Modifier.size(12.dp))
        }
        Grouped {
            ListRow("Add Time Off", "Leave or a holiday, one day or several") { edit(null) }
            RowDivider()
            ListRow("Import a Calendar", "An .ics file, or a CSV with date,name,type") { pick.launch(arrayOf("text/calendar", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
        }
        if (weekends.isNotEmpty()) {
            Text("LONG WEEKENDS", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
            Grouped {
                weekends.take(6).forEachIndexed { i, w ->
                    if (i > 0) RowDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("${span(w.start, w.end)} · ${w.days} days", color = Rw.colors.text, fontWeight = FontWeight.SemiBold)
                        Text("${w.names.joinToString(", ")} · ${if (w.bookingOpen) "booking is open" else "book from ${shortDay(w.planBy)}"}", color = if (w.bookingOpen) Rw.colors.openInk else Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (ranges.isNotEmpty()) {
            Text("COMING UP", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
            Grouped {
                ranges.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    // Tap to edit (dates, name, kind) or remove it there.
                    Row(Modifier.fillMaxWidth().clickable { edit(r) }.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(if (r.type == "leave") Rw.colors.open else Rw.colors.brand, androidx.compose.foundation.shape.CircleShape))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(r.name, color = Rw.colors.text, style = MaterialTheme.typography.titleMedium)
                            val n = r.ids.size
                            Text("${span(r.start, r.end)}${if (n > 1) " · $n days" else ""} · ${if (r.type == "leave") "Leave" else "Holiday"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(RwIcons.Pencil, "Edit ${r.name}", tint = Rw.colors.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersPage(model: AppModel, ask: (Choice) -> Unit) {
    val s = model.planner.settings; val clock = s.reminderClock
    val times = HALF_HOURS.map { it to it }
    fun head(t: String) = @Composable { Text(t, style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)) }
    fun help(t: String) = @Composable { Text(t, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
    Column {
        // General booking: three simple reminders, or your own schedule of reminders.
        head("GENERAL BOOKING (OPENS 08:00, ${s.bookingWindowDays} DAYS AHEAD)")()
        // One list of reminders. Settings without a saved list start from the evening / morning / opening times.
        val rows = s.bookingSchedule ?: listOfNotNull(
            ScheduleRow(1, clock.previous_evening).takeIf { "previous_evening" in s.reminderTimes },
            ScheduleRow(0, clock.morning).takeIf { "morning" in s.reminderTimes },
            ScheduleRow(0, clock.opening).takeIf { "opening" in s.reminderTimes })
        ScheduleEditor(rows) { r -> model.setSettings { it.copy(bookingSchedule = r) } }
        help("Applies to every trip unless the trip or its routine has its own reminders. Tap a day or time to change it.")()

        // Tatkal opens the day before travel: 10:00 for AC classes, 11:00 for non-AC.
        head("TATKAL (AC OPENS 10:00, NON-AC 11:00, THE DAY BEFORE)")()
        Text("AC Classes", style = MaterialTheme.typography.titleMedium, color = Rw.colors.text, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        ScheduleEditor(dev.railwatch.shared.domain.tatkalSchedule(s, false)) { rows -> model.setSettings { it.copy(tatkalSchedule = rows) } }
        Text("Non-AC Classes", style = MaterialTheme.typography.titleMedium, color = Rw.colors.text, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))
        ScheduleEditor(dev.railwatch.shared.domain.tatkalSchedule(s, true)) { rows -> model.setSettings { it.copy(tatkalNonAcSchedule = rows) } }
        if (s.tatkalSchedule != null || s.tatkalNonAcSchedule != null) Text("Reset Tatkal to Defaults", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { model.setSettings { it.copy(tatkalSchedule = null, tatkalNonAcSchedule = null) } }.padding(start = 4.dp, top = 10.dp))

        head("AFTER BOOKING")()
        Grouped {
            Toggle("Cancellation Follow-Up", s.cancellationEnabled) { on -> model.setSettings { it.copy(cancellationEnabled = on) } }
            if (s.cancellationEnabled) { RowDivider(); ValueRow("Follow-Up Time", s.cancellationTime) { ask(Choice("Follow-Up Time", times, s.cancellationTime) { v -> model.setSettings { it.copy(cancellationTime = v) } }) } }
        }
        help("When you mark a ticket to cancel, a daily reminder at this time until you mark it cancelled on IRCTC.")()
        Spacer(Modifier.height(12.dp))
        Grouped {
            Toggle("PNR Check Reminders", s.pnrNudges) { on -> model.setSettings { it.copy(pnrNudges = on) } }
            RowDivider()
            Toggle("Booking Countdown", s.bookingCountdown) { on -> model.setSettings { it.copy(bookingCountdown = on) } }
        }
        help("PNR check reminders: for a waitlisted or RAC ticket, a reminder at 09:00 each day in the five days before travel and four hours before departure. Tap it to check. Booking countdown: a live timer on the lock screen for the last 15 minutes before a booking window opens.")()
        head("QUIET HOURS")()
        val q = s.quietHours ?: QuietHours()
        Grouped {
            Toggle("Quiet Hours", q.enabled) { on -> model.setSettings { it.copy(quietHours = q.copy(enabled = on)) } }
            if (q.enabled) {
                val all = (0..23).map { "%02d:00".format(it) }.map { it to it }
                RowDivider(); ValueRow("From", q.start) { ask(Choice("Quiet from", all, q.start) { v -> model.setSettings { it.copy(quietHours = q.copy(start = v)) } }) }
                RowDivider(); ValueRow("Until", q.end) { ask(Choice("Quiet until", all, q.end) { v -> model.setSettings { it.copy(quietHours = q.copy(end = v)) } }) }
            }
        }
    }
}

@Composable
private fun PlanningPage(model: AppModel, ask: (Choice) -> Unit) {
    val s = model.planner.settings
    val keep = s.ticketRetentionDays ?: 7
    var editWindow by remember { mutableStateOf(false) }
    if (editWindow) BookingWindowDialog(s.bookingWindowDays, { editWindow = false }) { model.setBookingWindow(it); editWindow = false }
    Grouped {
        ValueRow("Booking Window", "${s.bookingWindowDays} days before travel",
            "How many days before the journey IRCTC opens general booking (60 today). Changing it moves the booking day, reminders and widget for every trip you haven't booked yet. Tatkal always opens the day before.") {
            editWindow = true
        }
        RowDivider()
        ValueRow("Plan Routines Ahead", "${s.routineMonthsAhead} months",
            "Routines create their trips this far ahead, so you see every booking day and get its reminders. IRCTC opens booking ${s.bookingWindowDays} days before travel, so anything past that is just planning.") {
            ask(Choice("Plan Routines Ahead", listOf(1, 3, 6, 9, 12).map { "$it" to "$it month${if (it == 1) "" else "s"}" }, "${s.routineMonthsAhead}") { v -> model.setSettings { it.copy(routineMonthsAhead = v.toInt()) } })
        }
        RowDivider()
        ValueRow("Keep Ticket PDFs", "$keep days after the trip",
            "Ticket PDFs are deleted this long after the trip to save space. The journey, fare and seat stay.") {
            ask(Choice("Keep Ticket PDFs", listOf(7, 30, 90, 365).map { "$it" to "$it days after the trip" }, "$keep") { v -> model.setSettings { it.copy(ticketRetentionDays = v.toInt()) } })
        }
        RowDivider()
        ValueRow("Week Starts On", if (s.weekStartsOn == 1) "Monday" else "Sunday", "The first column of the calendar.") {
            ask(Choice("Week Starts On", listOf("0" to "Sunday", "1" to "Monday"), "${s.weekStartsOn}") { v -> model.setSettings { it.copy(weekStartsOn = v.toInt()) } })
        }
    }
}

@Composable
private fun AddTimeOff(model: AppModel, today: String, done: () -> Unit) {
    var date by remember { mutableStateOf(today) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("leave") }
    Column(Modifier.padding(16.dp)) {
        DateField("Day", date, { date = it }, min = today)
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.size(10.dp))
        Segmented(listOf("leave" to "Leave", "company" to "Holiday"), type) { type = it }
        Spacer(Modifier.size(10.dp))
        PrimaryButton("Add", { model.addTimeOff(date, name.ifBlank { if (type == "leave") "Leave" else "Holiday" }.trim(), type); done() }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Category(icon: ImageVector, title: String, sub: String, alert: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Rw.colors.brandSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Rw.colors.brandSoftInk, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = if (alert) Rw.colors.attentionInk else Rw.colors.muted)
        }
        Icon(RwIcons.ChevronRight, contentDescription = null, tint = Rw.colors.muted)
    }
}

@Composable
private fun ValueRow(title: String, value: String, help: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Rw.colors.brand)
        }
        help?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { set(!on) }.heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Rw.colors.text, modifier = Modifier.weight(1f))
        Switch(on, set, colors = SwitchDefaults.colors(checkedTrackColor = Rw.colors.brand, checkedThumbColor = Rw.colors.brandInk))
    }
}

@Composable
internal fun ListRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        }
        Icon(RwIcons.ChevronRight, contentDescription = null, tint = Rw.colors.muted)
    }
}

/** Type the booking window in days (1 to 365), starting from the current value. */
@Composable
private fun BookingWindowDialog(current: Int, dismiss: () -> Unit, save: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val days = text.toIntOrNull()?.takeIf { it in 1..365 }
    AlertDialog(onDismissRequest = dismiss, containerColor = Rw.colors.surface,
        title = { Text("Booking Window", color = Rw.colors.text) },
        text = {
            Column {
                Text("Days before travel that IRCTC opens general booking. It's 60 today.", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text("Days before travel") },
                    isError = days == null, supportingText = { if (days == null) Text("Enter a number from 1 to 365") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton({ days?.let(save) }, enabled = days != null) { Text("Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
