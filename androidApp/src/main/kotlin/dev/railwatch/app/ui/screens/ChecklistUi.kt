package dev.railwatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.Grouped
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.RowDivider
import dev.railwatch.app.ui.components.RwIcons
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.CheckItem
import dev.railwatch.shared.domain.Checklist
import dev.railwatch.shared.domain.ChecklistRule
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.addTripItem
import dev.railwatch.shared.domain.checklistsOf
import dev.railwatch.shared.domain.removeTripItem
import dev.railwatch.shared.domain.ruleLabel
import dev.railwatch.shared.domain.setChecked
import dev.railwatch.shared.domain.setSkipped
import dev.railwatch.shared.domain.showsChecklist
import dev.railwatch.shared.domain.tripChecklist

/**
 * Before You Go on the trip sheet, from two days before departure. "All" shows every item at once, grouped by the
 * list it came from; a list's chip shows just that list. Each item can be ticked, or marked Not Needed for this trip.
 */
@Composable
fun TripChecklist(j: Journey, model: AppModel) {
    if (!showsChecklist(j, model.today)) return
    val groups = tripChecklist(model.planner, j)
    var filter by rememberSaveable(j.id) { mutableStateOf<String?>(null) }
    var adding by remember(j.id) { mutableStateOf("") }
    val all = groups.flatMap { it.items }
    val handled = all.count { it.done || it.skipped }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Rw.colors.surface2).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
            Text("Before You Go", color = Rw.colors.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (all.isNotEmpty()) Text("$handled of ${all.size}", color = if (handled == all.size) Rw.colors.bookedInk else Rw.colors.muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (groups.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", filter == null) { filter = null }
            groups.forEach { g -> Chip(g.name, filter == g.name) { filter = g.name } }
        }
        groups.filter { filter == null || it.name == filter }.forEach { g ->
            if (filter == null && groups.size > 1) Text(g.name.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            g.items.forEach { item -> ItemRow(item, oneOff = g.name == "This Trip", model = model, j = j) }
        }
        if (filter == null || filter == "This Trip") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(adding, { adding = it.take(60) }, placeholder = { Text("Add an item for this trip") }, singleLine = true, colors = fieldColors(),
                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (adding.isNotBlank()) { model.saveJourney(addTripItem(j, adding)); adding = "" } }))
            Text("Add", color = if (adding.isBlank()) Rw.colors.muted else Rw.colors.brand, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = adding.isNotBlank()) { model.saveJourney(addTripItem(j, adding)); adding = "" }.padding(horizontal = 12.dp, vertical = 14.dp))
        }
        Text("Lists live in More › Settings › Checklists.", color = Rw.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ItemRow(item: CheckItem, oneOff: Boolean, model: AppModel, j: Journey) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = !item.skipped, role = Role.Checkbox) { model.saveJourney(setChecked(j, item.text, !item.done)) }
            .semantics(mergeDescendants = true) { stateDescription = if (item.done) "Done" else if (item.skipped) "Not needed" else "Not done" },
            verticalAlignment = Alignment.CenterVertically) {
            val box = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
            Box(if (item.done) box.background(Rw.colors.bookedInk) else box.border(2.dp, if (item.skipped) Rw.colors.line else Rw.colors.muted, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                if (item.done) Icon(RwIcons.Check, null, tint = Rw.colors.ground, modifier = Modifier.size(16.dp))
            }
            Text(item.text, color = if (item.done || item.skipped) Rw.colors.muted else Rw.colors.text,
                textDecoration = if (item.done || item.skipped) TextDecoration.LineThrough else null, modifier = Modifier.padding(start = 12.dp))
            if (item.skipped) Text("Not needed", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(99.dp)).background(Rw.colors.surface).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        val (label, action) = when {
            item.skipped -> "Undo" to { model.saveJourney(setSkipped(j, item.text, false)) }
            oneOff -> "Remove" to { model.saveJourney(removeTripItem(j, item.text)) }
            item.done -> null to {}
            else -> "Not Needed" to { model.saveJourney(setSkipped(j, item.text, true)) }
        }
        if (label != null) Text(label, color = Rw.colors.brand, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = action).semantics { contentDescription = "$label: ${item.text}" }.padding(horizontal = 8.dp, vertical = 14.dp))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) =
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Rw.colors.brandInk else Rw.colors.text,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Rw.colors.brand else Rw.colors.surface).clickable(role = Role.Tab, onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp))

