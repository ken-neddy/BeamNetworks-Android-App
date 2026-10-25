package com.helper.beamnetworks.ui.screens.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.notifications.AppNotification
import com.helper.beamnetworks.data.notifications.NotificationCategory
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.IconTile
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.TextLink
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

fun NotificationCategory.icon() = when (this) {
    NotificationCategory.Routers -> BeamIcons.Activity
    NotificationCategory.Outages -> BeamIcons.Bell
    NotificationCategory.Downtime -> BeamIcons.PauseCircle
    NotificationCategory.Payments -> BeamIcons.Banknote
    NotificationCategory.Accounts -> BeamIcons.Users
    NotificationCategory.Tickets -> BeamIcons.Ticket
    NotificationCategory.Billing -> BeamIcons.Link
    NotificationCategory.Stock -> BeamIcons.Package
}

fun NotificationCategory.tone(): TonePair = when (this) {
    NotificationCategory.Routers, NotificationCategory.Outages -> Tones.Red
    NotificationCategory.Downtime -> Tones.Orange
    NotificationCategory.Payments -> Tones.Green
    NotificationCategory.Accounts -> Tones.Purple
    NotificationCategory.Tickets -> Tones.Blue
    NotificationCategory.Billing -> Tones.Teal
    NotificationCategory.Stock -> Tones.Blue
}

