package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.components.Segmented
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.Planner
import dev.railwatch.shared.domain.ReminderOverride
import dev.railwatch.shared.domain.ScheduleRow
import dev.railwatch.shared.domain.reminderTimes
import kotlinx.datetime.toLocalDateTime

/** Every half hour, 05:00–23:30, for reminder times. */
val HALF_HOURS = (5..23).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }

fun daysBeforeLabel(n: Int) = when (n) { 0 -> "On booking day"; 1 -> "The day before"; else -> "$n days before" }

/** Picks one value from a scrolling list in a popup. */
@Composable
fun PickDialog(title: String, options: List<Pair<String, String>>, selected: String, pick: (String) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, containerColor = Rw.colors.surface, title = { Text(title, color = Rw.colors.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(options) { (value, label) ->
                    Text(label, color = if (value == selected) Rw.colors.brand else Rw.colors.text, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pick(value); dismiss() }.padding(horizontal = 8.dp, vertical = 12.dp))
                }
            }
        }, confirmButton = { TextButton(dismiss) { Text("Cancel") } })
}

/** Reminder rows: "The day before at 20:00", each editable or removable; add more (up to 6). */
@Composable
fun ScheduleEditor(rows: List<ScheduleRow>, change: (List<ScheduleRow>) -> Unit) {
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) } // row index and "days" or "time"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Rw.colors.surface2).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(daysBeforeLabel(r.daysBefore), color = Rw.colors.text, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { editing = i to "days" }.padding(10.dp))
                Text("at", color = Rw.colors.muted)
                Text(r.time, color = Rw.colors.brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { editing = i to "time" }.padding(10.dp))
                Icon(RwIcons.Close, "Remove this reminder", tint = Rw.colors.muted, modifier = Modifier.size(44.dp).clickable { change(rows.filterIndexed { k, _ -> k != i }) }.padding(12.dp))
            }
        }
        if (rows.size < 6) Text("+ Add a Reminder", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { change(rows + ScheduleRow(0, "07:00")) }.padding(vertical = 10.dp, horizontal = 4.dp))
    }
    editing?.let { (i, what) ->
        val r = rows.getOrNull(i) ?: return@let
        if (what == "days") PickDialog("When", (0..7).map { "$it" to daysBeforeLabel(it) }, "${r.daysBefore}", { v -> change(rows.mapIndexed { k, x -> if (k == i) x.copy(daysBefore = v.toInt()) else x }) }) { editing = null }
        else PickDialog("Time", HALF_HOURS.map { it to it }, r.time, { v -> change(rows.mapIndexed { k, x -> if (k == i) x.copy(time = v) else x }) }) { editing = null }
    }
}

/** A journey's reminders: as in settings, none, or its own schedule; shows when they will arrive. */
@Composable
fun JourneyReminders(p: Planner, j: Journey, value: ReminderOverride?, today: String, change: (ReminderOverride?) -> Unit) {
    val mode = value?.mode ?: "inherit"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Booking Reminders", style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
        Segmented(listOf("inherit" to "As in Settings", "custom" to "Custom", "off" to "Off"), mode) { m ->
            change(when (m) {
                "inherit" -> null
                "off" -> ReminderOverride("off")
                else -> ReminderOverride("custom", schedule = listOf(ScheduleRow(1, "20:00"), ScheduleRow(0, "07:00")))
            })
        }
        if (mode == "custom") ScheduleEditor(value?.schedule.orEmpty()) { change(ReminderOverride("custom", schedule = it)) }
        // What will actually happen for this trip.
        val at = reminderTimes(p, j.copy(reminderOverride = value), today)
        Text(if (mode == "off") "No booking reminders for this trip." else if (at.isEmpty()) "No reminders: booking has already opened or none are set."
            else "You'll be reminded " + at.joinToString(", ") { val t = it.toLocalDateTime(dev.railwatch.shared.IST); "${dev.railwatch.app.ui.shortDay(t.date.toString())} ${"%02d:%02d".format(t.hour, t.minute)}" } + ".",
            color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
    }
}
