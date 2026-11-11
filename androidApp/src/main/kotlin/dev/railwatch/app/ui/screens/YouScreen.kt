package dev.railwatch.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.LargeTitle
import dev.railwatch.app.ui.components.MonthHeader
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Rule

/** You: routines and planning settings (more arrives with later milestones). */
@Composable
fun YouScreen(model: AppModel, editRoutine: (Rule?) -> Unit, contentPadding: PaddingValues) {
    val p = model.planner
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
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
        item { MonthHeader("Planning") }
        item {
            Grouped {
                ListRow("Booking opens", "${p.settings.bookingWindowDays} days before travel, 08:00 IST") {}
                RowDivider()
                ListRow("Plan ahead", "${p.settings.routineMonthsAhead} months of trips from routines") {
                    model.setSettings { s -> s.copy(routineMonthsAhead = if (s.routineMonthsAhead >= 12) 3 else s.routineMonthsAhead + 3) }
                }
            }
        }
        item { Text("Stored only on this phone. No account, no server.", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, modifier = Modifier.padding(top = 16.dp, start = 4.dp)) }
    }
}

@Composable
private fun ListRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted)
        }
        Icon(RwIcons.ChevronRight, contentDescription = null, tint = Rw.colors.muted)
    }
}