private val KINDS = listOf("always" to "Always", "class" to "Class", "train" to "Train", "to" to "Destination", "overnight" to "Overnight", "tatkal" to "Tatkal")

/** Settings › Checklists: every list with its rule; tap one to edit it, or add a new one. */
@Composable
fun ChecklistsPage(model: AppModel) {
    val lists = checklistsOf(model.planner)
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    editing?.let { id ->
        val list = lists.firstOrNull { it.id == id }
        if (list != null) { ChecklistEditor(list, lists, model) { editing = null }; return }
        editing = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Grouped {
            lists.forEachIndexed { i, l ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { editing = l.id }.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
                        Text(l.items.take(3).joinToString(", ").ifEmpty { "No items yet" } + " · ${l.items.size}", style = MaterialTheme.typography.bodyMedium, color = Rw.colors.muted, maxLines = 1)
                    }
                    Text(ruleLabel(l.rule), style = MaterialTheme.typography.labelMedium, color = Rw.colors.muted,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Rw.colors.surface2).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        PrimaryButton("New List", {
            val id = "list-" + kotlin.random.Random.nextLong(1_000_000, 9_999_999)
            model.setChecklists(lists + Checklist(id, "New List")); editing = id
        }, Modifier.fillMaxWidth())
        Text("A trip shows the items of every list that matches it, once each. Use \"Not Needed\" on a trip to skip an item just for that trip.",
            color = Rw.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun ChecklistEditor(list: Checklist, lists: List<Checklist>, model: AppModel, done: () -> Unit) {
    var name by remember(list.id) { mutableStateOf(list.name) }
    var kind by remember(list.id) { mutableStateOf(list.rule.kind) }
    var values by remember(list.id) { mutableStateOf(list.rule.values.joinToString(", ")) }
    var items by remember(list.id) { mutableStateOf(list.items) }
    var adding by remember(list.id) { mutableStateOf("") }
    fun save() {
        val v = values.split(",").map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val updated = list.copy(name = name.trim().ifEmpty { "Checklist" }, items = items, rule = ChecklistRule(kind, if (kind in setOf("class", "train", "to")) v else emptyList()))
        model.setChecklists(lists.map { if (it.id == list.id) updated else it }); done()
    }
    androidx.activity.compose.BackHandler { save() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("List Name") }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Text("USE WHEN", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { KINDS.forEach { (k, l) -> Chip(l, kind == k) { kind = k } } }
        if (kind in setOf("class", "train", "to")) OutlinedTextField(values, { values = it.take(80) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
            label = { Text(when (kind) { "class" -> "Classes, e.g. SL, 2S"; "train" -> "Train numbers, e.g. 12345"; else -> "Station codes, e.g. BBB" }) })
        Text("ITEMS", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Grouped {
            items.forEachIndexed { i, it ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = Rw.colors.text, modifier = Modifier.weight(1f))
                    Icon(RwIcons.Close, "Remove $it", tint = Rw.colors.muted, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable { items = items - it }.padding(14.dp))
                }
            }
            if (items.isEmpty()) Text("No items yet.", color = Rw.colors.muted, modifier = Modifier.padding(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(adding, { adding = it.take(60) }, placeholder = { Text("Add an item") }, singleLine = true, colors = fieldColors(), modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (adding.isNotBlank()) { items = items + adding.trim(); adding = "" } }))
            Text("Add", color = if (adding.isBlank()) Rw.colors.muted else Rw.colors.brand, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = adding.isNotBlank()) { items = items + adding.trim(); adding = "" }.padding(horizontal = 12.dp, vertical = 14.dp))
        }
        PrimaryButton("Save", ::save, Modifier.fillMaxWidth())
        Text("Delete List", color = Rw.colors.danger, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { model.setChecklists(lists.filter { it.id != list.id }); done() }.padding(vertical = 14.dp))
        Spacer(Modifier.height(8.dp))
    }
}
