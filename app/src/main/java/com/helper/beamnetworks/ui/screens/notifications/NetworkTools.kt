package com.helper.beamnetworks.ui.screens.notifications

import android.graphics.drawable.GradientDrawable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.CustomerStatus
import com.helper.beamnetworks.data.api.AnnouncementCreate
import com.helper.beamnetworks.data.api.AnnouncementDto
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.CompensateRequest
import com.helper.beamnetworks.data.api.MaintenanceCreate
import com.helper.beamnetworks.data.api.MaintenanceDto
import com.helper.beamnetworks.data.api.OfflinePeriodDto
import com.helper.beamnetworks.data.api.SessionDto
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.api.fetchAllPages
import com.helper.beamnetworks.data.notifications.NotificationRules
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.DateField
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.InputBox
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SuggestField
import com.helper.beamnetworks.ui.components.SwitchRow
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TimeField
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.screens.customers.bytes
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.isoOffset
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

@Serializable data object SessionsRoute
@Serializable data object CustomerMapRoute
@Serializable data object AnnouncementsRoute

// ---- Maintenance and downtime compensation (tabs on Network status) ----

class OutagesViewModel(private val app: AppContainer) : ViewModel() {
    var maintenances by mutableStateOf<List<MaintenanceDto>?>(null)
        private set
    var downtime by mutableStateOf<List<OfflinePeriodDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var affected by mutableStateOf<Int?>(null)
        private set
    val networks = app.customers.networks.state
    val toasts = app.toasts
    private var previewJob: Job? = null

    init {
        app.customers.networks.ensureLoaded()
    }

    fun loadMaintenances() = load { maintenances = apiCall { app.api.maintenances() }.results }

    /** Downtime over 30 minutes in the last 7 days that hasn't been compensated or dismissed yet. */
    fun loadDowntime() = load {
        val from = ZonedDateTime.now(zone).minusDays(7).toOffsetDateTime().toString()
        downtime = apiCall { fetchAllPages(maxPages = 5) { app.api.downtimeSince(from, 30, page = it) } }
            .filter { it.customer != null && it.scheduledMaintenance == null && it.customerStatus !in setOf("suspended", "archived") }
            .sortedByDescending { it.durationSeconds }
    }

    private fun load(block: suspend () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                error = e.message
            }
        }
    }

    /** Debounced "≈ N customers affected" for the maintenance form. */
    fun preview(networkId: Int?, estate: String, ward: String, road: String) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(500)
            affected = runCatching {
                apiCall { app.api.previewAffected(networkId, estate.ifBlank { null }, ward.ifBlank { null }, road.ifBlank { null }) }.count
            }.getOrNull()
        }
    }

    fun schedule(body: MaintenanceCreate, onDone: () -> Unit) = act("create", "Maintenance scheduled") {
        val created = apiCall { app.api.createMaintenance(body) }
        maintenances = listOf(created) + maintenances.orEmpty()
        onDone()
    }

    fun start(m: MaintenanceDto) = act("m${m.id}", "Maintenance started") { replace(apiCall { app.api.startMaintenance(m.id) }) }
    fun complete(m: MaintenanceDto) = act("m${m.id}", "Maintenance completed") { replace(apiCall { app.api.completeMaintenance(m.id) }) }
    fun cancel(m: MaintenanceDto) = act("m${m.id}", "Maintenance cancelled") { replace(apiCall { app.api.cancelMaintenance(m.id) }) }

    private fun replace(m: MaintenanceDto) {
        maintenances = maintenances?.map { if (it.id == m.id) m else it }
    }

    /** "actual" gives each customer back exactly the time they were down. */
    fun compensate(periods: List<OfflinePeriodDto>, reason: String, manualHours: Int?, onDone: () -> Unit) = act("compensate", null) {
        val request = if (manualHours == null) {
            CompensateRequest(
                mode = "actual",
                reason = reason.trim(),
                durations = periods.groupBy { it.customer!! }.mapKeys { it.key.toString() }.mapValues { (_, list) -> list.sumOf { it.durationSeconds } },
            )
        } else {
            CompensateRequest(mode = "manual", reason = reason.trim(), customerIds = periods.mapNotNull { it.customer }.distinct(), durationSeconds = manualHours * 3600L)
        }
        val result = apiCall { app.api.compensate(request) }
        app.toasts.show(
            "${result.compensated} customer${if (result.compensated == 1) "" else "s"} compensated" +
                if (result.suspendedIds.isNotEmpty()) " · ${result.suspendedIds.size} skipped (suspended)" else "",
        )
        loadDowntime()
        app.customers.customers.refreshAsync()
        onDone()
    }

    private fun act(key: String, success: String?, block: suspend () -> Unit) {
        if (busy != null) return
        busy = key
        viewModelScope.launch {
            try {
                block()
                success?.let(app.toasts::show)
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Something went wrong")
            } finally {
                busy = null
            }
        }
    }
}

