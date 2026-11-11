package dev.railwatch.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.MonthHeader
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
import dev.railwatch.shared.domain.spendByMonth
import dev.railwatch.shared.domain.timeOffConflicts

/** You: routines, time off, spend, reminders and planning settings. */
@Composable
fun YouScreen(model: AppModel, editRoutine: (Rule?) -> Unit, open: (Journey) -> Unit, info: (String) -> Unit, contentPadding: PaddingValues) {
    val p = model.planner; val s = p.settings; val today = model.today
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val context = LocalContext.current
    var importType by remember { mutableStateOf("company") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(2_000_000).toByteArray().decodeToString() } }.getOrNull()
        val name = uri.lastPathSegment.orEmpty()
        info(if (text == null) "Couldn't open that file." else model.importTimeOff(text, name, importType))
    }
    var adding by remember { mutableStateOf(false) }
    val conflicts = timeOffConflicts(p, today)
    val upcomingOff = p.holidays.filter { it.date >= today }
    val spend = spendByMonth(p)

    LazyColumn(contentPadding = contentPadding) {
        item { LargeTitle("You") }
        item { MonthHeader("Routines") }
        item {
            Grouped {
                p.rules.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    ListRow(r.name, "${r.from} → ${r.to} · ${r.weekdays.joinToString("/") { days[it] }}${if (r.paused) " · paused" else ""}") { editRoutine(r) }
                }
                if (p.rules.isNotEmpty()) RowDivider()
                ListRow("New routine", "Plan a regular trip once") { editRoutine(null) }
            }
        }

        item { MonthHeader("Time off") }
        if (conflicts.isNotEmpty()) item {
            Grouped {
                Text("${conflicts.size} trip${if (conflicts.size == 1) "" else "s"} on your time off", fontWeight = FontWeight.SemiBold, color = Rw.colors.attentionInk, modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp))
                conflicts.take(5).forEach { j ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${shortDay(j.date)} · ${j.from} → ${j.to}", color = Rw.colors.text, modifier = Modifier.weight(1f).clickable { open(j) })
                        Text("Keep", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { model.keepOnTimeOff(j) }.padding(8.dp))
                    }
                }
                Spacer(Modifier.size(6.dp))
            }
            Spacer(Modifier.size(10.dp))
        }
        item {
            Grouped {
                upcomingOff.take(8).forEachIndexed { i, h ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.name, color = Rw.colors.text)
                            Text("${shortDay(h.date)} · ${if (h.type == "leave") "Leave" else "Holiday"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(RwIcons.Close, "Remove ${h.name}", tint = Rw.colors.muted, modifier = Modifier.size(36.dp).clickable { model.removeTimeOff(h.id) }.padding(8.dp))
                    }
                }
                if (upcomingOff.size > 8) Text("and ${upcomingOff.size - 8} more", color = Rw.colors.muted, modifier = Modifier.padding(16.dp))
                if (upcomingOff.isNotEmpty()) RowDivider()
                if (adding) AddTimeOff(model, today) { adding = false } else ListRow("Add a day off", "Holidays and leave flag trips you may not take") { adding = true }
                RowDivider()
                ListRow("Import a calendar", "An .ics file, or a CSV with date,name,type") { importType = "company"; pick.launch(arrayOf("text/calendar", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) }
            }
        }

        item { MonthHeader("Spend") }
        item {
            Grouped {
                if (spend.isEmpty()) Text("Fares show here once tickets are added.", color = Rw.colors.muted, modifier = Modifier.padding(16.dp))
                spend.take(6).forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(monthLabel(m.month, today), color = Rw.colors.text)
                            Text("${m.trips} trip${if (m.trips == 1) "" else "s"}", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("₹" + "%,.2f".format(java.util.Locale.forLanguageTag("en-IN"), m.amount), color = Rw.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    }
                }
            }
        }

        item { MonthHeader("Reminders") }
        item {
            Grouped {
                val clock = s.reminderClock
                listOf("previous_evening" to "Evening before · ${clock.previous_evening}", "morning" to "Morning of · ${clock.morning}", "opening" to "Just before opening · ${clock.opening}").forEachIndexed { i, (key, label) ->
                    if (i > 0) RowDivider()
                    Toggle(label, key in s.reminderTimes) { on -> model.setSettings { it.copy(reminderTimes = if (on) (it.reminderTimes + key).distinct() else it.reminderTimes - key) } }
                }
                RowDivider()
                Toggle("Cancellation follow-up · ${s.cancellationTime}", s.cancellationEnabled) { on -> model.setSettings { it.copy(cancellationEnabled = on) } }
                RowDivider()
                val q = s.quietHours ?: QuietHours()
                Toggle("Quiet hours · ${q.start}–${q.end}", q.enabled) { on -> model.setSettings { it.copy(quietHours = q.copy(enabled = on)) } }
            }
        }

        item { MonthHeader("Planning") }
        item {
            Grouped {
                ListRow("Plan ahead", "${s.routineMonthsAhead} months of trips from routines") {
                    model.setSettings { it.copy(routineMonthsAhead = if (it.routineMonthsAhead >= 12) 3 else it.routineMonthsAhead + 3) }
                }
                RowDivider()
                val keep = s.ticketRetentionDays ?: 7
                ListRow("Keep ticket PDFs", "$keep days after the trip") {
                    model.setSettings { it.copy(ticketRetentionDays = when (keep) { 7 -> 30; 30 -> 90; else -> 7 }) }
                }
                RowDivider()
                ListRow("Week starts on", if (s.weekStartsOn == 1) "Monday" else "Sunday") { model.setSettings { it.copy(weekStartsOn = 1 - it.weekStartsOn.coerceIn(0, 1)) } }
            }
        }
        item { Text("Stored only on this phone. No account, no server.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(top = 16.dp, start = 4.dp)) }
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
