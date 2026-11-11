package dev.railwatch.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.Segmented
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.TimeOffRange
import dev.railwatch.shared.domain.daysBetween

/** Add or edit a holiday or leave: one day or a range, a name, and its kind. */
@Composable
fun TimeOffSheet(model: AppModel, existing: TimeOffRange?, close: (String) -> Unit) {
    val today = model.today
    var start by remember { mutableStateOf(existing?.start ?: today) }
    var end by remember { mutableStateOf(existing?.end ?: today) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "leave") }
    val days = daysBetween(start, end) + 1
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (existing == null) "Add time off" else "Edit time off", style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text)
        Segmented(listOf("leave" to "Leave", "company" to "Holiday"), type) { type = it }
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, placeholder = { Text(if (type == "leave") "Leave" else "e.g. Pongal") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        DateField("From", start, { start = it; if (end < it) end = it })
        DateField("To", end, { end = it }, min = start)
        Text(if (days == 1) "1 day" else "$days days. Trips on these days are flagged, and long weekends are worked out from them.", color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(if (existing == null) "Add" else "Save", {
            model.setTimeOff(existing?.ids.orEmpty(), start, end, name.trim().ifEmpty { if (type == "leave") "Leave" else "Holiday" }, type)
            close(if (existing == null) "Time off added." else "Time off updated.")
        }, Modifier.fillMaxWidth(), enabled = days in 1..366)
        if (existing != null) Text("Remove", color = Rw.colors.danger, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable { existing.ids.forEach(model::removeTimeOff); close("Time off removed.") }.padding(vertical = 8.dp))
    }
}
