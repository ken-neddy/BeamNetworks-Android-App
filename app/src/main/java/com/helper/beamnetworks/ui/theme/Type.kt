package com.helper.beamnetworks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.helper.beamnetworks.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, tight: Boolean = false, lineHeight: Float? = null, numbers: Boolean = false) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = if (tight) (-0.02).em else 0.em,
    lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
    // Tabular figures keep amounts and counts aligned as they change
    fontFeatureSettings = if (numbers) "tnum" else null,
    color = BeamColors.Ink,
)

/** Named text roles from the handoff. */
object BeamType {
    val Greeting = style(26, FontWeight.ExtraBold, tight = true, lineHeight = 1.15f)
    val Hero = style(30, FontWeight.ExtraBold, tight = true, lineHeight = 1f, numbers = true)
    val Net = style(28, FontWeight.ExtraBold, tight = true, numbers = true)
    val Stat = style(22, FontWeight.ExtraBold, tight = true, numbers = true)
    val StatLarge = style(24, FontWeight.ExtraBold, numbers = true)
    val EmptyTitle = style(20, FontWeight.ExtraBold)
    val DetailName = style(20, FontWeight.ExtraBold).copy(letterSpacing = (-0.01).em)
    val SheetTitle = style(19, FontWeight.ExtraBold).copy(letterSpacing = (-0.01).em)
    val SectionTitle = style(16, FontWeight.Bold)
    val AppBarTitle = style(16, FontWeight.Bold)
    val Button = style(15, FontWeight.Bold)
    val RowTitleLarge = style(15, FontWeight.Bold)
    val RowTitle = style(14, FontWeight.Bold)
    val Body = style(14, FontWeight.Normal)
    val BodyStrong = style(14, FontWeight.SemiBold)
    val Small = style(13, FontWeight.Normal)
    val SmallStrong = style(13, FontWeight.SemiBold)
    val Meta = style(12, FontWeight.Normal).copy(color = BeamColors.TextMuted)
    val MetaStrong = style(12, FontWeight.SemiBold)
    val Amount = style(12, FontWeight.Bold, numbers = true)
    val Label = style(12, FontWeight.SemiBold).copy(color = BeamColors.TextSecondary)
    val Tag = style(11, FontWeight.SemiBold)
    val PillTab = style(13, FontWeight.SemiBold)
    val Tiny = style(10, FontWeight.SemiBold)
}

val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PlusJakartaSans),
        displayMedium = displayMedium.copy(fontFamily = PlusJakartaSans),
        displaySmall = displaySmall.copy(fontFamily = PlusJakartaSans),
        headlineLarge = headlineLarge.copy(fontFamily = PlusJakartaSans),
        headlineMedium = headlineMedium.copy(fontFamily = PlusJakartaSans),
        headlineSmall = headlineSmall.copy(fontFamily = PlusJakartaSans),
        titleLarge = titleLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = PlusJakartaSans),
        bodyMedium = bodyMedium.copy(fontFamily = PlusJakartaSans),
        bodySmall = bodySmall.copy(fontFamily = PlusJakartaSans),
        labelLarge = labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    )
}
