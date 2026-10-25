package com.helper.beamnetworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair

/** One row in the side menu. [badge] is a short tag such as "3 low". */
data class MenuItem(
    val icon: ImageVector,
    val tone: TonePair,
    val title: String,
    val selected: Boolean = false,
    val badge: String? = null,
    val badgeTone: TonePair? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

data class MenuSection(val title: String?, val items: List<MenuItem>)

/** Side menu opened from the top bar's hamburger: account header, then grouped feature rows. */
@Composable
fun MenuDrawer(tenant: String, subtitle: String, sections: List<MenuSection>) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = BeamColors.Background,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).background(BeamColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text("BN", style = BeamType.BodyStrong.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
                Column(Modifier.weight(1f)) {
                    Text(tenant, style = BeamType.RowTitleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                modifier = Modifier.weight(1f).navigationBarsPadding(),
            ) {
                sections.forEach { section ->
                    section.title?.let { title ->
                        item(key = "h-$title") {
                            Text(
                                title.uppercase(),
                                style = BeamType.Tag.copy(color = BeamColors.TextMuted, letterSpacing = androidx.compose.ui.unit.TextUnit(0.06f, androidx.compose.ui.unit.TextUnitType.Em)),
                                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                            )
                        }
                    }
                    section.items.forEach { entry ->
                        item(key = "${section.title}-${entry.title}") { MenuRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .pressable(BeamShapes.Button, if (entry.selected) BeamColors.Surface else Color.Transparent, BeamColors.PressedNeutral, onClick = entry.onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(entry.icon, entry.tone, size = 34.dp, iconSize = 16.dp)
        Text(
            entry.title,
            style = BeamType.RowTitle.copy(color = if (entry.danger) BeamColors.Danger else BeamColors.Ink),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.badge != null) Tag(entry.badge, entry.badgeTone ?: com.helper.beamnetworks.ui.theme.Tones.Neutral)
    }
}
