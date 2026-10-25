package com.helper.beamnetworks.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.CustomerStatus
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.InputBox
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.normalizePhone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CustomerFilter(val key: String, val label: String, val test: (Customer) -> Boolean) {
    Online("online", "Online", { it.online }),
    All("all", "All", { true }),
    Active("active", "Active", { it.status == CustomerStatus.Active }),
    Suspended("suspended", "Suspended", { it.status == CustomerStatus.Suspended }),
    Owing("owing", "Owing", { it.owing });

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Online
    }
}

fun CustomerStatus.tone(): TonePair = when (this) {
    CustomerStatus.Active -> Tones.Green
    CustomerStatus.Suspended -> Tones.Red
    CustomerStatus.Overdue -> Tones.Orange
    CustomerStatus.Paused, CustomerStatus.Archived -> Tones.Neutral
}

data class CustomersUi(
    val filter: CustomerFilter = CustomerFilter.Online,
    val query: String = "",
    val counts: Map<CustomerFilter, Int> = emptyMap(),
    val list: List<Customer> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

class CustomersViewModel(private val app: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val filter = MutableStateFlow(CustomerFilter.from(handle.get<String>("filter")))
    private val query = MutableStateFlow("")

    val ui = combine(app.customers.customers.state, filter, query) { state, filter, query ->
        val q = query.trim()
        val digits = normalizePhone(q)
        CustomersUi(
            filter = filter,
            query = query,
            counts = CustomerFilter.entries.associateWith { f -> state.items.count(f.test) },
            list = state.items.filter(filter.test).filter { c ->
                q.isEmpty() || c.name.contains(q, true) || (c.pppoeUser?.contains(q, true) == true) ||
                    (digits.length >= 3 && normalizePhone(c.phone).contains(digits))
            },
            loading = state.loading,
            loaded = state.loaded,
            error = state.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersUi(filter = filter.value))

    init {
        app.customers.customers.ensureLoaded()
    }

    fun setFilter(value: CustomerFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun refresh() = app.customers.customers.refreshAsync()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(filterKey: String?, onOpen: (Int) -> Unit) {
    val vm = containerViewModel(key = "customers") { app, handle -> CustomersViewModel(app, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // A new filter from the dashboard replaces the current one
    LaunchedEffect(filterKey) { if (filterKey != null) vm.setFilter(CustomerFilter.from(filterKey)) }

    PullToRefreshBox(isRefreshing = ui.loading && ui.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                InputBox(
                    value = ui.query,
                    onValueChange = vm::setQuery,
                    placeholder = "Search name, phone or username",
                    height = 46.dp,
                    fill = BeamColors.Surface,
                    shape = BeamShapes.Button,
                    leadingIcon = BeamIcons.Search,
                )
            }
            item {
                PillTabs(
                    options = CustomerFilter.entries.map { PillTabOption(it, it.label, ui.counts[it] ?: 0) },
                    selected = ui.filter,
                    onSelect = vm::setFilter,
                    scrollable = true,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (ui.error != null && ui.list.isEmpty()) {
                item { EmptyState("Couldn't load customers", ui.error!!, actionLabel = "Try again", onAction = vm::refresh, topPadding = 40.dp) }
            } else if (!ui.loaded) {
                item { Text("Loading customers…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp)) }
            } else if (ui.list.isEmpty()) {
                item { EmptyState("No customers found", "Try a different name, phone or filter", topPadding = 40.dp) }
            }
            items(ui.list, key = { it.id }) { customer -> CustomerRow(customer) { onOpen(customer.id) } }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun CustomerRow(customer: Customer, onClick: () -> Unit) {
    BeamCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InitialsAvatar(customer.name, customer.id, 42.dp, online = customer.online)
            Column(Modifier.weight(1f)) {
                Text(customer.name, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(customer.planName.ifBlank { null }, customer.pppoeUser ?: customer.phone).joinToString(" · "),
                    style = BeamType.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(customer.status.label, customer.status.tone())
                Text(
                    ksh(customer.displayBalance),
                    style = BeamType.Amount.copy(color = if (customer.owing) BeamColors.Danger else BeamColors.Ink),
                )
            }
        }
    }
}

