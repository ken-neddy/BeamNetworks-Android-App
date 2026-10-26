package com.helper.beamnetworks.ui.screens.installs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Job
import com.helper.beamnetworks.data.JobStatus
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.avatarSeed
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.InstallDetailRoute
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

enum class JobSegment { Upcoming, Completed }

fun Job.tag(today: LocalDate = LocalDate.now(zone)): Pair<String, TonePair> = when {
    status == JobStatus.Completed -> "Completed" to Tones.Green
    status == JobStatus.Cancelled -> "Cancelled" to Tones.Neutral
    status == JobStatus.InProgress -> "On site" to Tones.Purple
    scheduled?.withZoneSameInstant(zone)?.toLocalDate() == today -> "Today" to Tones.Orange
    scheduled != null && scheduled.withZoneSameInstant(zone).toLocalDate() < today -> "Overdue" to Tones.Red
    else -> "Scheduled" to Tones.Blue
}

data class JobsUi(
    val segment: JobSegment = JobSegment.Upcoming,
    val type: JobType? = null,
    val mine: Boolean = false,
    val upcomingCount: Int = 0,
    val completedCount: Int = 0,
    val list: List<Job> = emptyList(),
    val typesInUse: List<JobType> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val moduleDisabled: Boolean = false,
)

class InstallsViewModel(private val app: AppContainer) : ViewModel() {
    private val segment = MutableStateFlow(JobSegment.Upcoming)
    private val type = MutableStateFlow<JobType?>(null)
    private val mine = MutableStateFlow(false)

