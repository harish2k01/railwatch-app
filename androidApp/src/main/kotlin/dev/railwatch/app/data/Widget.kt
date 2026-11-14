package dev.railwatch.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.railwatch.app.MainActivity
import dev.railwatch.app.R
import dev.railwatch.shared.data.PlannerCodec
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.WidgetFocus
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.effectiveTicketStatus
import dev.railwatch.shared.domain.widgetFocus
import dev.railwatch.shared.domain.widgetNextChange
import dev.railwatch.shared.todayIst
import kotlinx.datetime.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The home-screen widget: the most urgent thing to do, else the next trip. Tapping opens that trip. */
class RailWatchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widget.refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = Widget.refresh(context)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widget.TICK) Widget.refresh(context) else super.onReceive(context, intent)
    }
}

object Widget {
    const val TICK = "dev.railwatch.WIDGET_TICK"
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("en-IN"))
    private fun d(s: String) = LocalDate.parse(s).format(day)
    private fun route(j: Journey) = "${j.from} → ${j.to}"

    /** Redraws every RailWatch widget from the saved plans, and wakes again when the focus next changes by itself. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RailWatchWidget::class.java))
        if (ids.isEmpty()) return
        val planner = AndroidPlannerFiles(context).read()?.let { runCatching { PlannerCodec.decode(it) }.getOrNull() }
        val today = todayIst().toString(); val now = Clock.System.now()
        val focus = planner?.let { widgetFocus(it, today, now) } ?: WidgetFocus.AllSet(null, null)
        val next = planner?.let { dev.railwatch.shared.domain.nextTrip(it, today) }
        for (id in ids) {
            val big = views(context, focus, next, small = false)
            val views = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(SizeF(110f, 110f) to views(context, focus, next, small = true), SizeF(250f, 110f) to big)) else big
            manager.updateAppWidget(id, views)
        }
        // Wake at the next change (an hour before a window opens, and when it opens); midnight is covered by the 30-min update.
        val am = context.getSystemService(AlarmManager::class.java)
        val tick = PendingIntent.getBroadcast(context, 7, Intent(context, RailWatchWidget::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(tick)
        planner?.let { widgetNextChange(it, today, now) }?.let { am.set(AlarmManager.RTC, it.toEpochMilliseconds(), tick) }
    }

    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("en-IN"))
    private fun timeIst(at: kotlinx.datetime.Instant) = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(java.time.ZoneId.of("Asia/Kolkata"))
    /** "in 7h 05m", "in 25m" until an instant. */
    private fun until(at: kotlinx.datetime.Instant): String {
        val m = ((at.toEpochMilliseconds() - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        return if (m >= 60) "in ${m / 60}h ${"%02d".format(m % 60)}m" else "in ${m}m"
    }
    /** Date, departure and train, skipping what isn't known. */
    private fun details(j: Journey) = listOfNotNull(d(j.date), j.departure.ifBlank { null }, (j.trainName ?: j.train).ifBlank { null }).joinToString(" · ")

    private fun views(context: Context, focus: WidgetFocus, next: Journey?, small: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget)
        var brand = false; var journey: Journey? = null; var chip: String? = null; var chipStyle = R.drawable.widget_chip_dark
        v.setViewVisibility(R.id.countdown, View.GONE); v.setViewVisibility(R.id.headline, View.VISIBLE)
        when (focus) {
            is WidgetFocus.Opening -> {
                brand = true; journey = focus.journey; val j = focus.journey
                v.setTextViewText(R.id.label, "${if (j.bookingType == "tatkal") "Tatkal" else "Booking"} opens ${timeIst(focus.at).format(clock)}")
                v.setViewVisibility(R.id.headline, View.GONE); v.setViewVisibility(R.id.countdown, View.VISIBLE)
                v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + (focus.at.toEpochMilliseconds() - System.currentTimeMillis()), null, true)
                v.setTextViewText(R.id.sub, "${route(j)} · ${d(j.date)}")
                chip = "Get ready"
            }
            is WidgetFocus.Travelling -> {
                journey = focus.journey; val j = focus.journey
                v.setTextViewText(R.id.label, "Travelling today")
                v.setTextViewText(R.id.headline, route(j))
                v.setTextViewText(R.id.sub, listOfNotNull("Departs ${j.departure}", listOfNotNull(j.coach, j.seat ?: j.berth).joinToString(" ").ifBlank { null }).joinToString(" · "))
                chip = effectiveTicketStatus(j) ?: j.currentStatus; chipStyle = R.drawable.widget_chip_green
            }
            is WidgetFocus.Book -> {
                brand = true; journey = focus.journey; val j = focus.journey
                v.setTextViewText(R.id.label, "Booking is open")
                v.setTextViewText(R.id.headline, route(j))
                v.setTextViewText(R.id.sub, "${details(j)} · not booked yet")
                chip = if (focus.count > 1) "${focus.count} to book" else "Book now"
            }
            is WidgetFocus.OpensSoon -> {
                journey = focus.journey; val j = focus.journey
                val today = todayIst().toString(); val day = timeIst(focus.at).toLocalDate().toString()
                v.setTextViewText(R.id.label, "Booking opens ${if (day == today) "today" else "tomorrow"} ${timeIst(focus.at).format(clock)}")
                v.setTextViewText(R.id.headline, route(j))
                v.setTextViewText(R.id.sub, details(j))
                chip = until(focus.at)
            }
            is WidgetFocus.AllSet -> {
                journey = focus.nextTrip; val j = focus.nextTrip
                v.setTextViewText(R.id.label, if (j == null) "RailWatch" else "Next trip")
                v.setTextViewText(R.id.headline, j?.let { route(it) } ?: "No trips planned")
                v.setTextViewText(R.id.sub, j?.let { details(it) } ?: "Tap + in RailWatch to add one")
                chip = j?.let { effectiveTicketStatus(it) ?: if (it.status == dev.railwatch.shared.domain.JourneyStatus.Booked) "Booked" else null }
                if (j?.status == dev.railwatch.shared.domain.JourneyStatus.Booked) chipStyle = R.drawable.widget_chip_green
            }
        }
        if (brand) chipStyle = R.drawable.widget_chip_light
        v.setViewVisibility(R.id.chip, if (chip.isNullOrBlank()) View.GONE else View.VISIBLE)
        chip?.let { v.setTextViewText(R.id.chip, it) }
        v.setInt(R.id.chip, "setBackgroundResource", chipStyle)
        v.setTextColor(R.id.chip, when (chipStyle) { R.drawable.widget_chip_green -> 0xFF86EFAC.toInt(); R.drawable.widget_chip_light -> 0xFFFFFFFF.toInt(); else -> 0xFFC7C8FC.toInt() })
        // The bottom line: the next trip, unless the card is already about it; else the next booking day when all set.
        val bottom = when {
            small -> null
            focus is WidgetFocus.AllSet -> focus.nextBookingDay?.let { "Next booking day: ${d(it)}" }
            next != null && next.id != journey?.id -> "Next trip: ${d(next.date)} · ${route(next)} · ${next.departure}"
            else -> null
        }
        v.setViewVisibility(R.id.next, if (bottom == null) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.divider, if (bottom == null) View.GONE else View.VISIBLE)
        bottom?.let { v.setTextViewText(R.id.next, it) }
        v.setInt(R.id.root, "setBackgroundResource", if (brand) R.drawable.widget_brand else R.drawable.widget_surface)
        v.setInt(R.id.logo, "setColorFilter", if (brand) 0xFFFFFFFF.toInt() else 0xFFA5A6FA.toInt())
        v.setTextColor(R.id.label, if (brand) 0xFFE3E3FF.toInt() else 0xFFA5A6FA.toInt())
        v.setTextColor(R.id.sub, if (brand) 0xFFE3E3FF.toInt() else 0xFFB3BAC6.toInt())
        v.setTextColor(R.id.next, if (brand) 0xFFFFFFFF.toInt() else 0xFFC9CED8.toInt())
        v.setInt(R.id.divider, "setBackgroundColor", if (brand) 0x40FFFFFF else 0xFF232B38.toInt())
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        journey?.let { open.putExtra("journey", it.id) }
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 9 + (if (small) 1 else 0), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return v
    }
}
