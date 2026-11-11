package dev.railwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** RailWatch colour tokens: the same values as the web app's --rw-* variables (globals.css). */
@Immutable
data class RwColors(
    val ground: Color, val surface: Color, val surface2: Color, val line: Color, val text: Color, val muted: Color,
    val brand: Color, val brandInk: Color, val brandSoft: Color, val brandSoftInk: Color,
    val open: Color, val openBg: Color, val openInk: Color,
    val booked: Color, val bookedBg: Color, val bookedInk: Color,
    val attention: Color, val attentionBg: Color, val attentionInk: Color,
    val cancelBg: Color, val cancelInk: Color, val timeoffBg: Color, val timeoffInk: Color, val danger: Color,
)

val DarkRw = RwColors(
    ground = Color(0xFF0B0F17), surface = Color(0xFF141A24), surface2 = Color(0xFF1A2230), line = Color(0xFF232B38),
    text = Color(0xFFEEF1F5), muted = Color(0xFF98A2B3),
    brand = Color(0xFF8B8CF6), brandInk = Color(0xFF0E0F2E), brandSoft = Color(0xFF1F2046), brandSoftInk = Color(0xFFC7C8FC),
    open = Color(0xFF60A5FA), openBg = Color(0xFF13243D), openInk = Color(0xFF93C5FD),
    booked = Color(0xFF4ADE80), bookedBg = Color(0xFF12301F), bookedInk = Color(0xFF86EFAC),
    attention = Color(0xFFFBBF24), attentionBg = Color(0xFF2A2210), attentionInk = Color(0xFFFBBF24),
    cancelBg = Color(0xFF2D1416), cancelInk = Color(0xFFFCA5A5), timeoffBg = Color(0xFF2A1D3B), timeoffInk = Color(0xFFD8B4FE),
    danger = Color(0xFFF87171),
)

val LightRw = RwColors(
    ground = Color(0xFFF6F5F2), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF1EFEB), line = Color(0xFFE2DFD9),
    text = Color(0xFF141821), muted = Color(0xFF5B6472),
    brand = Color(0xFF4C4DDC), brandInk = Color(0xFFFFFFFF), brandSoft = Color(0xFFE9E9FD), brandSoftInk = Color(0xFF3B3CB8),
    open = Color(0xFF3B82F6), openBg = Color(0xFFE0ECFF), openInk = Color(0xFF1D4ED8),
    booked = Color(0xFF16A34A), bookedBg = Color(0xFFDCFCE7), bookedInk = Color(0xFF166534),
    attention = Color(0xFFD97706), attentionBg = Color(0xFFFEF3C7), attentionInk = Color(0xFF92400E),
    cancelBg = Color(0xFFFEE2E2), cancelInk = Color(0xFFB91C1C), timeoffBg = Color(0xFFF3E8FF), timeoffInk = Color(0xFF6B21A8),
    danger = Color(0xFFDC2626),
)

val LocalRw = staticCompositionLocalOf { DarkRw }

/** The RailWatch palette, reachable as `Rw.colors` anywhere under [RailWatchTheme]. */
object Rw { val colors: RwColors @Composable get() = LocalRw.current }

@Composable
fun RailWatchTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val rw = if (dark) DarkRw else LightRw
    val scheme = if (dark) darkColorScheme(primary = rw.brand, onPrimary = rw.brandInk, background = rw.ground, surface = rw.surface, onBackground = rw.text, onSurface = rw.text, error = rw.danger)
    else lightColorScheme(primary = rw.brand, onPrimary = rw.brandInk, background = rw.ground, surface = rw.surface, onBackground = rw.text, onSurface = rw.text, error = rw.danger)
    CompositionLocalProvider(LocalRw provides rw) {
        MaterialTheme(colorScheme = scheme, typography = RailWatchTypography, content = content)
    }
}
