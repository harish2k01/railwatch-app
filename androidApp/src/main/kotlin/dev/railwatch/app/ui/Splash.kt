package dev.railwatch.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** RailWatch purple, the launch-screen colour (res/values-v31/themes.xml uses the same). */
val SplashPurple = Color(0xFF4C4DDC)

/**
 * Launch animation (~2 s): a track is laid across the middle of the screen, a train runs along it and off the edge,
 * the track fades as the R monogram and "RailWatch" come up in the centre, then the purple opens out in a growing
 * circle onto the app.
 */
@Composable
fun Splash(done: () -> Unit) {
    val track = remember { Animatable(0f) }; val train = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }; val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { track.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
        delay(260)
        // An even pace (gentle start and stop) so the train reads as it crosses.
        launch { train.animateTo(1f, tween(1250, easing = androidx.compose.animation.core.CubicBezierEasing(0.35f, 0f, 0.65f, 1f))) }
        delay(1050)
        logo.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        delay(260)
        reveal.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
        done()
    }
    // The purple layer with a hole that grows from the centre: the app shows through it.
    Box(Modifier.fillMaxSize().drawWithContent {
        val r = reveal.value * hypot(size.width, size.height) / 2
        clipPath(Path().apply { addOval(Rect(center, r)) }, ClipOp.Difference) { this@drawWithContent.drawContent() }
    }) {
        Box(Modifier.fillMaxSize().background(SplashPurple), contentAlignment = Alignment.Center) {
            // Track and train, fading out as the logo arrives.
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - logo.value }) {
                val y = size.height / 2 + 70.dp.toPx()
                val gauge = 14.dp.toPx(); val laid = size.width * track.value
                val white = Color.White
                // Sleepers, laid left to right.
                val step = 22.dp.toPx(); var x = step / 2
                while (x < laid) { drawLine(white.copy(alpha = .55f), Offset(x, y - gauge / 2 - 5.dp.toPx()), Offset(x, y + gauge / 2 + 5.dp.toPx()), 4.dp.toPx(), StrokeCap.Round); x += step }
                // Two rails.
                drawLine(white, Offset(0f, y - gauge / 2), Offset(laid, y - gauge / 2), 3.dp.toPx())
                drawLine(white, Offset(0f, y + gauge / 2), Offset(laid, y + gauge / 2), 3.dp.toPx())
                // The train: an engine and two coaches running left to right, off the far edge.
                val car = 74.dp.toPx(); val gap = 6.dp.toPx(); val length = car * 3 + gap * 2
                // The engine's nose goes from the left edge (train hidden) to past the right edge (train gone).
                val head = (size.width + length) * train.value
                for (i in 0 until 3) {
                    val left = head - (i + 1) * car - i * gap
                    drawCar(Offset(left, y - gauge / 2 - 44.dp.toPx()), Size(car, 40.dp.toPx()), engine = i == 0)
                }
            }
            // The logo and name in the centre.
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
                alpha = logo.value * (1f - reveal.value); val s = 0.8f + 0.2f * logo.value - 0.12f * reveal.value; scaleX = s; scaleY = s
            }) {
                Canvas(Modifier.size(112.dp)) { drawMonogram() }
                Text("RailWatch", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 14.dp).graphicsLayer { translationY = (1f - logo.value) * 18.dp.toPx() })
            }
        }
    }
}

/** One carriage: a rounded body with windows; the engine has a sloped nose and a headlight. */
private fun DrawScope.drawCar(at: Offset, size: Size, engine: Boolean) {
    val r = 10.dp.toPx()
    if (engine) {
        val nose = Path().apply {
            moveTo(at.x + r, at.y); lineTo(at.x + size.width - 22.dp.toPx(), at.y)
            cubicTo(at.x + size.width - 4.dp.toPx(), at.y, at.x + size.width, at.y + size.height * 0.55f, at.x + size.width, at.y + size.height - r)
            lineTo(at.x + size.width, at.y + size.height); lineTo(at.x, at.y + size.height); lineTo(at.x, at.y + r)
            quadraticTo(at.x, at.y, at.x + r, at.y); close()
        }
        drawPath(nose, Color.White)
        drawCircle(Color(0xFFFFE08A), 3.5.dp.toPx(), Offset(at.x + size.width - 6.dp.toPx(), at.y + size.height - 9.dp.toPx()))
    } else drawRoundRect(Color.White, at, size, CornerRadius(r))
    val w = 12.dp.toPx(); val h = 10.dp.toPx(); val top = at.y + 8.dp.toPx()
    val count = if (engine) 2 else 4
    for (i in 0 until count) drawRoundRect(SplashPurple, Offset(at.x + 8.dp.toPx() + i * (w + 5.dp.toPx()), top), Size(w, h), CornerRadius(3.dp.toPx()))
    // Wheels.
    for (fx in listOf(0.22f, 0.78f)) drawCircle(Color.White, 4.dp.toPx(), Offset(at.x + size.width * fx, at.y + size.height + 3.dp.toPx()))
}

/** The app icon's R: an R whose stem is cut by sleeper gaps. */
fun DrawScope.drawMonogram(ink: Color = Color.White, cut: Color = SplashPurple) {
    val u = size.width / 108f
    val path = Path().apply {
        moveTo(40f * u, 78f * u); lineTo(40f * u, 30f * u); lineTo(56f * u, 30f * u)
        cubicTo(72f * u, 30f * u, 72f * u, 54f * u, 56f * u, 54f * u); lineTo(40f * u, 54f * u)
        moveTo(54f * u, 54f * u); lineTo(70f * u, 78f * u)
    }
    drawPath(path, ink, style = Stroke(9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    for (y in listOf(40f, 50f, 60f, 70f)) drawLine(cut, Offset(35f * u, y * u), Offset(45f * u, y * u), 3f * u)
}
