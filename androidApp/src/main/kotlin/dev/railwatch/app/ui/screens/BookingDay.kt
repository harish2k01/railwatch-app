package dev.railwatch.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.data.AppModel
import dev.railwatch.app.ui.components.PrimaryButton
import dev.railwatch.app.ui.components.SecondaryButton
import dev.railwatch.app.ui.shortDay
import dev.railwatch.app.ui.theme.Rw
import dev.railwatch.shared.domain.Journey
import dev.railwatch.shared.domain.JourneyStatus
import dev.railwatch.shared.domain.bookingClock
import dev.railwatch.shared.domain.bookingInstant
import kotlinx.coroutines.delay

/**
 * Booking day: a countdown to when IRCTC opens booking, a short checklist, then Open IRCTC and the outcome
 * (I've booked it, Try Tatkal, Skip). Opened from a booking notification or the journey sheet.
 */
@Composable
fun BookingDaySheet(j: Journey, model: AppModel, close: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val left = (bookingInstant(j).toEpochMilliseconds() - now) / 1000
    val tatkal = j.bookingType == "tatkal"
    val checks = remember { mutableStateListOf(false, false, false) }
    val items = listOf("Signed in to IRCTC", "Passenger details saved in Master List", "UPI or wallet ready to pay")

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(if (tatkal) "TATKAL BOOKING" else "BOOKING DAY", style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted)
        Text("${j.from} → ${j.to}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Rw.colors.text)
        Text("Travel ${shortDay(j.date)}${if (j.train.isNotEmpty()) " · ${j.train}" else ""}", color = Rw.colors.muted)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().background(if (left > 0) Rw.colors.openBg else Rw.colors.bookedBg, RoundedCornerShape(20.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (left > 0) {
                Text("Opens at ${bookingClock(j)} in", color = Rw.colors.openInk)
                Text(countdown(left), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Rw.colors.openInk)
            } else {
                Text("Booking is open", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Rw.colors.bookedInk)
                Text("Book now, seats go fast", color = Rw.colors.bookedInk)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if (left > 0) "Before it opens" else "Before you book",fontWeight = FontWeight.SemiBold, color = Rw.colors.text)
        items.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().clickable { checks[i] = !checks[i] }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(22.dp).background(if (checks[i]) Rw.colors.brand else Rw.colors.surface2, CircleShape))
                Text(label, color = if (checks[i]) Rw.colors.muted else Rw.colors.text, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Open IRCTC", { openIrctc(context) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Booked", { model.setStatus(j, JourneyStatus.Booked); close() }, Modifier.weight(1f))
            if (!tatkal) SecondaryButton("Try Tatkal", { model.saveJourney(j.copy(bookingType = "tatkal", tatkalClass = j.tatkalClass ?: if (j.travelClass in listOf("SL", "2S")) "non_ac" else "ac", trainOriginDate = j.trainOriginDate ?: j.date)); close() }, Modifier.weight(1f))
            SecondaryButton("Skip", { model.setStatus(j, JourneyStatus.Skipped); close() }, Modifier.weight(1f))
        }
    }
}

private fun countdown(s: Long): String {
    val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60; val sec = s % 60
    return if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** The IRCTC app if it is installed, otherwise the website. */
private fun openIrctc(context: android.content.Context) {
    val app = context.packageManager.getLaunchIntentForPackage("cris.org.in.prs.ima")
    context.startActivity(app ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.irctc.co.in/nget/train-search")))
}