private fun maintenanceTone(status: String): Pair<String, TonePair> = when (status) {
    "in_progress" -> "In progress" to Tones.Orange
    "completed" -> "Completed" to Tones.Green
    "cancelled" -> "Cancelled" to Tones.Neutral
    else -> "Scheduled" to Tones.Blue
}

fun LazyListScope.maintenanceSection(vm: OutagesViewModel, onNew: () -> Unit) {
    item { PrimaryButton("Schedule maintenance", onNew, Modifier.padding(bottom = 12.dp), height = 48.dp) }
    val list = vm.maintenances
    when {
        vm.error != null && list == null -> item { EmptyState("Couldn't load maintenance", vm.error!!, topPadding = 24.dp) }
        list == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
        list.isEmpty() -> item { EmptyState("No maintenance planned", "Schedule work so affected customers are told in advance and downtime isn't counted against you.", topPadding = 24.dp) }
    }
    items(list.orEmpty(), key = { "m${it.id}" }) { m ->
        BeamCard(modifier = Modifier.padding(bottom = 8.dp), padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = BeamType.RowTitleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                maintenanceTone(m.status).let { (label, tone) -> Tag(label, tone, large = true) }
            }
            Text(
                listOfNotNull(
                    parseDateTime(m.scheduledStart)?.let(::whenLabel),
                    m.networkName?.ifBlank { null },
                    listOfNotNull(m.estate, m.road, m.ward).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
                    "${m.affectedCustomers} customers",
                ).joinToString(" · "),
                style = BeamType.Meta,
            )
            if (m.status == "scheduled" || m.status == "in_progress") {
                BeamDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.status == "scheduled") PillButton("Start now", Tones.Orange, Tones.Orange.background, { vm.start(m) })
                    if (m.status == "in_progress") PillButton("Complete", Tones.Green, Tones.Green.background, { vm.complete(m) })
                    PillButton("Cancel", Tones.Neutral.copy(content = BeamColors.Danger), BeamColors.PressedNeutral, { vm.cancel(m) }, bold = false)
                }
            }
        }
    }
}

