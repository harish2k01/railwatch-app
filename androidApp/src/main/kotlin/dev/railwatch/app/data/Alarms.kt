package dev.railwatch.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.railwatch.app.MainActivity
import dev.railwatch.app.R
import dev.railwatch.shared.data.PlannerCodec
import dev.railwatch.shared.domain.Reminder
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.bookingDay
import dev.railwatch.shared.domain.pnrSummary
import dev.railwatch.shared.domain.upcomingReminders
import dev.railwatch.shared.todayIst
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Booking and cancellation reminders as phone notifications. Every change to the plans reschedules the next
 * reminders as exact alarms (inexact if the person hasn't allowed exact alarms); a restart reschedules them too.
 */
object Alarms {
    const val BOOKING = "booking"; const val CANCEL = "cancellation"; const val PNR = "pnr"; const val COUNTDOWN = "countdown"
    private const val COUNTDOWN_ID = 4242

    /**
     * The live countdown in the 15 minutes before a window opens: an ongoing notification whose timer counts down to
     * the opening minute, shown on the lock screen, gone a few minutes after it opens. Tapping opens the booking screen.
     */
    fun countdown(context: Context, j: dev.railwatch.shared.domain.Journey) {
        val opens = dev.railwatch.shared.domain.bookingInstant(j).toEpochMilliseconds()
        if (opens <= System.currentTimeMillis()) return
        val open = PendingIntent.getActivity(context, j.id.hashCode() + 1, Intent(context, MainActivity::class.java).putExtra("journey", j.id).putExtra("bookingDay", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val what = if (j.bookingType == "tatkal") "Tatkal" else "Booking"
        val n = NotificationCompat.Builder(context, COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$what opens at ${bookingClock(j)}")
            .setContentText("${j.from} → ${j.to} · ${LocalDate.parse(j.date).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-IN")))}${j.trainName?.let { " · $it" } ?: ""}")
            .setWhen(opens).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setTimeoutAfter(opens - System.currentTimeMillis() + 5 * 60_000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open).setColor(0xFF4C4DDC.toInt())
            .build()
        NotificationManagerCompat.from(context).notify(COUNTDOWN_ID, n)
    }
    private const val MAX = 40 // the next 40 reminders; more are scheduled as these fire or the app opens

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(BOOKING, "Booking Opens", NotificationManager.IMPORTANCE_HIGH).apply { description = "Reminders before IRCTC booking opens for your trips" })
        nm.createNotificationChannel(NotificationChannel(PNR, "PNR Checks", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Reminders to check waitlisted and RAC tickets before travel" })
        nm.createNotificationChannel(NotificationChannel(COUNTDOWN, "Booking Countdown", NotificationManager.IMPORTANCE_HIGH).apply { description = "A live countdown on the lock screen in the last 15 minutes before booking opens"; lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC })
        nm.createNotificationChannel(NotificationChannel(CANCEL,"Cancellations", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Follow-ups for tickets you marked to cancel" })
    }

    fun canExact(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Replaces every scheduled reminder with the ones the current plans need. */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        prefs.getStringSet("keys", emptySet())!!.forEach { am.cancel(pending(context, it, null)) }
        val planner = AndroidPlannerFiles(context).read()?.let { runCatching { PlannerCodec.decode(it) }.getOrNull() } ?: return
        val next = upcomingReminders(planner, todayIst().toString(), kotlinx.datetime.Clock.System.now()).take(MAX)
        for (r in next) {
            val pi = pending(context, r.key, r)
            val at = r.at.toEpochMilliseconds()
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.edit().putStringSet("keys", next.map { it.key }.toSet()).apply()
        Widget.refresh(context)
        PhoneCalendar.syncAsync(context)
    }

    private fun pending(context: Context, key: String, r: Reminder?): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction("dev.railwatch.REMIND").putExtra("key", key)
        if (r != null) i.putExtra("journey", r.journeyId).putExtra("kind", r.kind.name)
        return PendingIntent.getBroadcast(context, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Posts the notification for one reminder, worded from the journey as it is now. */
    fun notify(context: Context, journeyId: String, kind: Reminder.Kind) {
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val p = AndroidPlannerFiles(context).read()?.let { runCatching { PlannerCodec.decode(it) }.getOrNull() } ?: return
        val j = p.journeys.firstOrNull { it.id == journeyId } ?: return
        if (kind == Reminder.Kind.Countdown) { if (allowed) countdown(context, j); return }
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-IN"))
        val travel = LocalDate.parse(j.date).format(fmt)
        val (title, body) = when (kind) {
            Reminder.Kind.Booking -> {
                val opens = bookingDay(j); val today = todayIst().toString()
                val whenText = if (opens == today) "today at ${bookingClock(j)}" else "${LocalDate.parse(opens).format(fmt)} at ${bookingClock(j)}"
                "Booking opens $whenText" to "${j.from} → ${j.to} · $travel${if (j.bookingType == "tatkal") " · Tatkal" else ""}"
            }
            Reminder.Kind.Pnr -> "Check your PNR" to "${j.from} → ${j.to} · $travel · ${(j.pnrStatus?.let(::pnrSummary) ?: j.currentStatus ?: "Waitlisted")} · tap to check"
            Reminder.Kind.Countdown -> return
            Reminder.Kind.Cancellation -> "Cancel your ticket" to "${j.from} → ${j.to} · $travel · cancel on IRCTC, then mark it in RailWatch"
        }
        val open = PendingIntent.getActivity(context, journeyId.hashCode(), Intent(context, MainActivity::class.java).putExtra("journey", journeyId).putExtra("bookingDay", kind == Reminder.Kind.Booking).putExtra("pnr", kind == Reminder.Kind.Pnr).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, when (kind) { Reminder.Kind.Booking -> BOOKING; Reminder.Kind.Pnr -> PNR; else -> CANCEL })
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setContentIntent(open).setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        // The inbox keeps it even if phone notifications are turned off.
        if (allowed) NotificationManagerCompat.from(context).notify(journeyId.hashCode(), n)
        Inbox.add(context, title, body, journeyId)
    }
}

/** Fires a reminder, then schedules what comes next. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val journey = intent.getStringExtra("journey"); val kind = intent.getStringExtra("kind")
        if (journey != null && kind != null) Alarms.notify(context, journey, Reminder.Kind.valueOf(kind))
        Alarms.reschedule(context)
    }
}

/** Restores reminders after a restart, an app update or a time change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Alarms.channels(context); Alarms.reschedule(context) }
}