    val ui = combine(app.jobs.jobs.state, segment, type, mine, app.sessions.session) { state, segment, type, mine, session ->
        val filtered = state.items
            .filter { type == null || it.type == type }
            .filter { !mine || it.technicianId == session?.userId }
        val upcoming = filtered.filter { it.status.isUpcoming }.sortedBy { it.scheduled }
        // Cancelled jobs sit with completed ones (their tag says Cancelled) so they never vanish
        val completed = filtered.filter { it.status == JobStatus.Completed || it.status == JobStatus.Cancelled }.sortedByDescending { it.completedAt ?: it.scheduled }
        JobsUi(
            segment = segment,
            type = type,
            mine = mine,
            upcomingCount = upcoming.size,
            completedCount = completed.size,
            list = if (segment == JobSegment.Upcoming) upcoming else completed,
            typesInUse = state.items.map { it.type }.distinct().sortedBy { it.ordinal },
            loading = state.loading,
            loaded = state.loaded,
            error = state.error,
            moduleDisabled = state.moduleDisabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobsUi())

    init {
        app.jobs.jobs.ensureLoaded()
    }

    fun setSegment(value: JobSegment) {
        segment.value = value
    }

    fun setType(value: JobType?) {
        type.value = value
    }

    fun setMine(value: Boolean) {
        mine.value = value
    }

    fun refresh() = app.jobs.jobs.refreshAsync()
}

private sealed interface TypeFilter {
    data object All : TypeFilter
    data object Mine : TypeFilter
    data class Of(val type: JobType) : TypeFilter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallsScreen(segmentKey: String?, onOpen: (Int) -> Unit, onMarkDone: (Int) -> Unit, onSchedule: () -> Unit) {
    val vm = containerViewModel(key = "installs") { app, _ -> InstallsViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(segmentKey) { if (segmentKey == "upcoming") vm.setSegment(JobSegment.Upcoming) }

    PullToRefreshBox(isRefreshing = ui.loading && ui.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                PillTabs(
                    options = listOf(PillTabOption(JobSegment.Upcoming, "Upcoming", ui.upcomingCount), PillTabOption(JobSegment.Completed, "Completed", ui.completedCount)),
                    selected = ui.segment,
                    onSelect = vm::setSegment,
                )
            }
            if (ui.typesInUse.size > 1 || ui.mine) {
                item {
                    val selected: TypeFilter = when {
                        ui.mine -> TypeFilter.Mine
                        ui.type != null -> TypeFilter.Of(ui.type!!)
                        else -> TypeFilter.All
                    }
                    PillTabs(
                        options = listOf<PillTabOption<TypeFilter>>(PillTabOption(TypeFilter.All, "All types"), PillTabOption(TypeFilter.Mine, "Mine")) +
                            ui.typesInUse.map { PillTabOption<TypeFilter>(TypeFilter.Of(it), it.label) },
                        selected = selected,
                        onSelect = { f ->
                            vm.setMine(f == TypeFilter.Mine)
                            vm.setType((f as? TypeFilter.Of)?.type)
                        },
                        scrollable = true,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            when {
                ui.moduleDisabled -> item { EmptyState("Field jobs are off", "Enable the field-jobs module in Beam to schedule jobs.") }
                ui.error != null && ui.list.isEmpty() -> item { EmptyState("Couldn't load jobs", ui.error!!, actionLabel = "Try again", onAction = vm::refresh) }
                !ui.loaded -> item { Text("Loading jobs…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp)) }
                ui.list.isEmpty() -> item {
                    EmptyState(
                        if (ui.segment == JobSegment.Upcoming) "No jobs scheduled" else "Nothing completed yet",
                        if (ui.mine) "Nothing is assigned to you here." else "Schedule an installation, repair or site visit.",
                        actionLabel = "+ Schedule a job",
                        onAction = onSchedule,
                    )
                }
            }
            items(ui.list, key = { it.id }) { job -> JobCard(job, { onOpen(job.id) }, { onMarkDone(job.id) }) }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun JobCard(job: Job, onClick: () -> Unit, onMarkDone: () -> Unit) {
    val (tag, tone) = job.tag()
    BeamCard(onClick = onClick, padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WhenPill(whenLabel(job.scheduled))
            Spacer(Modifier.weight(1f))
            if (job.priority == "high" || job.priority == "urgent") Tag(if (job.priority == "urgent") "Urgent" else "High", Tones.Red, large = true)
            Tag(tag, tone, large = true)
        }
        Column {
            Text(job.customerName, style = BeamType.RowTitleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(job.type.label, job.location.ifBlank { null }).joinToString(" · "),
                style = BeamType.Meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        BeamDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tech = job.technicianName ?: "Unassigned"
            InitialsAvatar(tech, avatarSeed(tech), 24.dp)
            Text(tech, style = BeamType.Meta.copy(color = BeamColors.TextSecondary), modifier = Modifier.weight(1f))
            if (job.status.isUpcoming) PillButton("Mark done", Tones.Blue, BeamColors.PrimaryTintPressed, onMarkDone)
        }
    }
}

@Composable
private fun WhenPill(label: String) {
    Row(
        Modifier.background(BeamColors.InputFill, BeamShapes.Pill).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(BeamIcons.Calendar, null, tint = BeamColors.TextSecondary, modifier = Modifier.size(12.dp))
        Text(label, style = BeamType.Tag.copy(color = BeamColors.TextSecondary))
    }
}

class InstallDetailViewModel(private val app: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<InstallDetailRoute>().installationId
    val job = app.jobs.jobs.state.map { state -> state.items.firstOrNull { it.id == id } to state.loaded }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to false)
    val isAdmin get() = app.sessions.current?.isAdmin == true
    var busy by mutableStateOf<String?>(null)
        private set

    init {
        app.jobs.jobs.ensureLoaded()
    }

    /** Checks in on site; the location is attached when permission was given and a fix arrives quickly. */
    fun checkIn(context: Context) = act("checkin") {
        val location = currentLocation(context)
        val job = app.jobs.byId(id) ?: return@act
        app.jobs.checkIn(job, location?.first, location?.second)
        app.toasts.show(if (location != null) "Checked in on site" else "Checked in (no GPS fix)")
    }

    fun cancel() = act("cancel") {
        val job = app.jobs.byId(id) ?: return@act
        app.jobs.cancel(job)
        app.toasts.show("Job cancelled")
    }

    private fun act(key: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = key
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Something went wrong")
            } finally {
                busy = null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return withTimeoutOrNull(10_000) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?.let { it.latitude to it.longitude }
            }.getOrNull()
        }
    }
}

/** Opened from a job card or the reminder notification's deep link. */
@Composable
fun InstallDetailScreen(onEdit: (Int) -> Unit, onMarkDone: (Int) -> Unit) {
    val vm = containerViewModel { app, handle -> InstallDetailViewModel(app, handle) }
    val (job, loaded) = vm.job.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.checkIn(context) }
    if (job == null) {
        EmptyState(if (loaded) "Job not found" else "Loading…", if (loaded) "It may have been removed." else "")
        return
    }
    val (tag, tone) = job.tag()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        item {
            BeamCard(shape = BeamShapes.CardLarge, padding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WhenPill(whenLabel(job.scheduled))
                    Spacer(Modifier.weight(1f))
                    Tag(tag, tone, large = true)
                }
                Text(job.customerName, style = BeamType.DetailName)
                Text(listOfNotNull(job.type.label, job.location.ifBlank { null }).joinToString(" · "), style = BeamType.Small.copy(color = BeamColors.TextSecondary))
            }
        }
        if (job.status == JobStatus.Open || job.status == JobStatus.Scheduled) {
            item {
                PrimaryButton(
                    if (vm.busy == "checkin") "Checking in…" else "Check in on site",
                    {
                        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocation) vm.checkIn(context)
                        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    Modifier.padding(top = 12.dp),
                    height = 50.dp,
                    enabled = vm.busy == null,
                )
            }
            item { SecondaryButton("Mark done", { onMarkDone(job.id) }, Modifier.padding(top = 8.dp), bold = true) }
        } else if (job.status == JobStatus.InProgress) {
            item { PrimaryButton("Mark done", { onMarkDone(job.id) }, Modifier.padding(top = 12.dp), height = 50.dp) }
        }
        item {
            TwoColumns(
                {
                    SecondaryButton("Call", {
                        job.phone?.let { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$it".toUri())) }
                    }, textColor = if (job.phone == null) BeamColors.TextMuted else BeamColors.Ink)
                },
                {
                    SecondaryButton("Directions", {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(job.location)}".toUri()))
                    }, textColor = if (job.location.isBlank()) BeamColors.TextMuted else BeamColors.Ink)
                },
                Modifier.padding(top = 8.dp),
            )
        }
        item {
            ListCard(Modifier.padding(top = 12.dp)) {
                val rows = listOfNotNull(
                    "Type" to job.type.label,
                    "Phone" to (job.phone ?: "—"),
                    "Technician" to (job.technicianName ?: "Unassigned"),
                    job.checkedInAt?.let { "Checked in" to whenLabel(it) },
                    job.sourceTicket?.let { "From ticket" to it },
                    if (job.type == JobType.Installation) "Has a router" to (if (job.hasRouter) "Yes" else "No") else null,
                    if (job.type == JobType.Installation) "Has PoE" to (if (job.hasPoe) "Yes" else "No") else null,
                    job.notes.takeIf { it.isNotBlank() }?.let { "Notes" to it },
                )
                rows.forEachIndexed { i, (label, value) -> InfoRow(label, value, showDivider = i < rows.lastIndex) }
            }
        }
        if (job.status.isUpcoming) {
            item { SecondaryButton("Edit job", { onEdit(job.id) }, Modifier.padding(top = 12.dp), height = 48.dp, bold = true) }
            if (vm.isAdmin) {
                item { SecondaryButton(if (vm.busy == "cancel") "Cancelling…" else "Cancel job", vm::cancel, Modifier.padding(top = 8.dp), textColor = BeamColors.Danger, height = 48.dp) }
            }
        }
        item { BottomSpacer() }
    }
}
