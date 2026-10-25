package com.helper.beamnetworks.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.CustomerStatus
import com.helper.beamnetworks.data.Loadable
import com.helper.beamnetworks.ui.components.AvatarStack
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.Meter
import com.helper.beamnetworks.ui.components.NavRow
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.TextLink
import com.helper.beamnetworks.ui.components.avatarSeed
import com.helper.beamnetworks.ui.components.openCallerIdSettings
import com.helper.beamnetworks.ui.components.percentOf
import com.helper.beamnetworks.ui.components.rememberCallerIdReady
import com.helper.beamnetworks.ui.components.pressable
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.longDate
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ChartDay(val date: LocalDate, val billed: Double, val collected: Double)
data class UpcomingInstall(val id: Int, val label: String, val customer: String, val technician: String?)

data class HomeUi(
    val greetingName: String = "Beam Networks",
    val month: String = "",
    val collected: Double = 0.0,
    /** Null when invoices can't be read (permission or module), so the % is hidden. */
    val billed: Double? = null,
    val payers: List<Pair<String, Int>> = emptyList(),
    val customers: Int = 0,
    val online: Int = 0,
    val openTickets: Int = 0,
    val chart: List<ChartDay> = emptyList(),
    val owingCount: Int = 0,
    val owingTotal: Double = 0.0,
    val suspendedCount: Int = 0,
    val lowStock: List<String> = emptyList(),
    val upcoming: List<UpcomingInstall> = emptyList(),
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    val attentionCount: Int get() = listOf(owingCount > 0, suspendedCount > 0, lowStock.isNotEmpty()).count { it }
}

class HomeViewModel(private val app: AppContainer) : ViewModel() {
    private val loads = combine(
        app.customers.customers.state,
        app.payments.payments.state,
        app.payments.invoices.state,
        app.tickets.tickets.state,
    ) { customers, payments, invoices, tickets -> Quad(customers, payments, invoices, tickets) }

