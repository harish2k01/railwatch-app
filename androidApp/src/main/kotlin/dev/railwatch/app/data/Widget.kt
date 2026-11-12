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

    private fun views(context: Context, focus: WidgetFocus, next: Journey?, small: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget)
        var brand = false; var journey: Journey? = null
        v.setViewVisibility(R.id.countdown, View.GONE); v.setViewVisibility(R.id.headline, View.VISIBLE)
        when (focus) {
            is WidgetFocus.Opening -> {
                brand = true; journey = focus.journey
                val j = focus.journey
                v.setTextViewText(R.id.label, if (j.bookingType == "tatkal") "Tatkal opens in" else "Booking opens in")
                v.setViewVisibility(R.id.headline, View.GONE); v.setViewVisibility(R.id.countdown, View.VISIBLE)
                val left = focus.at.toEpochMilliseconds() - System.currentTimeMillis()
                v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, null, true)
                v.setTextViewText(R.id.sub, "${bookingClock(j)} · ${d(j.date)} · ${route(j)}")
            }
            is WidgetFocus.Travelling -> {
                journey = focus.journey; val j = focus.journey
                v.setTextViewText(R.id.label, "Today · departs ${j.departure}")
                v.setTextViewText(R.id.headline, route(j))
                v.setTextViewText(R.id.sub, listOfNotNull(effectiveTicketStatus(j) ?: j.currentStatus, j.trainName ?: j.train.ifBlank { null }).joinToString(" · "))
            }
            is WidgetFocus.Book -> {
                brand = true; journey = focus.journey; val j = focus.journey
                v.setTextViewText(R.id.label, if (focus.count > 1) "Book now · ${focus.count} trips open" else "Book now · window open")
                v.setTextViewText(R.id.headline, if (small) d(j.date) else "${d(j.date)} · ${route(j)}")
                v.setTextViewText(R.id.sub, if (small) route(j) else "Not booked yet${j.trainName?.let { " · $it" } ?: ""}")
            }
            is WidgetFocus.AllSet -> {
                journey = focus.nextTrip
                v.setTextViewText(R.id.label, "All set")
                v.setTextViewText(R.id.headline, focus.nextTrip?.let { if (small) d(it.date) else "Next: ${d(it.date)} · ${route(it)}" } ?: "No trips planned")
                v.setTextViewText(R.id.sub, focus.nextBookingDay?.let { "Next booking day ${d(it)}" } ?: "Tap + in RailWatch to add one")
            }
        }
        // The next trip under the focus, unless the focus is already that trip (or there's no room).
        val showNext = !small && next != null && next.id != journey?.id && focus !is WidgetFocus.AllSet
        v.setViewVisibility(R.id.next, if (showNext) View.VISIBLE else View.GONE)
        if (showNext) v.setTextViewText(R.id.next, "Next trip ${d(next!!.date)} · ${route(next)} · ${next.departure}")
        v.setInt(R.id.root, "setBackgroundResource", if (brand) R.drawable.widget_brand else R.drawable.widget_surface)
        v.setTextColor(R.id.label, if (brand) 0xFFE3E3FF.toInt() else 0xFFA5A6FA.toInt())
        v.setTextColor(R.id.sub, if (brand) 0xFFE3E3FF.toInt() else 0xFFB3BAC6.toInt())
        v.setTextColor(R.id.next, if (brand) 0xFFFFFFFF.toInt() else 0xFFC9CED8.toInt())
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        journey?.let { open.putExtra("journey", it.id) }
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(context, 9 + (if (small) 1 else 0), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return v
    }
}
