package com.helper.beamnetworks.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BeamColors.Primary,
    onPrimary = Color.White,
    primaryContainer = BeamColors.PrimaryTint,
    onPrimaryContainer = BeamColors.Primary,

    secondary = BeamColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEEDE5),
    onSecondaryContainer = Color(0xFFB4410F),

    tertiary = BeamColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = BeamColors.SuccessTint,
    onTertiaryContainer = BeamColors.Success,

    error = BeamColors.Danger,
    onError = Color.White,
    errorContainer = BeamColors.DangerTint,
    onErrorContainer = BeamColors.Danger,

    background = BeamColors.Background,
    onBackground = BeamColors.Ink,
    surface = BeamColors.Surface,
    onSurface = BeamColors.Ink,
    surfaceVariant = BeamColors.InputFill,
    onSurfaceVariant = BeamColors.TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = BeamColors.InputFill,
    outline = Color(0xFFD5D7DC),
    outlineVariant = BeamColors.Divider,
    scrim = Color(0xFF111318),
)

object BeamShapes {
    val Card = RoundedCornerShape(16.dp)
    val CardLarge = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Button = RoundedCornerShape(14.dp)
    val Input = RoundedCornerShape(12.dp)
    val Tile = RoundedCornerShape(12.dp)
    val TileSmall = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = BeamShapes.Input,
    medium = BeamShapes.Card,
    large = BeamShapes.CardLarge,
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BeamNetworksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
