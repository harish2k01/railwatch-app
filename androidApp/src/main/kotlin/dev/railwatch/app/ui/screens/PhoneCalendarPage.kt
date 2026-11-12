package dev.railwatch.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.PhoneCalendar
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.theme.Rw

/** Copy trips into a calendar on the phone: off, or one calendar, optionally with trips not booked yet. */
@Composable
fun PhoneCalendarPage(info: (String) -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) } // bumps to re-read after a change
    val allowed = remember(version) { PhoneCalendar.allowed(context) }
    val cals = remember(version) { PhoneCalendar.calendars(context) }
    val chosen = remember(version) { PhoneCalendar.calendarId(context) }
    val planned = remember(version) { PhoneCalendar.includePlanned(context) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) version++ else info("Calendar access wasn't allowed. You can allow it in the phone's settings for RailWatch.")
    }

    Column {
        Text("Your trips appear in the phone's calendar, with the PNR and seat in the notes. They follow every change, and are removed when a trip is cancelled or skipped.",
            style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        if (!allowed) {
            Grouped { ListRow("Allow Calendar Access", "Needed to add events to your calendar") { ask.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) } }
            return
        }
        Text("ADD TRIPS TO", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Grouped {
            Choice("Don't Add", null, chosen == null) { PhoneCalendar.choose(context, null); version++; info("Trips are no longer added to your calendar.") }
            cals.forEach { c ->
                RowDivider()
                Choice(c.name, c.account.takeIf { it.isNotBlank() && it != c.name }, chosen == c.id) { PhoneCalendar.choose(context, c.id); version++; info("Trips will appear in ${c.name}.") }
            }
        }
        if (cals.isEmpty()) Text("No calendar on this phone accepts new events. Add an account in the Calendar app first.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(4.dp, 8.dp))
        if (chosen != null) {
            Spacer(Modifier.height(16.dp))
            Grouped {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { PhoneCalendar.choose(context, chosen, !planned); version++ }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include Trips to Book", color = Rw.colors.text, style = MaterialTheme.typography.bodyLarge)
                        Text("Also add planned trips, marked \"To book\"", color = Rw.colors.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(planned, null, colors = SwitchDefaults.colors(checkedTrackColor = Rw.colors.brand))
                }
            }
        }
    }
}

@Composable
private fun Choice(title: String, sub: String?, selected: Boolean, pick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton, onClick = pick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = Rw.colors.brand), modifier = Modifier.padding(8.dp))
        Column {
            Text(title, color = Rw.colors.text, style = MaterialTheme.typography.bodyLarge)
            sub?.let { Text(it, color = Rw.colors.muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
