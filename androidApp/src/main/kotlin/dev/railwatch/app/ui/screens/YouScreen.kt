package dev.railwatch.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import dev.railwatch.shared.domain.hasTicket
import dev.railwatch.shared.domain.spendByMonth
import dev.railwatch.shared.domain.timeOffConflicts

private enum class Page(val title: String, val icon: ImageVector) {
    Routines("Routines", RwIcons.List), TimeOff("Holidays & leave", RwIcons.Calendar), Tickets("Tickets", RwIcons.Ticket),
    Spend("Spend", RwIcons.Ticket), Reminders("Reminders", RwIcons.Bell), Planning("Planning", RwIcons.Clock),
}

/** A single-choice popup, as in phone settings. */
private data class Choice(val title: String, val options: List<Pair<String, String>>, val selected: String, val pick: (String) -> Unit)

/** You: a list of setting categories; each opens its own page, and choices open as popups. */
@Composable
fun YouScreen(model: AppModel, editRoutine: (Rule?) -> Unit, open: (Journey) -> Unit, showTicket: (Journey) -> Unit, info: (String) -> Unit, contentPadding: PaddingValues) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var choice by remember { mutableStateOf<Choice?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val p = model.planner; val today = model.today

    val header: @Composable () -> Unit = {
        val pg = page
        if (pg == null) LargeTitle("More")
        else Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RwIcons.ChevronLeft, "Back", tint = Rw.colors.brand, modifier = Modifier.size(44.dp).clickable { page = null }.padding(8.dp))
            Text(pg.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text)
        }
    }
    if (page == Page.Tickets) { TicketsScreen(model, showTicket, contentPadding, header); return }

    LazyColumn(contentPadding = contentPadding) {
        item { header() }
        when (page) {
            null -> item {
                val conflicts = timeOffConflicts(p, today).size
                val subs = mapOf(
                    Page.Routines to (p.rules.size.let { if (it == 0) "None yet" else "$it routine${if (it == 1) "" else "s"}" }),
                    Page.Tickets to p.journeys.count { hasTicket(it) }.let { if (it == 0) "Share an IRCTC PDF to add one" else "$it saved" },
                    Page.TimeOff to (if (conflicts > 0) "$conflicts trip${if (conflicts == 1) "" else "s"} on time off" else dev.railwatch.shared.domain.longWeekends(p, today).firstOrNull()?.let { "Next long weekend ${shortDay(it.start)}" } ?: "${p.holidays.count { it.date >= today }} days off ahead"),
                    Page.Spend to (spendByMonth(p).firstOrNull()?.let { "${monthLabel(it.month, today)} · ${rupees(it.amount)}" } ?: "No fares yet"),
                    Page.Reminders to "Booking and cancellation alerts",
                    Page.Planning to "${p.settings.routineMonthsAhead} months ahead · week starts ${if (p.settings.weekStartsOn == 1) "Monday" else "Sunday"}",
                )
                Grouped {
                    Page.entries.forEachIndexed { i, pg ->
                        if (i > 0) RowDivider()
                        Category(pg.icon, pg.title, subs[pg].orEmpty(), alert = pg == Page.TimeOff && conflicts > 0) { page = pg }
                    }
                }
            }
            Page.Routines -> routines(model, editRoutine)
            Page.TimeOff -> item { TimeOffPage(model, open, info) }
            Page.Spend -> spend(model)
            Page.Reminders -> item { RemindersPage(model) { choice = it } }
            Page.Planning -> item { PlanningPage(model) { choice = it } }
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

private fun LazyListScope.routines(model: AppModel, editRoutine: (Rule?) -> Unit) = item {
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Grouped {
        model.planner.rules.forEachIndexed { i, r ->
            if (i > 0) RowDivider()
            ListRow(r.name, "${r.from} → ${r.to} · ${r.weekdays.joinToString("/") { days[it] }}${if (r.paused) " · paused" else ""}") { editRoutine(r) }
        }
        if (model.planner.rules.isNotEmpty()) RowDivider()
        ListRow("New routine", "Plan a regular trip once") { editRoutine(null) }
    }
}

private fun LazyListScope.spend(model: AppModel) = item {
    val today = model.today
    val spend = spendByMonth(model.planner)
    Grouped {
        if (spend.isEmpty()) Text("Fares show here once tickets are added.", color = Rw.colors.muted, modifier = Modifier.padding(16.dp))
        spend.take(12).forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(monthLabel(m.month, today), color = Rw.colors.text)
                    Text("${m.trips} trip${if (m.trips == 1) "" else "s"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text(rupees(m.amount), color = Rw.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun TimeOffPage(model: AppModel, open: (Journey) -> Unit, info: (String) -> Unit) {
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
            if (adding) AddTimeOff(model, today) { adding = false } else ListRow("Add a day off", "Leave or a holiday") { adding = true }
            RowDivider()
            ListRow("Import a calendar", "An .ics file, or a CSV with date,name,type") { pick.launch(arrayOf("text/calendar", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
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
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Rw.colors.text)
                            val n = r.ids.size
                            Text("${span(r.start, r.end)}${if (n > 1) " · $n days" else ""} · ${if (r.type == "leave") "Leave" else "Holiday"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(RwIcons.Close, "Remove ${r.name}", tint = Rw.colors.muted, modifier = Modifier.size(44.dp).clickable { r.ids.forEach(model::removeTimeOff) }.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersPage(model: AppModel, ask: (Choice) -> Unit) {
    val s = model.planner.settings; val clock = s.reminderClock
    val times = (5..23).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }.map { it to it }
    Column {
        Grouped {
            listOf("previous_evening" to "Evening before", "morning" to "Morning of", "opening" to "Just before opening").forEachIndexed { i, (key, label) ->
                if (i > 0) RowDivider()
                Toggle(label, key in s.reminderTimes) { on -> model.setSettings { it.copy(reminderTimes = if (on) (it.reminderTimes + key).distinct() else it.reminderTimes - key) } }
            }
        }
        Spacer(Modifier.size(16.dp))
        Grouped {
            ValueRow("Evening reminder", clock.previous_evening) { ask(Choice("Evening reminder", times, clock.previous_evening) { v -> model.setSettings { it.copy(reminderClock = it.reminderClock.copy(previous_evening = v)) } }) }
            RowDivider()
            ValueRow("Morning reminder", clock.morning) { ask(Choice("Morning reminder", times, clock.morning) { v -> model.setSettings { it.copy(reminderClock = it.reminderClock.copy(morning = v)) } }) }
        }
        Spacer(Modifier.size(16.dp))
        Grouped {
            Toggle("Cancellation follow-up", s.cancellationEnabled) { on -> model.setSettings { it.copy(cancellationEnabled = on) } }
            if (s.cancellationEnabled) { RowDivider(); ValueRow("Follow-up time", s.cancellationTime) { ask(Choice("Follow-up time", times, s.cancellationTime) { v -> model.setSettings { it.copy(cancellationTime = v) } }) } }
        }
        Spacer(Modifier.size(16.dp))
        val q = s.quietHours ?: QuietHours()
        Grouped {
            Toggle("Quiet hours", q.enabled) { on -> model.setSettings { it.copy(quietHours = q.copy(enabled = on)) } }
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
    Grouped {
        ValueRow("Plan routines ahead", "${s.routineMonthsAhead} months") {
            ask(Choice("Plan routines ahead", listOf(1, 3, 6, 9, 12).map { "$it" to "$it month${if (it == 1) "" else "s"}" }, "${s.routineMonthsAhead}") { v -> model.setSettings { it.copy(routineMonthsAhead = v.toInt()) } })
        }
        RowDivider()
        ValueRow("Keep ticket PDFs", "$keep days after the trip") {
            ask(Choice("Keep ticket PDFs", listOf(7, 30, 90, 365).map { "$it" to "$it days after the trip" }, "$keep") { v -> model.setSettings { it.copy(ticketRetentionDays = v.toInt()) } })
        }
        RowDivider()
        ValueRow("Week starts on", if (s.weekStartsOn == 1) "Monday" else "Sunday") {
            ask(Choice("Week starts on", listOf("0" to "Sunday", "1" to "Monday"), "${s.weekStartsOn}") { v -> model.setSettings { it.copy(weekStartsOn = v.toInt()) } })
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
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
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
private fun ListRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        }
        Icon(RwIcons.ChevronRight, contentDescription = null, tint = Rw.colors.muted)
    }
}
