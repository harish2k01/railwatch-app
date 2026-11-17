package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.clashes
import dev.railwatch.shared.domain.missingReturns

/** Overlaps with another trip, and a booked trip with no way back: shown on the trip, with a way to say it's one-way. */
@Composable
fun TripWarnings(j: Journey, model: AppModel) {
    val p = model.planner; val today = model.today
    val clash = remember(p, j.id) {
        clashes(p, today).firstOrNull { it.first.id == j.id || it.second.id == j.id }?.let { if (it.first.id == j.id) it.second else it.first }
    }
    val noReturn = remember(p, j.id) { missingReturns(p, today).any { it.id == j.id } }
    if (clash != null) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.cancelBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Overlaps Another Trip", color = Rw.colors.cancelInk, fontWeight = FontWeight.Bold)
        Text("${clash.from} → ${clash.to} on ${shortDay(clash.date)} at ${clash.departure} is at the same time. Skip or cancel one of them.",
            color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
    }
    if (noReturn) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Rw.colors.attentionBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("No Return Planned", color = Rw.colors.attentionInk, fontWeight = FontWeight.Bold)
        Text("There's no trip from ${j.to} back to ${j.from} in the 30 days after. Add one with +, or mark this trip one-way.",
            color = Rw.colors.text, style = MaterialTheme.typography.bodyMedium)
        Text("It's One-Way", color = Rw.colors.brand, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { model.saveJourney(j.copy(oneWay = true)) }.padding(vertical = 14.dp))
    }
}
