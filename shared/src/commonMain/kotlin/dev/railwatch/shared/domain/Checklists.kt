package dev.railwatch.shared.domain

import kotlin.time.Instant

const val TICKET_ITEM = "Ticket saved offline"

/** The list everyone starts with. */
val defaultChecklists = listOf(Checklist("every", "Every Trip", listOf(TICKET_ITEM, "ID proof", "Phone charger")))

fun checklistsOf(p: Planner): List<Checklist> = p.checklists ?: defaultChecklists

/** Arrives on a later day than it leaves (or arrives at an earlier clock time). */
fun isOvernight(j: Journey): Boolean =
    (j.arrivalDate != null && j.arrivalDate > j.date) || (j.arrival != null && j.departure.isNotEmpty() && j.arrival < j.departure)

private fun norm(s: String) = s.uppercase().replace(" ", "")

fun ruleMatches(rule: ChecklistRule, j: Journey): Boolean = when (rule.kind) {
    "class" -> norm(j.travelClass) in rule.values.map(::norm)
    "train" -> j.trainNumber?.trim()?.let { t -> rule.values.any { it.trim() == t } } == true
    "to" -> norm(j.to) in rule.values.map(::norm)
    "overnight" -> isOvernight(j)
    "tatkal" -> j.bookingType == "tatkal"
    else -> true
}

/** "Always", "SL, 2S", "Train 12345", "To BBB", "Overnight", "Tatkal". */
fun ruleLabel(rule: ChecklistRule): String = when (rule.kind) {
    "class" -> rule.values.joinToString(", ").ifEmpty { "Class" }
    "train" -> "Train ${rule.values.joinToString(", ")}"
    "to" -> "To ${rule.values.joinToString(", ")}"
    "overnight" -> "Overnight"
    "tatkal" -> "Tatkal"
    else -> "Always"
}

data class CheckItem(val text: String, val done: Boolean, val skipped: Boolean)
data class CheckGroup(val name: String, val items: List<CheckItem>)

/**
 * The trip's checklist, grouped by the lists that matched (in list order) and then "This Trip" for one-off items.
 * An item that appears in several lists shows once, under the first. "Ticket saved offline" ticks itself.
 */
fun tripChecklist(p: Planner, j: Journey): List<CheckGroup> {
    val seen = mutableSetOf<String>()
    val checked = j.checked.orEmpty().map(String::lowercase).toSet()
    val skipped = j.checkSkipped.orEmpty().map(String::lowercase).toSet()
    fun item(t: String) = CheckItem(t, t.lowercase() in checked || (t == TICKET_ITEM && hasTicket(j)), t.lowercase() in skipped)
    val groups = checklistsOf(p).filter { ruleMatches(it.rule, j) }.map { list ->
        CheckGroup(list.name, list.items.filter { it.isNotBlank() && seen.add(it.trim().lowercase()) }.map { item(it.trim()) })
    }.filter { it.items.isNotEmpty() }
    val extra = j.checkExtra.orEmpty().filter { seen.add(it.trim().lowercase()) }.map { item(it.trim()) }
    return groups + listOfNotNull(CheckGroup("This Trip", extra).takeIf { extra.isNotEmpty() })
}

/** Items still to do: not ticked and not marked as not needed. */
fun checklistLeft(p: Planner, j: Journey): List<String> = tripChecklist(p, j).flatMap { it.items }.filter { !it.done && !it.skipped }.map { it.text }

private fun toggle(list: List<String>?, item: String, on: Boolean): List<String> {
    val rest = list.orEmpty().filterNot { it.equals(item, ignoreCase = true) }
    return if (on) rest + item else rest
}

fun setChecked(j: Journey, item: String, on: Boolean) = j.copy(checked = toggle(j.checked, item, on), checkSkipped = if (on) toggle(j.checkSkipped, item, false) else j.checkSkipped)
fun setSkipped(j: Journey, item: String, on: Boolean) = j.copy(checkSkipped = toggle(j.checkSkipped, item, on), checked = if (on) toggle(j.checked, item, false) else j.checked)
fun addTripItem(j: Journey, item: String) = if (item.isBlank()) j else j.copy(checkExtra = toggle(j.checkExtra, item.trim(), true))
fun removeTripItem(j: Journey, item: String) = j.copy(checkExtra = toggle(j.checkExtra, item, false), checked = toggle(j.checked, item, false), checkSkipped = toggle(j.checkSkipped, item, false))

/** The checklist shows from two days before departure until the train leaves. */
fun showsChecklist(j: Journey, today: String): Boolean =
    j.status in setOf(JourneyStatus.Booked, JourneyStatus.NeedsBooking) && daysBetween(today, j.date) in 0..2

/** 20:00 the evening before a booked trip. */
fun checklistNudgeTime(j: Journey): Instant? =
    if (j.status == JourneyStatus.Booked) Instant.parse("${addDays(j.date, -1)}T20:00:00+05:30") else null
