package dev.railwatch.shared.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RailWatch's data, field for field the same JSON as the web app's planner (src/lib/travel-planner.ts), so a backup
 * from either opens in the other. Dates are "YYYY-MM-DD" strings and times "HH:MM" (IST), exactly as stored there.
 */
@Serializable
enum class JourneyStatus {
    @SerialName("needs_booking") NeedsBooking,
    @SerialName("booked") Booked,
    @SerialName("skipped") Skipped,
    @SerialName("cancellation_needed") CancellationNeeded,
    @SerialName("cancelled") Cancelled,
    @SerialName("completed") Completed,
}

@Serializable data class Attachment(val id: String, val name: String, val type: String, val size: Long, val createdAt: String)

@Serializable data class Fare(
    val total: Double, val ticket: Double? = null, val convenience: Double? = null, val wallet: Double? = null,
    val insurance: Double? = null, val refund: Double? = null,
)

@Serializable data class StatusEntry(val at: String, val status: String)

@Serializable data class Journey(
    val id: String,
    val from: String,
    val to: String,
    val date: String,
    val departure: String,
    val train: String = "",
    val travelClass: String = "",
    val windowDays: Int = 60,
    val originOffset: Int = 0,
    val status: JourneyStatus = JourneyStatus.NeedsBooking,
    val pnr: String = "",
    val notes: String = "",
    val ruleId: String? = null,
    val leg: String? = null,
    val manualOverride: Boolean? = null,
    val timePreference: String? = null,
    val departureConfirmed: Boolean? = null,
    val bookingDateOverride: String? = null,
    val bookingType: String? = null,
    val tatkalClass: String? = null,
    val trainOriginDate: String? = null,
    val trainNumber: String? = null,
    val trainName: String? = null,
    val coach: String? = null,
    val seat: String? = null,
    val berth: String? = null,
    val arrival: String? = null,
    val arrivalDate: String? = null,
    val timeOffKept: Boolean? = null,
    val ticketStatus: String? = null,
    val currentStatus: String? = null,
    val statusLog: List<StatusEntry>? = null,
    val fare: Fare? = null,
    val readTickets: List<String>? = null,
    val completedAt: String? = null,
    val cancelledAt: String? = null,
    val archivedAt: String? = null,
    val attachments: List<Attachment>? = null,
    val reminderOverride: ReminderOverride? = null,
    val pnrStatus: PnrSnapshot? = null,
    val pnrLastAttemptAt: String? = null,
    val pnrLastError: String? = null,
)

@Serializable data class PnrPassenger(val number: Int = 1, val booking: String = "", val current: String = "", val coach: String = "", val berth: String = "")
@Serializable data class PnrSnapshot(val pnr: String, val checkedAt: String = "", val chartStatus: String = "", val passengers: List<PnrPassenger> = emptyList())

@Serializable data class Recurrence(
    val frequency: String, val interval: Int, val monthlyPattern: String = "date", val dayOfMonth: Int = 1,
    val ordinal: Int = 1, val weekday: Int = 1,
)

@Serializable data class Rule(
    val id: String,
    val name: String,
    val from: String,
    val to: String,
    val train: String = "",
    val travelClass: String = "",
    val windowDays: Int = 60,
    val originOffset: Int = 0,
    val start: String,
    val end: String? = null,
    val linkedRuleId: String? = null,
    val paused: Boolean = false,
    val excludedDates: List<String> = emptyList(),
    val timePreference: String? = null,
    val recurrence: Recurrence? = null,
    val weekdays: List<Int>,
    val intervalWeeks: Int = 1,
    val departure: String,
    val returnAfterDays: Int? = null,
    val returnDeparture: String = "",
    val returnTrain: String = "",
    val returnOriginOffset: Int = 0,
    val reminderOverride: ReminderOverride? = null,
)

/**
 * A journey's (or routine's) own reminders, as on the web: "inherit" uses the settings, "off" sends none, "custom"
 * uses its own times (evening / morning / opening at the clock given) or its own schedule rows.
 */
@Serializable data class ReminderOverride(val mode: String = "inherit", val times: List<String> = emptyList(), val clock: ReminderClock = ReminderClock(), val schedule: List<ScheduleRow>? = null)

@Serializable data class Holiday(val id: String, val name: String, val date: String, val type: String)

@Serializable data class ReminderClock(val previous_evening: String = "20:00", val morning: String = "07:00", val opening: String = "07:55")
@Serializable data class ScheduleRow(val daysBefore: Int, val time: String)
@Serializable data class QuietHours(val enabled: Boolean = false, val start: String = "22:00", val end: String = "07:00")

@Serializable data class Settings(
    /** Booking reminders: the evening before, the morning of, and just before opening (web defaults). */
    val reminderTimes: List<String> = listOf("previous_evening", "morning", "opening"),
    val reminderClock: ReminderClock = ReminderClock(),
    val bookingSchedule: List<ScheduleRow>? = null,
    val tatkalSchedule: List<ScheduleRow>? = null,
    val tatkalNonAcSchedule: List<ScheduleRow>? = null,
    val cancellationEnabled: Boolean = true,
    val cancellationTime: String = "09:00",
    val quietHours: QuietHours? = null,
    val bookingWindowDays: Int = 60,
    val routineHorizonMode: String = "months",
    val routineMonthsAhead: Int = 6,
    val routineTicketCount: Int = 26,
    val cancelledArchiveDays: Int? = null,
    val ticketRetentionDays: Int? = null,
    val weekStartsOn: Int = 0,
    val weekendDays: List<Int> = listOf(0, 6),
    val theme: String = "dark",
)

@Serializable data class Planner(
    val version: Int = 1,
    val journeys: List<Journey> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val settings: Settings = Settings(),
)
