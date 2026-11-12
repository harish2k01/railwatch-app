package dev.railwatch.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.hypot

/** RailWatch purple, the launch-screen colour (res/values-v31/themes.xml uses the same). */
val SplashPurple = Color(0xFF4C4DDC)

/**
 * Launch animation over the app: the R monogram draws itself (its stem is a short stretch of track, rails then
 * sleepers), "RailWatch" rises in, then the purple opens out in a growing circle to reveal the app. ~1.3 s.
 */
@Composable
fun Splash(done: () -> Unit) {
    val rails = remember { Animatable(0f) }; val sleepers = remember { Animatable(0f) }; val bowl = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }; val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            rails.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            listOf(async { sleepers.animateTo(1f, tween(260)) }, async { bowl.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }, async { word.animateTo(1f, tween(420, 120)) }).awaitAll()
            reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        done()
    }
    // The purple layer with a hole that grows from the centre: the app shows through it.
    Box(Modifier.fillMaxSize().drawWithContent {
        val r = reveal.value * hypot(size.width, size.height) / 2
        val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
    }) {
        Box(Modifier.fillMaxSize().background(SplashPurple), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { val s = 1f - reveal.value * 0.15f; scaleX = s; scaleY = s; alpha = 1f - reveal.value }) {
                Canvas(Modifier.size(132.dp)) {
                    val u = size.width / 108f
                    fun p(x: Float, y: Float) = Offset(x * u, y * u)
                    val w = Stroke(width = 7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    // Two rails for the stem, drawn top to bottom.
                    for (x in listOf(36f, 48f)) drawLine(Color.White, p(x, 26f), p(x, 26f + 56f * rails.value), 6f * u, StrokeCap.Round)
                    // Sleepers across the rails, appearing one after another.
                    for (i in 0 until 5) {
                        val t = ((sleepers.value * 5) - i).coerceIn(0f, 1f)
                        if (t > 0f) drawLine(Color.White.copy(alpha = t), p(31f, 33f + i * 11f), p(53f, 33f + i * 11f), 4f * u, StrokeCap.Round)
                    }
                    // The bowl and leg of the R, traced as one stroke.
                    val path = Path().apply { moveTo(48f * u, 26f * u); lineTo(60f * u, 26f * u); cubicTo(78f * u, 26f * u, 78f * u, 54f * u, 60f * u, 54f * u); lineTo(48f * u, 54f * u); moveTo(58f * u, 54f * u); lineTo(76f * u, 82f * u) }
                    val m = PathMeasure().apply { setPath(path, false) }
                    val part = Path(); m.getSegment(0f, m.length * bowl.value, part, true)
                    drawPath(part, Color.White, style = w)
                }
                Text("RailWatch", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 18.dp).graphicsLayer { alpha = word.value; translationY = (1f - word.value) * 24.dp.toPx() })
            }
        }
    }
}
