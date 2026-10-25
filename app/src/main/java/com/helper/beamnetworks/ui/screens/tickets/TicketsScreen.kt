package com.helper.beamnetworks.ui.screens.tickets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Ticket
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.avatarSeed
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.relativeAge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TicketSegment { Open, Resolved }

data class TicketRow(val ticket: Ticket, val phone: String?, val seed: Int)

data class TicketsUi(
    val segment: TicketSegment = TicketSegment.Open,
    val openCount: Int = 0,
    val resolvedCount: Int = 0,
    val list: List<TicketRow> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val moduleDisabled: Boolean = false,
)

class TicketsViewModel(private val app: AppContainer) : ViewModel() {
    private val segment = MutableStateFlow(TicketSegment.Open)

    val ui = combine(app.tickets.tickets.state, app.customers.customers.state, segment) { state, customers, segment ->
        val open = state.items.filter { it.status.isOpen }
        val resolved = state.items.filterNot { it.status.isOpen }
        TicketsUi(
            segment = segment,
            openCount = open.size,
            resolvedCount = resolved.size,
            list = (if (segment == TicketSegment.Open) open else resolved).map { t ->
                val customer = customers.items.firstOrNull { t.identityId != null && it.identityId == t.identityId }
                    ?: customers.items.firstOrNull { it.name.equals(t.customerName, ignoreCase = true) }
                val phone = customer?.phone ?: t.description.lineSequence().firstOrNull { it.startsWith("Phone: ") }?.removePrefix("Phone: ")
                TicketRow(t, phone, customer?.id ?: avatarSeed(t.customerName))
            },
            loading = state.loading,
            loaded = state.loaded,
            error = state.error,
            moduleDisabled = state.moduleDisabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TicketsUi())

    init {
        app.tickets.tickets.ensureLoaded()
        app.customers.customers.ensureLoaded()
    }

    fun setSegment(value: TicketSegment) {
        segment.value = value
    }

    fun refresh() = app.tickets.tickets.refreshAsync()

    fun resolve(ticket: Ticket) {
        viewModelScope.launch {
            try {
                app.tickets.resolve(ticket)
                app.toasts.show("Ticket ${ticket.number} resolved")
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't resolve the ticket")
            }
        }
    }

    fun noPhone() = app.toasts.show("No phone number for this customer")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(segmentKey: String?, onRaise: () -> Unit, onOpen: (Int) -> Unit) {
    val vm = containerViewModel(key = "tickets") { app, _ -> TicketsViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(segmentKey) { if (segmentKey == "open") vm.setSegment(TicketSegment.Open) }

    PullToRefreshBox(isRefreshing = ui.loading && ui.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                PillTabs(
                    options = listOf(PillTabOption(TicketSegment.Open, "Open", ui.openCount), PillTabOption(TicketSegment.Resolved, "Resolved", ui.resolvedCount)),
                    selected = ui.segment,
                    onSelect = vm::setSegment,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                ui.moduleDisabled -> item { EmptyState("Support tickets are off", "Enable the support-tickets module in Beam to raise tickets.") }
                ui.error != null && ui.list.isEmpty() -> item { EmptyState("Couldn't load tickets", ui.error!!, actionLabel = "Try again", onAction = vm::refresh) }
                !ui.loaded -> item { Text("Loading tickets…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp)) }
                ui.list.isEmpty() -> item {
                    EmptyState(
                        "All caught up",
                        "No tickets here. Nice work.",
                        actionLabel = "+ Raise a ticket",
                        onAction = onRaise,
                        icon = {
                            Box(Modifier.size(72.dp).background(BeamColors.SuccessTint, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(BeamIcons.Check, null, tint = BeamColors.Success, modifier = Modifier.size(30.dp))
                            }
                        },
                    )
                }
            }
            items(ui.list, key = { it.ticket.id }) { row ->
                TicketCard(
                    row,
                    onOpen = { onOpen(row.ticket.id) },
                    onResolve = { vm.resolve(row.ticket) },
                    onCall = {
                        val phone = row.phone
                        if (phone == null) vm.noPhone() else context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
                    },
                )
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun TicketCard(row: TicketRow, onOpen: () -> Unit, onResolve: () -> Unit, onCall: () -> Unit) {
    val t = row.ticket
    val name = t.customerName.ifBlank { "Customer" }
    BeamCard(onClick = onOpen, padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InitialsAvatar(name, row.seed, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t.number} · ${relativeAge(t.created)}", style = BeamType.Tag.copy(color = BeamColors.TextMuted, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
            }
            if (t.status.isOpen) slaTag(t.slaStatus)?.let { (label, tone) -> Tag(label, tone, large = true) }
            priorityTone(t.priority).let { (label, tone) -> Tag(label, tone, large = true) }
        }
        Text(t.description.lineSequence().filterNot { it.startsWith("Customer: ") || it.startsWith("Phone: ") }.joinToString("\n").trim(), style = BeamType.Body.copy(lineHeight = 1.45.em))
        if (t.status.isOpen) {
            BeamDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Resolve", Tones.Green, Tones.Green.background.copy(alpha = 0.7f), onResolve)
                PillButton("Call customer", Tones.Neutral.copy(content = BeamColors.Ink), BeamColors.PressedNeutral, onCall, bold = false)
            }
        }
    }
}
