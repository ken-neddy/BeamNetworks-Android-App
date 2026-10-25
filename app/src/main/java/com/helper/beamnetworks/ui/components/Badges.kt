package com.helper.beamnetworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import kotlin.math.roundToInt

/** Small rounded tag: status, priority, plan... */
@Composable
fun Tag(text: String, tone: TonePair, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text = text,
        style = BeamType.Tag.copy(color = tone.content),
        maxLines = 1,
        modifier = modifier
            .clip(BeamShapes.Pill)
            .background(tone.background)
            .padding(horizontal = if (large) 10.dp else 9.dp, vertical = if (large) 4.dp else 3.dp),
    )
}

/** Small pill-shaped action button ("Mark done", "Resolve", "Call customer"). */
@Composable
fun PillButton(text: String, tone: TonePair, pressedColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier, bold: Boolean = true) {
    Text(
        text = text,
        style = BeamType.MetaStrong.copy(color = tone.content, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold),
        modifier = modifier
            .pressable(BeamShapes.Pill, tone.background, pressedColor, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

data class PillTabOption<K>(val key: K, val label: String, val count: Int? = null)

/** Segmented filter row: selected = ink fill + white text, unselected = transparent. */
@Composable
fun <K> PillTabs(
    options: List<PillTabOption<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    Row(
        modifier = modifier.then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.key == selected
            val fg = if (isSelected) Color.White else BeamColors.TextSecondary
            Text(
                text = buildAnnotatedString {
                    append(option.label)
                    if (option.count != null) {
                        append(" ")
                        withStyle(SpanStyle(color = fg.copy(alpha = 0.55f), fontFeatureSettings = "tnum")) { append(option.count.toString()) }
                    }
                },
                style = BeamType.PillTab.copy(color = fg),
                maxLines = 1,
                modifier = Modifier
                    .semantics { this.selected = isSelected }
                    .pressable(
                        shape = BeamShapes.Pill,
                        color = if (isSelected) BeamColors.Ink else Color.Transparent,
                        pressedColor = if (isSelected) BeamColors.Ink else BeamColors.PressedNeutral,
                        role = Role.Tab,
                        onClick = { onSelect(option.key) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/** Stable palette seed for names (Java's String.hashCode is stable across runs). */
fun avatarSeed(key: String): Int = key.hashCode()

/** Initials circle on a palette tint, with an optional presence dot. */
@Composable
fun InitialsAvatar(
    name: String,
    seed: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean? = null,
    ring: Color? = null,
) {
    val tone = Tones.avatar(seed)
    val fontSize = when {
        size >= 64.dp -> 20.sp
        size >= 42.dp -> 13.sp
        size >= 34.dp -> 11.sp
        size >= 26.dp -> 10.sp
        else -> 9.sp
    }
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                .background(tone.background, CircleShape)
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initialsOf(name),
                style = TextStyle(
                    fontFamily = BeamType.Tag.fontFamily,
                    fontSize = fontSize,
                    fontWeight = if (size >= 64.dp) FontWeight.ExtraBold else FontWeight.Bold,
                    color = tone.content,
                ),
            )
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .padding(2.dp)
                    .background(if (online) BeamColors.SuccessSolid else BeamColors.OfflineDot, CircleShape),
            )
        }
    }
}

/** Overlapping avatars (28dp, -8dp overlap, 2dp ring in [ringColor]). */
@Composable
fun AvatarStack(people: List<Pair<String, Int>>, ringColor: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Row(modifier) {
        people.forEachIndexed { index, (name, seed) ->
            InitialsAvatar(
                name = name,
                seed = seed,
                size = size,
                ring = ringColor,
                modifier = Modifier.offset(x = (-8).dp * index),
            )
        }
    }
}

/** Rounded progress track. [fraction] is clamped to 0..1. */
@Composable
fun Meter(fraction: Float, color: Color, trackColor: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(BeamShapes.Pill).background(trackColor)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(BeamShapes.Pill)
                .background(color),
        )
    }
}

/** −  qty  +  stepper with 32dp grey circles. */
@Composable
fun QtyStepper(value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepperButton(com.helper.beamnetworks.ui.icons.BeamIcons.Minus, "Decrease", onDecrement)
        Text(
            value.toString(),
            style = BeamType.RowTitleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum", textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            modifier = Modifier.padding(horizontal = 2.dp).size(width = 36.dp, height = 22.dp),
        )
        StepperButton(com.helper.beamnetworks.ui.icons.BeamIcons.Plus, "Increase", onIncrement)
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .pressable(CircleShape, BeamColors.InputFill, BeamColors.PressedNeutral, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = BeamColors.Ink, modifier = Modifier.size(16.dp))
    }
}

fun percentOf(part: Double, whole: Double): Int = if (whole <= 0) 0 else (part / whole * 100).roundToInt()