fun LazyListScope.downtimeSection(vm: OutagesViewModel, selected: MutableList<Int>, onCompensate: () -> Unit) {
    val list = vm.downtime
    item {
        Text(
            "Customers who were offline 30+ minutes in the last week (not suspended or planned). Give them the time back as free service.",
            style = BeamType.Meta,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
    }
    when {
        vm.error != null && list == null -> item { EmptyState("Couldn't load downtime", vm.error!!, topPadding = 24.dp) }
        list == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
        list.isEmpty() -> item { EmptyState("Nothing to compensate", "No long outages in the last 7 days.", topPadding = 24.dp) }
        else -> item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("${selected.size} selected", style = BeamType.SmallStrong, modifier = Modifier.weight(1f))
                PillButton(if (selected.size == list.size) "Clear" else "Select all", Tones.Neutral.copy(content = BeamColors.Ink), BeamColors.PressedNeutral, {
                    if (selected.size == list.size) selected.clear() else {
                        selected.clear()
                        selected.addAll(list.map { it.id })
                    }
                }, bold = false)
                Box(Modifier.padding(start = 8.dp)) {
                    PillButton("Compensate", Tones.Blue, BeamColors.PrimaryTintPressed, { if (selected.isNotEmpty()) onCompensate() else vm.toasts.show("Select customers first") })
                }
            }
        }
    }
    item {
        if (!list.isNullOrEmpty()) ListCard {
            list.forEachIndexed { i, p ->
                if (i > 0) BeamDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = p.id in selected,
                        onCheckedChange = { if (it) selected.add(p.id) else selected.remove(p.id) },
                        colors = CheckboxDefaults.colors(checkedColor = BeamColors.Primary),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(p.customerName.ifBlank { "Customer" }, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(parseDateTime(p.startedAt)?.let(::whenLabel), p.networkName?.ifBlank { null }, if (p.endedAt == null) "still offline" else null).joinToString(" · "),
                            style = BeamType.Meta,
                            maxLines = 1,
                        )
                    }
                    Text(NotificationRules.duration(p.durationSeconds * 1000), style = BeamType.Amount.copy(color = BeamColors.Warning))
                }
            }
        }
    }
}

@Composable
fun MaintenanceSheet(vm: OutagesViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val networks by vm.networks.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var networkId by rememberSaveable { mutableStateOf<Int?>(null) }
    var estate by rememberSaveable { mutableStateOf("") }
    var road by rememberSaveable { mutableStateOf("") }
    var ward by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(zone).plusDays(1)) }
    var start by rememberSaveable { mutableStateOf(LocalTime.of(9, 0)) }
    var end by rememberSaveable { mutableStateOf(LocalTime.of(12, 0)) }
    var compensate by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(networkId, estate, road, ward) { vm.preview(networkId, estate, ward, road) }
    BeamSheet("Schedule maintenance", onClose, toast, ctaLabel = if (vm.busy == "create") "Scheduling…" else "Schedule", onCta = {
        if (title.isBlank()) vm.toasts.show("Add a title")
        else vm.schedule(
            MaintenanceCreate(
                title = title.trim(),
                network = networkId,
                estate = estate.trim().ifBlank { null },
                road = road.trim().ifBlank { null },
                ward = ward.trim().ifBlank { null },
                autoCompensate = compensate,
                scheduledStart = isoOffset(date, start.hour, start.minute),
                scheduledEnd = isoOffset(date, end.hour, end.minute),
            ),
            onClose,
        )
    }, ctaEnabled = vm.busy == null) {
        FormField("What's happening", title, { title = it }, placeholder = "e.g. Fibre splicing on Kamakis Rd")
        DropdownField(
            "Network",
            networks.items.firstOrNull { it.id == networkId }?.name ?: "All networks",
            listOf("All networks") + networks.items.map { it.name },
            { name -> networkId = networks.items.firstOrNull { it.name == name }?.id },
        )
        TwoColumns({ FormField("Estate", estate, { estate = it }) }, { FormField("Road", road, { road = it }) })
        FormField("Ward", ward, { ward = it })
        DateField("Date", date, { date = it })
        TwoColumns({ TimeField("Starts", start, { start = it }) }, { TimeField("Ends", end, { end = it }) })
        SwitchRow("Give affected customers the time back", compensate) { compensate = it }
        Text(vm.affected?.let { "≈ $it customers affected. They're notified automatically." } ?: "Counting affected customers…", style = BeamType.Meta.copy(color = BeamColors.Primary))
    }
}

