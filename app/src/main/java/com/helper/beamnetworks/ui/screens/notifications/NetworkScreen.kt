package com.helper.beamnetworks.ui.screens.notifications

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.helper.beamnetworks.ui.components.NavRow
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.icons.BeamIcons
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.DeviceDto
import com.helper.beamnetworks.data.api.OfflinePeriodDto
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.api.fetchAllPages
import com.helper.beamnetworks.data.notifications.NotificationRules
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.pressable
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.lastOnlineLabel
import com.helper.beamnetworks.util.parseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Routers and customers that are offline right now (devices + open offline periods). */
class NetworkViewModel(private val app: AppContainer) : ViewModel() {
    var devices by mutableStateOf<List<DeviceDto>>(emptyList())
        private set
    var offline by mutableStateOf<List<OfflinePeriodDto>>(emptyList())
        private set
    var devicesError by mutableStateOf<String?>(null)
        private set
    var offlineError by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            val d = async { runCatching { apiCall { app.api.devices() } } }
            val o = async { runCatching { apiCall { fetchAllPages(maxPages = 5) { app.api.offlinePeriods(it, status = "open") } } } }
            d.await().onSuccess { devices = it.sortedWith(compareBy({ it.status == "online" }, { it.name })); devicesError = null }
                .onFailure { devicesError = (it as? ApiException)?.message ?: it.message }
            o.await().onSuccess { list -> offline = list.filter { it.customerStatus !in setOf("suspended", "archived", "paused") }.sortedBy { it.startedAt }; offlineError = null }
                .onFailure { offlineError = (it as? ApiException)?.message ?: it.message }
            loading = false
            loaded = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onCustomer: (Int) -> Unit, onSessions: () -> Unit, onMap: () -> Unit) {
    val vm = containerViewModel { app, _ -> NetworkViewModel(app) }
    val outages = containerViewModel(key = "outages") { app, _ -> OutagesViewModel(app) }
    var tab by rememberSaveable { mutableStateOf("status") }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<Int>() }
    LaunchedEffect(tab) {
        if (tab == "maintenance") outages.loadMaintenances()
        if (tab == "downtime") outages.loadDowntime()
    }
    val now = System.currentTimeMillis()
    PullToRefreshBox(isRefreshing = vm.loading && vm.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
            item {
                PillTabs(
                    options = listOf(PillTabOption("status", "Status"), PillTabOption("maintenance", "Maintenance"), PillTabOption("downtime", "Compensate")),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (tab == "maintenance") maintenanceSection(outages) { sheet = "maintenance" }
            if (tab == "downtime") downtimeSection(outages, selected) { sheet = "compensate" }
            if (tab == "status") item {
                TwoColumns(
                    { Stat("${vm.devices.count { it.status == "online" }}/${vm.devices.size}", "Routers online", if (vm.devices.any { it.status == "offline" }) BeamColors.Danger else BeamColors.Ink) },
                    { Stat(vm.offline.size.toString(), "Customers offline", if (vm.offline.isNotEmpty()) BeamColors.Warning else BeamColors.Ink) },
                )
            }
            if (tab == "status") item {
                ListCard(Modifier.padding(top = 12.dp)) {
                    NavRow(BeamIcons.Activity, Tones.Green, "Live sessions", "Who is connected now; disconnect a session", onSessions, tileSize = 38.dp, inCard = true)
                    BeamDivider()
                    NavRow(BeamIcons.MapPin, Tones.Blue, "Customer map", "Online, offline and owing customers on a map", onMap, tileSize = 38.dp, inCard = true)
                }
            }
            if (tab == "status") item { SectionHeader("Routers") }
            if (tab == "status") item {
                ListCard {
                    when {
                        vm.devicesError != null -> Note(vm.devicesError!!)
                        !vm.loaded -> Note("Loading routers…")
                        vm.devices.isEmpty() -> Note("No routers added yet.")
                    }
                    vm.devices.forEachIndexed { i, d ->
                        if (i > 0) BeamDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val online = d.status == "online"
                            Box(Modifier.size(10.dp).background(if (online) BeamColors.SuccessSolid else if (d.status == "offline") BeamColors.DangerSolid else BeamColors.OfflineDot, CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(d.networkName?.ifBlank { null }, if (online) null else "Last seen " + lastOnlineLabel(false, parseDateTime(d.lastSeenAt)).lowercase()).joinToString(" · "),
                                    style = BeamType.Meta,
                                )
                            }
                            Tag(
                                d.status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                when (d.status) {
                                    "online" -> Tones.Green
                                    "offline" -> Tones.Red
                                    else -> Tones.Neutral
                                },
                            )
                        }
                    }
                }
            }
            if (tab == "status") item { SectionHeader("Customers offline now") { Text("Longest first", style = BeamType.Meta) } }
            if (tab == "status") item {
                ListCard {
                    when {
                        vm.offlineError != null -> Note(vm.offlineError!!)
                        !vm.loaded -> Note("Loading…")
                        vm.offline.isEmpty() -> Note("Everyone is online.")
                    }
                    vm.offline.forEachIndexed { i, p ->
                        if (i > 0) BeamDivider()
                        val started = parseDateTime(p.startedAt)?.toInstant()?.toEpochMilli() ?: now
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (p.customer != null) Modifier.pressable(BeamShapes.Input, androidx.compose.ui.graphics.Color.Transparent, BeamColors.PressedCard, onClick = { onCustomer(p.customer) }) else Modifier)
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.customerName.ifBlank { "Customer" }, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(p.networkName?.ifBlank { null }, p.ward?.ifBlank { null }, if (p.scheduledMaintenance != null) "Planned maintenance" else null).joinToString(" · "),
                                    style = BeamType.Meta,
                                    maxLines = 1,
                                )
                            }
                            Text(NotificationRules.duration(now - started), style = BeamType.Amount.copy(color = BeamColors.Warning))
                        }
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
    when (sheet) {
        "maintenance" -> MaintenanceSheet(outages) { sheet = null }
        "compensate" -> CompensateSheet(outages, outages.downtime.orEmpty().filter { it.id in selected }) {
            sheet = null
            selected.clear()
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    BeamCard(padding = PaddingValues(14.dp)) {
        Text(value, style = BeamType.StatLarge.copy(color = color))
        Text(label, style = BeamType.Meta)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
}
