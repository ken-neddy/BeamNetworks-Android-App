package com.helper.beamnetworks.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC licence) as used in the design handoff: 24x24 outline, round caps and joins.
 * Rects and circles are written as path data.
 */
object BeamIcons {
    val LayoutGrid by lazy {
        lucide("LayoutGrid", rect(3f, 3f, 7f, 7f, 1.5f), rect(14f, 3f, 7f, 7f, 1.5f), rect(14f, 14f, 7f, 7f, 1.5f), rect(3f, 14f, 7f, 7f, 1.5f))
    }
    val Users by lazy {
        lucide("Users", "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", circle(9f, 7f, 4f), "M22 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75")
    }
    val Wrench by lazy {
        lucide("Wrench", "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z")
    }
    val Ticket by lazy {
        lucide("Ticket", "M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z", "M13 5v2", "M13 17v2", "M13 11v2")
    }
    val Bell by lazy { lucide("Bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val ChevronLeft by lazy { lucide("ChevronLeft", "m15 18-6-6 6-6") }
    val ChevronRight by lazy { lucide("ChevronRight", "m9 18 6-6-6-6") }
    val ChevronDown by lazy { lucide("ChevronDown", "m6 9 6 6 6-6") }
    val Search by lazy { lucide("Search", circle(11f, 11f, 7f), "m21 21-4.3-4.3") }
    val Plus by lazy { lucide("Plus", "M5 12h14", "M12 5v14") }
    val Minus by lazy { lucide("Minus", "M5 12h14") }
    val X by lazy { lucide("X", "M18 6 6 18", "m6 6 12 12") }
    val Calendar by lazy { lucide("Calendar", rect(3f, 4f, 18f, 18f, 2f), "M16 2v4", "M8 2v4", "M3 10h18") }
    val Clock by lazy { lucide("Clock", circle(12f, 12f, 10f), "M12 6v6l4 2") }
    val Package by lazy { lucide("Package", "M21 8 12 3 3 8v8l9 5 9-5z", "m3 8 9 5 9-5", "M12 13v8") }
    val Banknote by lazy { lucide("Banknote", rect(2f, 6f, 20f, 12f, 2f), circle(12f, 12f, 2f)) }
    val Link by lazy {
        lucide("Link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
    }
    val Activity by lazy { lucide("Activity", "M22 12h-4l-3 9L9 3l-3 9H2") }
    val PauseCircle by lazy { lucide("PauseCircle", circle(12f, 12f, 10f), "M10 15V9", "M14 15V9") }
    val Check by lazy { lucide("Check", "M20 6 9 17l-5-5") }
    val Phone by lazy {
        lucide("Phone", "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z")
    }
    val MapPin by lazy { lucide("MapPin", "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z", circle(12f, 10f, 3f)) }
    val MessageSquare by lazy { lucide("MessageSquare", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val Navigation by lazy { lucide("Navigation", "M3 11 22 2l-9 19-2-8-8-2z") }
    val Pencil by lazy { lucide("Pencil", "M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z", "m15 5 4 4") }
    val Trash by lazy { lucide("Trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2") }
    val ExternalLink by lazy { lucide("ExternalLink", "M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6") }
    val Menu by lazy { lucide("Menu", "M4 6h16", "M4 12h16", "M4 18h16") }
    val Settings by lazy {
        lucide(
            "Settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }
    val Wifi by lazy { lucide("Wifi", "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0") }
    val CreditCard by lazy { lucide("CreditCard", rect(2f, 5f, 20f, 14f, 2f), "M2 10h20") }
    val FileText by lazy { lucide("FileText", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8") }
    val Send by lazy { lucide("Send", "m22 2-7 20-4-9-9-4Z", "M22 2 11 13") }
    val Archive by lazy { lucide("Archive", rect(2f, 3f, 20f, 5f, 1f), "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4") }
    val Refresh by lazy {
        lucide("Refresh", "M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16", "M16 16h5v5")
    }
    val LogOut by lazy { lucide("LogOut", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "m16 17 5-5-5-5", "M21 12H9") }
    val Alert by lazy { lucide("Alert", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01") }
}

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}H${x + w - r}A$r $r 0 0 1 ${x + w} ${y + r}V${y + h - r}A$r $r 0 0 1 ${x + w - r} ${y + h}" +
        "H${x + r}A$r $r 0 0 1 $x ${y + h - r}V${y + r}A$r $r 0 0 1 ${x + r} ${y}Z"

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

private fun lucide(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()