@Composable
fun CompensateSheet(vm: OutagesViewModel, periods: List<OfflinePeriodDto>, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var reason by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("actual") }
    var hours by rememberSaveable { mutableStateOf("24") }
    val customers = periods.mapNotNull { it.customer }.distinct().size
    BeamSheet("Compensate $customers customer${if (customers == 1) "" else "s"}", onClose, toast, ctaLabel = if (vm.busy == "compensate") "Compensating…" else "Compensate", onCta = {
        when {
            reason.trim().length < 5 -> vm.toasts.show("Give a reason (at least 5 characters)")
            mode == "manual" && (hours.toIntOrNull() ?: 0) <= 0 -> vm.toasts.show("Enter the hours to give")
            else -> vm.compensate(periods, reason, if (mode == "manual") hours.toInt() else null, onClose)
        }
    }, ctaEnabled = vm.busy == null) {
        PillTabs(
            options = listOf(PillTabOption("actual", "Time they lost"), PillTabOption("manual", "Same for everyone")),
            selected = mode,
            onSelect = { mode = it },
        )
        if (mode == "actual") {
            Text("Each customer's service is extended by exactly how long they were offline (${NotificationRules.duration(periods.sumOf { it.durationSeconds } * 1000)} in total).", style = BeamType.Meta)
        } else {
            FormField("Hours each", hours, { hours = it.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number)
        }
        FormField("Reason", reason, { reason = it }, placeholder = "e.g. Fibre cut on 24 Oct")
        Text("Suspended customers are skipped: their downtime is from non-payment.", style = BeamType.Meta)
    }
}

// ---- Live sessions ----