    val ui = combine(loads, app.jobs.jobs.state, app.inventory.items.state, app.sessions.session) { l, jobs, stock, session ->
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)
        val payments = l.payments.items
        val monthPayments = payments.filter { (it.date?.toLocalDate() ?: LocalDate.MIN) >= monthStart }
        val billedAvailable = l.invoices.loaded && l.invoices.error == null
        val customers = l.customers.items
        val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
        HomeUi(
            greetingName = session?.tenantName ?: "Beam Networks",
            month = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            collected = monthPayments.sumOf { it.amount },
            billed = if (billedAvailable) l.invoices.items.filter { (it.date ?: LocalDate.MIN) >= monthStart }.sumOf { it.total } else null,
            payers = payments.take(4).map { p -> p.customerName.ifBlank { "?" } to (p.customerId ?: avatarSeed(p.customerName)) },
            customers = customers.size,
            online = customers.count { it.online },
            openTickets = l.tickets.items.count { it.status.isOpen },
            chart = days.map { day ->
                ChartDay(
                    day,
                    billed = if (billedAvailable) l.invoices.items.filter { it.date == day }.sumOf { it.total } else 0.0,
                    collected = payments.filter { it.date?.toLocalDate() == day }.sumOf { it.amount },
                )
            },
            owingCount = customers.count { it.owing },
            owingTotal = customers.filter { it.owing }.sumOf { it.amountDue },
            suspendedCount = customers.count { it.status == CustomerStatus.Suspended },
            lowStock = stock.items.filter { it.isLow }.map { it.name },
            upcoming = jobs.items.filter { it.status.isUpcoming }.sortedBy { it.scheduled }.take(3)
                .map { UpcomingInstall(it.id, whenLabel(it.scheduled), it.customerName, it.technicianName) },
            refreshing = listOf(l.customers, l.payments, l.tickets).any { it.loading },
            error = listOf(l.customers, l.payments).firstNotNullOfOrNull { it.error },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun load() {
        with(app) {
            customers.customers.ensureLoaded()
            payments.payments.ensureLoaded()
            payments.invoices.ensureLoaded()
            tickets.tickets.ensureLoaded()
            jobs.jobs.ensureLoaded()
            inventory.items.ensureLoaded()
        }
    }

    fun refresh() = app.refreshAll()

    private data class Quad<A, B, C, D>(val customers: Loadable<A>, val payments: Loadable<B>, val invoices: Loadable<C>, val tickets: Loadable<D>)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    listState: LazyListState,
    onAccounts: () -> Unit,
    onCustomers: (filter: String?) -> Unit,
    onTickets: () -> Unit,
    onInstalls: () -> Unit,
    onInstall: (Int) -> Unit,
    onStock: () -> Unit,
) {
    val vm = containerViewModel { app, _ -> HomeViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var selectedDay by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(13) }
    LaunchedEffect(Unit) { vm.load() }
    val callerIdReady = rememberCallerIdReady()
    val context = LocalContext.current

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 16.dp)) {
                    Text("Welcome back,\n${ui.greetingName}", style = BeamType.Greeting)
                    Text("Here's how your network is doing today", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (ui.error != null) {
                item { Text(ui.error!!, style = BeamType.Meta.copy(color = BeamColors.Danger), modifier = Modifier.padding(bottom = 12.dp, start = 2.dp)) }
            }
            if (!callerIdReady) {
                item {
                    NavRow(
                        BeamIcons.Phone, Tones.Teal, "Turn on caller ID",
                        "Allow the popup to show customer details when they call",
                        { openCallerIdSettings(context) }, modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            item { HeroCard(ui, onAccounts) }
            item {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(ui.customers, "Customers", Modifier.weight(1f)) { onCustomers("all") }
                    StatCard(ui.online, "Online now", Modifier.weight(1f)) { onCustomers("online") }
                    StatCard(ui.openTickets, "Open tickets", Modifier.weight(1f), onTickets)
                }
            }
            item { ChartCard(ui, selectedDay.coerceIn(0, (ui.chart.size - 1).coerceAtLeast(0))) { selectedDay = it } }
            if (ui.attentionCount > 0) {
                item {
                    SectionHeader("Needs attention") {
                        Text("${ui.attentionCount} item${if (ui.attentionCount == 1) "" else "s"}", style = BeamType.Meta)
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.owingCount > 0) {
                            NavRow(BeamIcons.Banknote, Tones.Red, "${ui.owingCount} customer${if (ui.owingCount == 1) "" else "s"} owing", "${kshShort(ui.owingTotal)} outstanding", { onCustomers("owing") })
                        }
                        if (ui.suspendedCount > 0) {
                            NavRow(BeamIcons.PauseCircle, Tones.Orange, "${ui.suspendedCount} account${if (ui.suspendedCount == 1) "" else "s"} suspended", "Waiting on payment to reconnect", { onCustomers("suspended") })
                        }
                        if (ui.lowStock.isNotEmpty()) {
                            NavRow(BeamIcons.Package, Tones.Blue, "${ui.lowStock.size} item${if (ui.lowStock.size == 1) "" else "s"} running low", ui.lowStock.joinToString(", "), onStock)
                        }
                    }
                }
            }
            item {
                SectionHeader("Upcoming jobs") { TextLink("See all", onInstalls) }
            }
            item {
                ListCard(horizontalPadding = 14.dp) {
                    if (ui.upcoming.isEmpty()) {
                        Text("Nothing scheduled.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 12.dp))
                    }
                    ui.upcoming.forEachIndexed { index, job ->
                        if (index > 0) BeamDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { onInstall(job.id) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                job.label,
                                style = BeamType.Tag.copy(color = BeamColors.TextMuted),
                                maxLines = 1,
                                modifier = Modifier.background(BeamColors.InputFill, BeamShapes.Pill).padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            Text(job.customer, style = BeamType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            job.technician?.let { InitialsAvatar(it, avatarSeed(it), 26.dp) }
                        }
                    }
                }
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun HeroCard(ui: HomeUi, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .dropShadow(BeamShapes.CardLarge, Shadow(radius = 24.dp, color = BeamColors.Primary.copy(alpha = 0.28f), offset = DpOffset(0.dp, 10.dp)))
            .pressable(BeamShapes.CardLarge, BeamColors.Primary, BeamColors.PrimaryPressed, onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val white = Color.White
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${ui.month} collections", style = BeamType.SmallStrong.copy(color = white.copy(alpha = 0.85f)), modifier = Modifier.weight(1f))
            Text(
                "Accounts →",
                style = BeamType.MetaStrong.copy(color = white),
                modifier = Modifier.background(white.copy(alpha = 0.18f), BeamShapes.Pill).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(ksh(ui.collected), style = BeamType.Hero.copy(color = white))
        if (ui.billed != null) {
            val pct = percentOf(ui.collected, ui.billed)
            Column {
                Row(Modifier.padding(bottom = 6.dp)) {
                    Text("Collected of ${kshShort(ui.billed)} billed", style = BeamType.Meta.copy(color = white.copy(alpha = 0.85f)), modifier = Modifier.weight(1f))
                    Text("$pct%", style = BeamType.Amount.copy(color = white.copy(alpha = 0.85f)))
                }
                Meter(pct / 100f, white, white.copy(alpha = 0.25f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                longDate(LocalDate.now(zone)),
                style = BeamType.MetaStrong.copy(color = white),
                modifier = Modifier.background(white.copy(alpha = 0.18f), BeamShapes.Pill).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                // Offset the stack so its overlap doesn't push it off the right edge
                AvatarStack(ui.payers, BeamColors.Primary, Modifier.padding(start = 8.dp).semantics { contentDescription = "Recent payers" })
            }
        }
    }
}

@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BeamCard(modifier = modifier, onClick = onClick) {
        Text(value.toString(), style = BeamType.Stat)
        Text(label, style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ChartCard(ui: HomeUi, selected: Int, onSelect: (Int) -> Unit) {
    if (ui.chart.isEmpty()) return
    val showBilled = ui.billed != null
    val max = ui.chart.maxOf { maxOf(it.billed, it.collected) }.coerceAtLeast(1.0)
    val day = ui.chart[selected]
    val dayLabel = day.date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    BeamCard(modifier = Modifier.padding(top = 12.dp), shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Collected on $dayLabel", style = BeamType.Meta)
                Text(kshShort(day.collected), style = BeamType.Stat)
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showBilled) Legend(BeamColors.Primary, "Billed")
                Legend(BeamColors.ChartCollected, "Collected")
            }
        }
        Row(
            Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ui.chart.forEachIndexed { index, d ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(if (index == selected) 1f else 0.55f)
                        .clickable(role = Role.Button, onClickLabel = "Show ${d.date}") { onSelect(index) },
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (showBilled) Bar(d.billed / max, BeamColors.Primary)
                    Bar(d.collected / max, BeamColors.ChartCollected)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ui.chart.forEachIndexed { index, d ->
                val isSelected = index == selected
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        d.date.dayOfMonth.toString(),
                        style = BeamType.Tiny.copy(color = if (isSelected) Color.White else BeamColors.TextMuted, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                        modifier = Modifier
                            .size(20.dp)
                            .background(if (isSelected) BeamColors.Primary else Color.Transparent, CircleShape)
                            .clickable { onSelect(index) }
                            .padding(top = 3.dp),
                    )
                }
            }
        }
        Text(
            day.date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            style = BeamType.MetaStrong.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun Bar(fraction: Double, color: Color) {
    Box(
        Modifier
            .width(4.dp)
            .height((fraction * 120).dp.coerceAtLeast(3.dp))
            .background(color, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = BeamType.Tag.copy(color = BeamColors.TextMuted, fontWeight = FontWeight.Normal))
    }
}
