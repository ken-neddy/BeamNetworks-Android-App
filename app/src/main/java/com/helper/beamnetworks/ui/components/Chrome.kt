package com.helper.beamnetworks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType

/**
 * 56dp bar: menu (or back on sub-screens) on the left, centred title, then the alerts bell and
 * the "BN" account avatar on the right. Both sides are 80dp wide so the title stays centred.
 */
@Composable
fun BeamTopBar(
    title: String,
    showBack: Boolean,
    showAlertDot: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onBell: () -> Unit,
    onAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BeamColors.Background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 80.dp, height = 44.dp), contentAlignment = Alignment.CenterStart) {
            CircleButton(
                icon = if (showBack) BeamIcons.ChevronLeft else BeamIcons.Menu,
                label = if (showBack) "Back" else "Menu",
                onClick = if (showBack) onBack else onMenu,
                iconSize = 18.dp,
            )
        }
        Text(
            title,
            style = BeamType.AppBarTitle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.size(width = 80.dp, height = 44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(BeamIcons.Bell, "Alerts", onBell, iconSize = 17.dp, dot = showAlertDot)
            Box(
                Modifier
                    .size(36.dp)
                    .pressable(CircleShape, BeamColors.Ink, Color(0xFF2A2D35), onClick = onAccount)
                    .semantics { contentDescription = "Account" },
                contentAlignment = Alignment.Center,
            ) {
                Text("BN", style = BeamType.MetaStrong.copy(color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            }
        }
    }
}

@Composable
fun CircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
    color: Color = BeamColors.Surface,
    dot: Boolean = false,
) {
    Box(
        Modifier
            .size(size)
            .pressable(CircleShape, color, BeamColors.PressedNeutral, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = BeamColors.Ink, modifier = Modifier.size(iconSize))
        if (dot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 8.dp)
                    .size(7.dp)
                    .background(Color.White, CircleShape)
                    .padding(1.5.dp)
                    .background(BeamColors.DangerSolid, CircleShape),
            )
        }
    }
}

enum class BottomTab { Home, Customers, Installs, Tickets }

/** White bar with four tabs and the centre create button. Labels hidden, per the handoff. */
@Composable
fun BeamBottomBar(selected: BottomTab?, onSelect: (BottomTab) -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BeamColors.Surface),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BeamColors.NavBorder))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 8.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(BeamIcons.LayoutGrid, "Home", selected == BottomTab.Home) { onSelect(BottomTab.Home) }
            NavIcon(BeamIcons.Users, "Customers", selected == BottomTab.Customers) { onSelect(BottomTab.Customers) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .dropShadow(shape, Shadow(radius = 16.dp, color = BeamColors.Accent.copy(alpha = 0.35f), offset = DpOffset(0.dp, 6.dp)))
                        .size(46.dp)
                        .pressable(shape, BeamColors.Accent, BeamColors.AccentPressed, onClick = onCreate)
                        .semantics { contentDescription = "Create" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BeamIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            NavIcon(BeamIcons.Wrench, "Installations", selected == BottomTab.Installs) { onSelect(BottomTab.Installs) }
            NavIcon(BeamIcons.Ticket, "Tickets", selected == BottomTab.Tickets) { onSelect(BottomTab.Tickets) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .pressable(BeamShapes.Button, Color.Transparent, BeamColors.Background, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) BeamColors.Ink else BeamColors.IconInactive, modifier = Modifier.size(22.dp))
    }
}

/** Full-width primary button (blue, 50–52dp, radius 14). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 52.dp, enabled: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressable(
                BeamShapes.Button,
                if (enabled) BeamColors.Primary else BeamColors.Primary.copy(alpha = 0.5f),
                BeamColors.PrimaryPressed,
                onClick = if (enabled) onClick else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BeamType.Button.copy(color = Color.White))
    }
}

/** White secondary button (46–48dp, radius 14) with coloured text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, textColor: Color = BeamColors.Ink, height: androidx.compose.ui.unit.Dp = 46.dp, bold: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressable(BeamShapes.Button, BeamColors.Surface, BeamColors.PressedNeutral, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BeamType.BodyStrong.copy(color = textColor, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 60.dp,
    icon: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(14.dp))
        }
        Text(title, style = BeamType.EmptyTitle, textAlign = TextAlign.Center)
        Text(message, style = BeamType.Small.copy(color = BeamColors.TextMuted), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (actionLabel != null && onAction != null) {
            TextLink(actionLabel, onAction, modifier = Modifier.padding(top = 10.dp), style = BeamType.BodyStrong)
        }
    }
}

/** Ink pill toast, 100dp above the bottom of its container. */
@Composable
fun BeamToast(message: String?, modifier: Modifier = Modifier, bottom: androidx.compose.ui.unit.Dp = 100.dp) {
    Box(modifier.fillMaxWidth().padding(bottom = bottom, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                message.orEmpty(),
                style = BeamType.SmallStrong.copy(color = Color.White),
                modifier = Modifier
                    .dropShadow(BeamShapes.Pill, Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.2f), offset = DpOffset(0.dp, 8.dp)))
                    .background(BeamColors.Ink, BeamShapes.Pill)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}

/** Row of two equal-width children with an 8dp gap. */
@Composable
fun TwoColumns(first: @Composable () -> Unit, second: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}
