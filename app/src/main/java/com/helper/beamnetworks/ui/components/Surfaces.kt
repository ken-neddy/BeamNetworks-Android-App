package com.helper.beamnetworks.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair

/**
 * Clips to [shape], fills with [color], and switches to [pressedColor] while pressed, as the
 * handoff's pressed states do. A ripple is drawn on top. Not clickable when [onClick] is null.
 */
@Composable
fun Modifier.pressable(
    shape: Shape,
    color: Color,
    pressedColor: Color,
    role: Role = Role.Button,
    onClick: (() -> Unit)?,
): Modifier {
    if (onClick == null) return this.clip(shape).background(color)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed) pressedColor else color, label = "pressed")
    return this
        .clip(shape)
        .background(fill)
        .clickable(interactionSource = interaction, indication = ripple(), role = role, onClick = onClick)
}

/** Flat white card on the grey background. */
@Composable
fun BeamCard(
    modifier: Modifier = Modifier,
    shape: Shape = BeamShapes.Card,
    padding: PaddingValues = PaddingValues(12.dp),
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(shape, BeamColors.Surface, BeamColors.PressedCard, onClick = onClick)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** Large white card that holds a list of rows separated by dividers (radius 20, padding 4x16). */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BeamShapes.CardLarge)
            .background(BeamColors.Surface)
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun BeamDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(BeamColors.Divider))
}

/** Rounded square holding an icon in a tinted colour. */
@Composable
fun IconTile(icon: ImageVector, tone: TonePair, size: Dp = 40.dp, iconSize: Dp = 18.dp, shape: Shape = BeamShapes.Tile) {
    Box(
        modifier = Modifier.size(size).clip(shape).background(tone.background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(iconSize))
    }
}

/** "Section title ........ trailing" header with the handoff's 20dp top / 10dp bottom margins. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = BeamType.SectionTitle)
        trailing()
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = BeamType.MetaStrong) {
    Text(
        text = text,
        style = style.copy(color = BeamColors.Primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        modifier = modifier
            .clip(BeamShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** White row with a tinted icon tile, title, subtitle and chevron (Needs attention, menus). */
@Composable
fun NavRow(
    icon: ImageVector,
    tone: TonePair,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileSize: Dp = 40.dp,
    inCard: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {
        Icon(BeamIcons.ChevronRight, contentDescription = null, tint = BeamColors.TextMuted, modifier = Modifier.size(16.dp))
    },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (inCard) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier.pressable(BeamShapes.Card, BeamColors.Surface, BeamColors.PressedCard, onClick = onClick)
            )
            .padding(if (inCard) PaddingValues(vertical = 12.dp) else PaddingValues(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, tone, size = tileSize, iconSize = if (tileSize < 40.dp) 17.dp else 18.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** Label / value row used in info cards. */
@Composable
fun InfoRow(label: String, value: String, showDivider: Boolean = true, onClick: (() -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, style = BeamType.Small.copy(color = BeamColors.TextMuted))
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = BeamType.SmallStrong.copy(color = if (onClick != null) BeamColors.Primary else BeamColors.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) BeamDivider()
    }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(24.dp))