class SessionsViewModel(private val app: AppContainer) : ViewModel() {
    var sessions by mutableStateOf<List<SessionDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf<Int?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        loading = true
        viewModelScope.launch {
            try {
                sessions = apiCall { app.api.sessions() }.sortedByDescending { it.durationSeconds }
                error = null
            } catch (e: ApiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun disconnect(s: SessionDto) {
        busy = s.id
        viewModelScope.launch {
            try {
                val result = apiCall { app.api.disconnectSession(s.id) }
                app.toasts.show(if (result.disconnectSent) "${s.customerName ?: "Session"} disconnected" else result.detail ?: "Sent, but the router didn't confirm")
                sessions = sessions?.filterNot { it.id == s.id }
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't disconnect")
            } finally {
                busy = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(onCustomer: (Int) -> Unit) {
    val vm = containerViewModel { app, _ -> SessionsViewModel(app) }
    var query by rememberSaveable { mutableStateOf("") }
    val list = vm.sessions.orEmpty().filter { query.isBlank() || it.customerName?.contains(query, true) == true || it.framedIp?.contains(query) == true }
    PullToRefreshBox(isRefreshing = vm.loading && vm.sessions != null, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                InputBox(query, { query = it }, placeholder = "Search name or IP", height = 46.dp, fill = BeamColors.Surface, shape = BeamShapes.Button, leadingIcon = BeamIcons.Search)
            }
            item { Text("${vm.sessions?.size ?: 0} active PPPoE sessions", style = BeamType.Meta, modifier = Modifier.padding(start = 2.dp)) }
            when {
                vm.error != null && vm.sessions == null -> item { EmptyState("Couldn't load sessions", vm.error!!) }
                vm.sessions == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
                list.isEmpty() -> item { EmptyState("No sessions", "Nobody matches.") }
            }
            items(list, key = { it.id }) { s ->
                BeamCard(onClick = { s.customer?.let(onCustomer) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).background(BeamColors.SuccessSolid, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(s.customerName ?: "Customer ${s.customer}", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(s.framedIp, "up " + NotificationRules.duration(s.durationSeconds * 1000), "↓ ${bytes(s.bytesIn)} ↑ ${bytes(s.bytesOut)}").joinToString(" · "),
                                style = BeamType.Meta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        PillButton(if (vm.busy == s.id) "…" else "Kick", Tones.Red, BeamColors.DangerTint, { vm.disconnect(s) })
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
}

// ---- Customer map ----

@Composable
fun CustomerMapScreen(onCustomer: (Int) -> Unit) {
    val context = LocalContext.current
    val app = remember { (context.applicationContext as com.helper.beamnetworks.BeamNetworksApplication).container }
    val state by app.customers.customers.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.customers.customers.ensureLoaded() }
    var filter by rememberSaveable { mutableStateOf("all") }
    val placed = state.items.filter { it.latitude != null && it.longitude != null && it.latitude != 0.0 }
    val shown = placed.filter {
        when (filter) {
            "online" -> it.online
            "offline" -> !it.online && it.status == CustomerStatus.Active
            "owing" -> it.owing
            else -> true
        }
    }
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(-1.2186, 36.9195)) // Nairobi east, until customers load
        }
    }
    DisposableEffect(Unit) { onDispose { mapView.onDetach() } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            PillTabs(
                options = listOf("all" to "All", "online" to "Online", "offline" to "Offline", "owing" to "Owing").map { (k, l) -> PillTabOption(k, l) },
                selected = filter,
                onSelect = { filter = it },
                scrollable = true,
            )
            Text(
                "${shown.size} on the map" + if (placed.size < state.items.size) " · ${state.items.size - placed.size} have no pin yet (add one on the web dashboard)" else "",
                style = BeamType.Meta,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            )
        }
        AndroidView(
            factory = { mapView },
            update = { map ->
                map.overlays.clear()
                shown.forEach { c -> map.overlays.add(marker(map, c, onCustomer)) }
                if (shown.size > 1) {
                    val box = BoundingBox.fromGeoPoints(shown.map { GeoPoint(it.latitude!!, it.longitude!!) })
                    map.post { runCatching { map.zoomToBoundingBox(box.increaseByScale(1.2f), false) } }
                } else if (shown.size == 1) {
                    map.controller.setCenter(GeoPoint(shown[0].latitude!!, shown[0].longitude!!))
                }
                map.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun marker(map: MapView, c: Customer, onCustomer: (Int) -> Unit) = Marker(map).apply {
    position = GeoPoint(c.latitude!!, c.longitude!!)
    title = c.name
    snippet = listOfNotNull(c.planName.ifBlank { null }, if (c.online) "Online" else "Offline", if (c.owing) "Owing" else null).joinToString(" · ")
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    val color = when {
        c.status == CustomerStatus.Suspended -> BeamColors.DangerSolid
        c.online -> BeamColors.SuccessSolid
        else -> Color(0xFF8A8F98)
    }
    icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color.toArgb())
        setStroke(4, android.graphics.Color.WHITE)
        setSize(36, 36)
    }
    setOnMarkerClickListener { m, _ ->
        if (m.isInfoWindowShown) onCustomer(c.id) else m.showInfoWindow()
        true
    }
}

// ---- Announcements ----

class AnnouncementsViewModel(private val app: AppContainer) : ViewModel() {
    var items by mutableStateOf<List<AnnouncementDto>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    val customers = app.customers.customers.state
    val toasts = app.toasts

    init {
        load()
        app.customers.customers.ensureLoaded()
    }

    fun load() {
        viewModelScope.launch {
            try {
                items = apiCall { app.api.announcements() }.results
                error = null
            } catch (e: ApiException) {
                error = e.message
            }
        }
    }

    fun send(subject: String, body: String, channels: List<String>, customer: Customer?, onDone: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val sent = apiCall {
                    app.api.sendAnnouncement(AnnouncementCreate(subject.trim(), body.trim(), channels, if (customer != null) "one" else "all", customer?.identityId))
                }
                items = listOf(sent) + items.orEmpty()
                app.toasts.show(if (customer != null) "Message sent to ${customer.name}" else "Announcement is going out to ${sent.recipientCount.takeIf { it > 0 } ?: "all"} customers")
                onDone()
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't send")
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun AnnouncementsScreen() {
    val vm = containerViewModel { app, _ -> AnnouncementsViewModel(app) }
    var composing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item { PrimaryButton("New announcement", { composing = true }, height = 48.dp) }
        item { Text("SMS, email and portal messages to all customers or to one.", style = BeamType.Meta, modifier = Modifier.padding(start = 2.dp)) }
        val list = vm.items
        when {
            vm.error != null && list == null -> item { EmptyState("Couldn't load announcements", vm.error!!) }
            list == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
            list.isEmpty() -> item { EmptyState("No announcements yet", "Tell customers about outages, new plans or holiday hours.") }
        }
        items(list.orEmpty(), key = { it.id }) { a ->
            BeamCard(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.subject, style = BeamType.RowTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Tag(
                        when (a.status) { "completed" -> "Sent"; "failed" -> "Failed"; else -> "Sending" },
                        when (a.status) { "completed" -> Tones.Green; "failed" -> Tones.Red; else -> Tones.Orange },
                    )
                }
                Text(a.body, style = BeamType.Small.copy(color = BeamColors.TextSecondary), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        if (a.target == "one") a.customerName ?: "One customer" else "All customers",
                        "${a.recipientCount} recipients",
                        relativeAge(parseDateTime(a.createdAt)),
                    ).joinToString(" · "),
                    style = BeamType.Meta,
                )
            }
        }
        item { BottomSpacer() }
    }
    if (composing) AnnouncementSheet(vm) { composing = false }
}

@Composable
fun AnnouncementSheet(vm: AnnouncementsViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    var subject by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var everyone by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var customerId by rememberSaveable { mutableStateOf<Int?>(null) }
    val channels = remember { mutableStateListOf("sms", "portal") }
    val customer = customers.items.firstOrNull { it.id == customerId && it.name == query }
    BeamSheet("New announcement", onClose, toast, ctaLabel = if (vm.busy) "Sending…" else "Send", onCta = {
        when {
            subject.isBlank() || body.isBlank() -> vm.toasts.show("Add a subject and message")
            channels.isEmpty() -> vm.toasts.show("Pick at least one channel")
            !everyone && customer == null -> vm.toasts.show("Choose a customer")
            !everyone && customer?.identityId == null -> vm.toasts.show("This customer has no portal identity yet; try again shortly")
            else -> vm.send(subject, body, channels.toList(), if (everyone) null else customer, onClose)
        }
    }, ctaEnabled = !vm.busy) {
        PillTabs(options = listOf(PillTabOption(true, "All customers"), PillTabOption(false, "One customer")), selected = everyone, onSelect = { everyone = it })
        if (!everyone) {
            SuggestField(
                label = "Customer",
                value = query,
                onValueChange = { query = it; customerId = null },
                suggestions = customers.items,
                suggestionText = { it.name },
                onPick = { c: Customer -> query = c.name; customerId = c.id },
                placeholder = "Search name",
            )
        }
        FormField("Subject", subject, { subject = it }, placeholder = "e.g. Planned maintenance on Saturday")
        FormField("Message", body, { body = it }, singleLine = false, placeholder = "Keep SMS short: 160 characters is one message")
        Text("${body.length} characters" + if (body.length > 160) " · ${(body.length + 152) / 153} SMS each" else "", style = BeamType.Meta)
        Text("Send by", style = BeamType.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("sms" to "SMS", "email" to "Email", "portal" to "Portal").forEach { (key, label) ->
                val on = key in channels
                PillButton(
                    (if (on) "✓ " else "") + label,
                    if (on) TonePair(BeamColors.Ink, Color.White) else Tones.Neutral,
                    if (on) BeamColors.Ink else BeamColors.PressedNeutral,
                    { if (on) channels.remove(key) else channels.add(key) },
                    bold = false,
                )
            }
        }
    }
}
