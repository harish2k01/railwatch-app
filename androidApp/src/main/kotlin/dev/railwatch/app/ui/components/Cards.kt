package dev.railwatch.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.railwatch.app.ui.theme.Rw
import kotlin.math.roundToInt

/** Section heading with an optional action on the right ("Do Next · See all"). */
@Composable
fun SectionHead(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Rw.colors.text, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = MaterialTheme.typography.titleMedium, color = Rw.colors.brand, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
    }
}

/** Uppercase month heading between list groups. */
@Composable
fun MonthHeader(label: String) = Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))

/** A dashed rule with half-circle notches at both ends: the boarding-pass tear line. */
@Composable
fun Perforation(notch: Color, line: Color) {
    Box(Modifier.fillMaxWidth().height(24.dp)) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            drawLine(line, Offset(14.dp.toPx(), size.height / 2), Offset(size.width - 14.dp.toPx(), size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawCircle(notch, 12.dp.toPx(), Offset(0f, size.height / 2)); drawCircle(notch, 12.dp.toPx(), Offset(size.width, size.height / 2))
        }
    }
}

/** A label above a value, used in pass and sheet fact rows. */
@Composable
fun Fact(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = .65f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Coloured status dot at the end of a row. */
enum class Tone { Ok, Warn, Info, Muted }

@Composable
fun toneColor(t: Tone) = when (t) { Tone.Ok -> Rw.colors.booked; Tone.Warn -> Rw.colors.attention; Tone.Info -> Rw.colors.open; Tone.Muted -> Rw.colors.muted }

/** One journey row: date tile, route, detail line, status dot. The whole row opens the journey. */
@Composable
fun JourneyRow(day: String, weekday: String, route: String, detail: String, tone: Tone, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Rw.colors.surface).clickable(onClickLabel = "Open journey", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$route, $status, $weekday $day" }
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Rw.colors.text, style = MaterialTheme.typography.titleMedium)
            Text(weekday, style = MaterialTheme.typography.labelSmall, color = Rw.colors.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(route, style = MaterialTheme.typography.titleMedium, color = Rw.colors.text)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (tone == Tone.Info) Rw.colors.openInk else Rw.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(toneColor(tone)))
    }
}

/** An action revealed behind a row by swiping left. */
data class SwipeAction(val label: String, val icon: ImageVector, val background: Color, val content: Color, val run: () -> Unit)

/** A row that slides left to reveal up to two actions; a short drag springs back. */
@Composable
fun SwipeRow(actions: List<SwipeAction>, content: @Composable () -> Unit) {
    if (actions.isEmpty()) { content(); return }
    val width = with(LocalDensity.current) { (78 * actions.size).dp.toPx() }
    var target by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableFloatStateOf(0f) } // 1 while the finger is down
    val shown by animateFloatAsState(if (dragging > 0f) drag else target, label = "swipe")
    // Measured once (no intrinsic pass) and the actions are only composed while the row is moved, so lists scroll smoothly.
    Box(Modifier.fillMaxWidth()) {
        if (shown < -0.5f) Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { a ->
                Column(Modifier.width(78.dp).fillMaxHeight().background(a.background).clickable(onClickLabel = a.label) { target = 0f; a.run() },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(a.icon, contentDescription = null, tint = a.content)
                    Text(a.label, style = MaterialTheme.typography.labelMedium, color = a.content)
                }
            }
        }
        Box(Modifier.offset { IntOffset(shown.roundToInt(), 0) }.pointerInput(width) {
            detectHorizontalDragGestures(
                onDragStart = { drag = target; dragging = 1f },
                onDragEnd = { target = if (drag < -width / 2) -width else 0f; dragging = 0f },
                onDragCancel = { dragging = 0f },
            ) { _, dx -> drag = (drag + dx).coerceIn(-width - 40f, 0f) }
        }) { content() }
    }
}

/** A grouped, rounded list container. */
@Composable
fun Grouped(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rw.colors.surface)) { content() }
}

/** Thin divider inside a grouped list. */
@Composable
fun RowDivider() = Spacer(Modifier.fillMaxWidth().height(1.dp).background(Rw.colors.line))