class NotificationsViewModel(private val app: AppContainer) : ViewModel() {
    val items = app.notificationFeed.items
    /** Snapshot when the screen opened, so new items stay highlighted while it's open. */
    val readBefore = app.notificationFeed.lastRead.value
    var refreshing by mutableStateOf(false)
        private set
    var busyAlert by mutableStateOf<Int?>(null)
        private set

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                app.notificationChecker.check()
            } finally {
                refreshing = false
            }
        }
    }

    fun markRead() = app.notificationFeed.markAllRead()

    fun alertAction(item: AppNotification, resolve: Boolean) {
        val id = item.alertId ?: return
        busyAlert = id
        viewModelScope.launch {
            try {
                apiCall { if (resolve) app.api.resolveAlert(id) else app.api.acknowledgeAlert(id) }
                app.notificationFeed.remove(item.id)
                app.toasts.show(if (resolve) "Alert resolved" else "Alert acknowledged")
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't update the alert")
            } finally {
                busyAlert = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onOpenLink: (String) -> Unit, onSettings: () -> Unit) {
    val vm = containerViewModel { app, _ -> NotificationsViewModel(app) }
    val items by vm.items.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.markRead() } }
    val today = LocalDate.now(zone)
    val (todayItems, earlier) = items.partition { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() == today }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Router, outage and customer events", style = BeamType.Meta, modifier = Modifier.weight(1f))
                    TextLink("Settings", onSettings)
                }
            }
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        "No notifications yet",
                        "Router and customer downtime, payments, tickets and Beam account alerts will show up here.",
                        actionLabel = "Check now",
                        onAction = vm::refresh,
                    )
                }
            }
            listOf("Today" to todayItems, "Earlier" to earlier).forEach { (label, group) ->
                if (group.isNotEmpty()) {
                    item(key = "h-$label") { SectionHeader(label, Modifier.padding(top = 0.dp)) }
                    items(group, key = { it.id }) { n ->
                        NotificationRow(
                            n,
                            unread = n.time > vm.readBefore,
                            busy = vm.busyAlert == n.alertId,
                            onClick = { n.link?.let(onOpenLink) },
                            onAcknowledge = { vm.alertAction(n, resolve = false) },
                            onResolve = { vm.alertAction(n, resolve = true) },
                        )
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, unread: Boolean, busy: Boolean, onClick: () -> Unit, onAcknowledge: () -> Unit, onResolve: () -> Unit) {
    BeamCard(onClick = onClick, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                IconTile(n.category.icon(), n.category.tone())
                if (unread) Box(Modifier.align(Alignment.TopEnd).size(10.dp).background(Color.White, CircleShape).padding(2.dp).background(BeamColors.DangerSolid, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(n.title, style = BeamType.RowTitle, modifier = Modifier.weight(1f))
                    Text(relativeAge(Instant.ofEpochMilli(n.time).atZone(zone)), style = BeamType.Meta, modifier = Modifier.padding(start = 8.dp))
                }
                if (n.body.isNotBlank()) {
                    Text(n.body, style = BeamType.Small.copy(color = BeamColors.TextSecondary, lineHeight = 1.4.em), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (n.alertId != null) {
            BeamDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (busy) "…" else "Acknowledge", Tones.Blue, BeamColors.PrimaryTintPressed, onAcknowledge)
                PillButton("Resolve", Tones.Green, Tones.Green.background.copy(alpha = 0.7f), onResolve)
            }
        }
    }
}

// ---- Settings ----

class NotificationSettingsViewModel(val app: AppContainer) : ViewModel() {
    val version = app.notificationSettings.version
    fun test() {
        if (app.notifier.canPost()) app.notifier.postTest() else app.toasts.show("Notifications are turned off for Beam Networks")
    }
}

@Composable
fun NotificationSettingsScreen() {
    val vm = containerViewModel { app, _ -> NotificationSettingsViewModel(app) }
    // Reading the version makes switches and pickers redraw when a setting changes
    vm.version.collectAsStateWithLifecycle().value
    val settings = vm.app.notificationSettings
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(vm.app.notifier.canPost()) }
    LifecycleResumeEffect(Unit) {
        allowed = vm.app.notifier.canPost()
        onPauseOrDispose { }
    }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        if (!allowed) {
            item {
                BeamCard(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }, modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(BeamIcons.Bell, Tones.Red)
                        Column(Modifier.weight(1f)) {
                            Text("Notifications are off", style = BeamType.RowTitle)
                            Text("Tap to allow Beam Networks to notify you.", style = BeamType.Meta)
                        }
                    }
                }
            }
        }
        item {
            ListCard {
                val categories = NotificationCategory.entries
                categories.forEachIndexed { i, category ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(category.icon(), category.tone(), size = 38.dp, iconSize = 17.dp)
                        Column(Modifier.weight(1f)) {
                            Text(category.title, style = BeamType.RowTitle)
                            Text(category.description, style = BeamType.Meta)
                        }
                        Switch(
                            checked = settings.isEnabled(category),
                            onCheckedChange = { settings.setEnabled(category, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = BeamColors.Primary, uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = BeamColors.PressedNeutral),
                        )
                    }
                    if (i < categories.lastIndex) BeamDivider()
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp).background(BeamColors.Surface, BeamShapes.CardLarge).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DropdownField(
                    "Tell me when a customer has been offline for",
                    minutesLabel(settings.downtimeMinutes),
                    listOf(15, 30, 60, 120, 240).map(::minutesLabel),
                    { label -> settings.downtimeMinutes = listOf(15, 30, 60, 120, 240).first { minutesLabel(it) == label } },
                )
                DropdownField(
                    "Treat it as an outage when this many drop at once",
                    "${settings.massOutageCustomers} customers",
                    listOf(3, 5, 10, 20).map { "$it customers" },
                    { label -> settings.massOutageCustomers = label.substringBefore(' ').toInt() },
                )
            }
        }
        item {
            Text(
                "Beam checks every 15 minutes in the background and every minute while the app is open. On Xiaomi phones, turn on Autostart and set Battery saver to \"No restrictions\" for Beam Networks, or background checks may be paused.",
                style = BeamType.Meta,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
            )
        }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton("Send test", vm::test, bold = true) }
                Box(Modifier.weight(1f)) {
                    SecondaryButton("Battery settings", {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    })
                }
            }
        }
        item { BottomSpacer() }
    }
}

private fun minutesLabel(minutes: Int) = if (minutes < 60) "$minutes minutes" else "${minutes / 60} hour${if (minutes >= 120) "s" else ""}"

