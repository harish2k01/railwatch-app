package dev.railwatch.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * RailWatch's line icons (the canvas's 24×24 stroke paths), built in code so the app needs no icon library.
 * They are drawn in black and tinted by Icon.
 */
object RwIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 2f) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    val Home = stroke("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val List = stroke("list", "M4 6h16M4 12h16M4 18h10")
    val Ticket = stroke("ticket", "M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2a2 2 0 0 0 0 6v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-6z")
    val Person = stroke("person", "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M4 21a8 8 0 0 1 16 0")
    val Plus = stroke("plus", "M12 5v14M5 12h14", width = 2.6f)
    val Bell = stroke("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8", "M10 20a2 2 0 0 0 4 0")
    val Clock = stroke("clock", "M12 5a8 8 0 1 0 0 16 8 8 0 0 0 0-16z", "M12 9v4l2 2M9 2h6")
    val Calendar = stroke("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 10h18M8 3v4M16 3v4")
    val Close = stroke("close", "M18 6 6 18M6 6l12 12", width = 2.4f)
    val Check = stroke("check", "M5 12l5 5 9-10", width = 3f)
    val ChevronRight = stroke("chevron-right", "M9 6l6 6-6 6")
    val ChevronLeft = stroke("chevron-left", "M15 6l-6 6 6 6")
    val Share = stroke("share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M16 6l-4-4-4 4M12 2v13")
    val Search = stroke("search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z", "M20 20l-3.5-3.5")
}
