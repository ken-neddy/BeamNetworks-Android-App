package com.helper.beamnetworks.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colours, sampled from handoff/logo.png
val BrandNavy = Color(0xFF2E449C)
val BrandOrange = Color(0xFFF26223)

/**
 * Design tokens from the v2 handoff. The handoff's primary blue (#1F5EFF) is replaced by the
 * logo navy, and the logo orange is used for the create button.
 */
object BeamColors {
    val Background = Color(0xFFF3F4F6)
    val Surface = Color.White
    val Ink = Color(0xFF111318)
    val TextSecondary = Color(0xFF5B616B)
    val TextMuted = Color(0xFF8A8F98)
    val IconInactive = Color(0xFFA3A8B0)
    val Divider = Color(0xFFF0F1F3)
    val NavBorder = Color(0xFFECEDF0)
    val InputFill = Color(0xFFF3F4F6)
    val PressedCard = Color(0xFFFAFAFB)
    val PressedNeutral = Color(0xFFE9EAEE)
    val TileFill = Color(0xFFF6F7F9)
    val TilePressed = Color(0xFFECEEF2)
    val SheetHandle = Color(0xFFDADCE0)
    val OfflineDot = Color(0xFFC9CCD2)
    val Scrim = Color(0x73111318)

    val Primary = BrandNavy
    val PrimaryPressed = Color(0xFF263A86)
    val PrimaryTint = Color(0xFFE9ECF7)
    val PrimaryTintPressed = Color(0xFFDCE1F2)

    val Accent = BrandOrange
    val AccentPressed = Color(0xFFDC531A)

    val Success = Color(0xFF15803D)
    val SuccessTint = Color(0xFFE6F8EC)
    val SuccessSolid = Color(0xFF22C55E)
    val ChartCollected = Color(0xFF7ED957)

    val Warning = Color(0xFFB45309)
    val WarningTint = Color(0xFFFEF3E2)
    val WarningSolid = Color(0xFFF59E0B)

    val Danger = Color(0xFFB91C1C)
    val DangerTint = Color(0xFFFDECEC)
    val DangerSolid = Color(0xFFEF4444)

    val Purple = Color(0xFF7C3AED)
    val PurpleTint = Color(0xFFF1E8FF)
    val Teal = Color(0xFF0E7490)
    val TealTint = Color(0xFFE0F7FA)
}

/** A tinted background with its matching foreground, used for icon tiles, tags and avatars. */
data class TonePair(val background: Color, val content: Color)

object Tones {
    val Blue = TonePair(BeamColors.PrimaryTint, BeamColors.Primary)
    val Green = TonePair(BeamColors.SuccessTint, BeamColors.Success)
    val Orange = TonePair(BeamColors.WarningTint, BeamColors.Warning)
    val Red = TonePair(BeamColors.DangerTint, BeamColors.Danger)
    val Purple = TonePair(BeamColors.PurpleTint, BeamColors.Purple)
    val Teal = TonePair(BeamColors.TealTint, BeamColors.Teal)
    val Neutral = TonePair(BeamColors.InputFill, BeamColors.TextSecondary)

    /** Avatar palette, picked by a stable index (customer id, technician name...). */
    val avatarPalette = listOf(Blue, Green, Orange, Red, Purple, Teal)

    fun avatar(seed: Int): TonePair = avatarPalette[Math.floorMod(seed, avatarPalette.size)]
}
