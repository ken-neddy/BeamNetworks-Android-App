package com.helper.beamnetworks.ui.screens.account

import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Expense
import com.helper.beamnetworks.data.Payment
import com.helper.beamnetworks.data.StockItem
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.CircleButton
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.Meter
import com.helper.beamnetworks.ui.components.NavRow
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.QtyStepper
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.openCallerIdSettings
import com.helper.beamnetworks.ui.components.pressable
import com.helper.beamnetworks.ui.components.rememberCallerIdReady
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.screens.customers.paymentSource
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.shortDate
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Public self-service payment page (was "Lipwa Link" in the old drawer). */
private const val LIPWA_LINK = "https://app.payhero.co.ke/lipwa/1787"

data class AccountUi(
    val email: String = "",
    val role: String = "",
    val tenant: String = "Beam Networks",
    val lowCount: Int = 0,
    val onlineCount: Int = 0,
)

class AccountViewModel(private val app: AppContainer) : ViewModel() {
    val ui = combine(app.sessions.session, app.inventory.items.state, app.customers.customers.state) { session, stock, customers ->
        AccountUi(
            email = session?.email.orEmpty(),
            role = if (session?.isAdmin == true) "Admin" else "Staff",
            tenant = session?.tenantName ?: "Beam Networks",
            lowCount = stock.items.count { it.isLow },
            onlineCount = customers.items.count { it.online },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUi())

    init {
        app.inventory.items.ensureLoaded()
        app.customers.customers.ensureLoaded()
    }

    fun signOut() = app.logout()
}

@Composable
fun AccountScreen(onNotifications: () -> Unit, onNetwork: () -> Unit, onAnnouncements: () -> Unit, onStock: () -> Unit, onAccounts: () -> Unit, onSessions: () -> Unit, onCallLog: () -> Unit, onMap: () -> Unit, onMessages: () -> Unit) {
    val vm = containerViewModel { app, _ -> AccountViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val callerIdReady = rememberCallerIdReady()
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        item {
            BeamCard(shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).background(BeamColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
                        Text("BN", style = BeamType.BodyStrong.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ui.tenant, style = BeamType.RowTitleLarge)
                        Text("${ui.email} · ${ui.role}", style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            ListCard(Modifier.padding(top = 12.dp), horizontalPadding = 14.dp) {
                val rows = listOf<@Composable () -> Unit>(
                    { NavRow(BeamIcons.Activity, Tones.Red, "Network status", "Routers and customers offline now", onNetwork, tileSize = 38.dp, inCard = true, trailing = {}) },
                    { NavRow(BeamIcons.Bell, Tones.Orange, "Notifications", "Choose which alerts you get", onNotifications, tileSize = 38.dp, inCard = true, trailing = {}) },
                    { NavRow(BeamIcons.MessageSquare, Tones.Purple, "Announcements", "SMS, email or portal message to customers", onAnnouncements, tileSize = 38.dp, inCard = true, trailing = {}) },
                    {
                        NavRow(BeamIcons.Package, Tones.Blue, "Stock", "Routers, cable and connectors", onStock, tileSize = 38.dp, inCard = true, trailing = {
                            if (ui.lowCount > 0) Tag("${ui.lowCount} low", Tones.Orange)
                        })
                    },
                    { NavRow(BeamIcons.Banknote, Tones.Green, "Accounts", "Income, expenses and net", onAccounts, tileSize = 38.dp, inCard = true, trailing = {}) },
                    {
                        NavRow(BeamIcons.Link, Tones.Orange, "Lipwa Link", "Share a self‑service payment link", {
                            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Pay for your Beam Networks internet here: $LIPWA_LINK")
                            context.startActivity(Intent.createChooser(share, "Share payment link"))
                        }, tileSize = 38.dp, inCard = true, trailing = {})
                    },
                    { NavRow(BeamIcons.Activity, Tones.Purple, "PPPoE sessions", "${ui.onlineCount} active right now", onSessions, tileSize = 38.dp, inCard = true, trailing = {}) },
                    { NavRow(BeamIcons.Phone, Tones.Teal, "Call log", "Recent calls, matched to customers", onCallLog, tileSize = 38.dp, inCard = true, trailing = {}) },
                    {
                        NavRow(
                            BeamIcons.Bell, Tones.Blue, "Caller ID popup",
                            if (callerIdReady) "On — shows who is calling" else "Off — tap to allow",
                            { openCallerIdSettings(context) }, tileSize = 38.dp, inCard = true,
                            trailing = { Tag(if (callerIdReady) "On" else "Off", if (callerIdReady) Tones.Green else Tones.Orange) },
                        )
                    },
                    { NavRow(BeamIcons.MapPin, Tones.Red, "Map", "Find and share a location", onMap, tileSize = 38.dp, inCard = true, trailing = {}) },
                    { NavRow(BeamIcons.MessageSquare, Tones.Green, "M‑Pesa messages", "Payment SMS on this phone", onMessages, tileSize = 38.dp, inCard = true, trailing = {}) },
                )
                rows.forEachIndexed { i, row ->
                    row()
                    if (i < rows.lastIndex) BeamDivider()
                }
            }
        }
        item { SecondaryButton("Sign out", vm::signOut, Modifier.padding(top = 12.dp), textColor = BeamColors.Danger, height = 48.dp, bold = true) }
        item { BottomSpacer() }
    }
}

// ---- Stock ----

data class StockUi(val items: List<StockItem> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null, val moduleDisabled: Boolean = false)

class StockViewModel(private val app: AppContainer) : ViewModel() {
    val ui = combine(app.inventory.items.state, MutableStateFlow(Unit)) { s, _ ->
        StockUi(s.items, s.loading, s.loaded, s.error, s.moduleDisabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUi())

    private val pending = mutableMapOf<Int, Int>()
    private val timers = mutableMapOf<Int, Job>()

    init {
        app.inventory.items.ensureLoaded()
    }

    fun refresh() = app.inventory.items.refreshAsync()

    /** Steppers update at once; taps are batched into one adjust call after a short pause. */
    fun step(item: StockItem, delta: Int) {
        if (item.quantity + delta < 0) return
        app.inventory.adjustLocally(item.id, delta)
        pending[item.id] = (pending[item.id] ?: 0) + delta
        timers[item.id]?.cancel()
        timers[item.id] = viewModelScope.launch {
            delay(700)
            val total = pending.remove(item.id) ?: 0
            try {
                app.inventory.commitAdjust(item.id, total)
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't update ${item.name}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(onEdit: (Int) -> Unit) {
    val vm = containerViewModel { app, _ -> StockViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = ui.loading && ui.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                TwoColumns(
                    { SmallStat(ui.items.size.toString(), "Products", BeamColors.Ink) },
                    { SmallStat(ui.items.count { it.isLow }.toString(), "Running low", BeamColors.Warning) },
                    Modifier.padding(bottom = 4.dp),
                )
            }
            when {
                ui.moduleDisabled -> item { EmptyState("Inventory is off", "Enable the inventory module in Beam to track stock.") }
                ui.error != null && ui.items.isEmpty() -> item { EmptyState("Couldn't load stock", ui.error!!, actionLabel = "Try again", onAction = vm::refresh) }
                !ui.loaded -> item { Text("Loading stock…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp)) }
                ui.items.isEmpty() -> item { EmptyState("No products yet", "Add routers, cable and connectors with the + button.") }
            }
            items(ui.items, key = { it.id }) { item ->
                BeamCard(onClick = { onEdit(item.id) }, padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Reorder at ${item.reorderAt}", style = BeamType.Meta)
                        }
                        QtyStepper(item.quantity, { vm.step(item, -1) }, { vm.step(item, 1) })
                    }
                    Meter(
                        fraction = item.quantity / (item.reorderAt * 2f).coerceAtLeast(1f),
                        color = if (item.isLow) BeamColors.WarningSolid else BeamColors.SuccessSolid,
                        trackColor = BeamColors.Divider,
                        height = 5.dp,
                    )
                }
            }
            item { BottomSpacer() }
        }
    }
}

@Composable
private fun SmallStat(value: String, label: String, color: Color) {
    BeamCard(padding = PaddingValues(14.dp)) {
        Text(value, style = BeamType.StatLarge.copy(color = color))
        Text(label, style = BeamType.Meta)
    }
}

// ---- Accounts ----

enum class LedgerSegment { Income, Expenses }

data class LedgerRow(val key: String, val title: String, val subtitle: String, val amount: String, val date: String, val income: Boolean, val expenseId: String?)

data class AccountsUi(
    val month: YearMonth = YearMonth.now(zone),
    val segment: LedgerSegment = LedgerSegment.Income,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val rows: List<LedgerRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val net: Double get() = income - expenses
}

class AccountsViewModel(private val app: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now(zone))
    private val segment = MutableStateFlow(LedgerSegment.Income)

    val ui = combine(app.payments.payments.state, app.expenses.expenses, month, segment) { payments, expenses, month, segment ->
        fun inMonth(date: LocalDate?) = date != null && YearMonth.from(date) == month
        val monthPayments: List<Payment> = payments.items.filter { inMonth(it.date?.toLocalDate()) }
        val monthExpenses: List<Expense> = expenses.items.filter { inMonth(it.date) }
        AccountsUi(
            month = month,
            segment = segment,
            income = monthPayments.sumOf { it.amount },
            expenses = monthExpenses.sumOf { it.amount },
            rows = if (segment == LedgerSegment.Income) {
                monthPayments.map { p ->
                    LedgerRow("p${p.id}", p.customerName.ifBlank { "—" }, paymentSource(p), "+" + kshShort(p.amount), shortDate(p.date?.toLocalDate()), true, null)
                }
            } else {
                monthExpenses.map { e -> LedgerRow("e${e.id}", e.title, e.category, "−" + kshShort(e.amount), shortDate(e.date), false, e.id) }
            },
            loading = payments.loading,
            error = payments.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUi())

    init {
        app.payments.ensureSince(month.value.atDay(1))
    }

    fun setSegment(value: LedgerSegment) {
        segment.value = value
    }

    fun shiftMonth(by: Long) {
        val next = month.value.plusMonths(by)
        if (next > YearMonth.now(zone)) return
        month.value = next
        app.payments.ensureSince(next.atDay(1))
    }
}

@Composable
fun AccountsScreen(onEditExpense: (String) -> Unit) {
    val vm = containerViewModel { app, _ -> AccountsViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val isThisMonth = ui.month == YearMonth.now(zone)
    val money = containerViewModel(key = "money") { app, _ -> MoneyViewModel(app) }
    var tab by rememberSaveable { mutableStateOf(MoneyTab.Ledger) }
    var sheet by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(tab) {
        when (tab) {
            MoneyTab.Unmatched -> money.loadReceipts()
            MoneyTab.Pending -> money.loadPending()
            MoneyTab.Report -> money.loadReport()
            MoneyTab.Ledger -> Unit
        }
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        item {
            BeamCard(shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThisMonth) "Net this month" else "Net in " + ui.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                            style = BeamType.Meta,
                        )
                        Text(ksh(ui.net), style = BeamType.Net)
                    }
                    CircleButton(BeamIcons.ChevronLeft, "Previous month", { vm.shiftMonth(-1) }, size = 32.dp, iconSize = 16.dp, color = BeamColors.InputFill)
                    if (!isThisMonth) {
                        Box(Modifier.padding(start = 6.dp)) {
                            CircleButton(BeamIcons.ChevronRight, "Next month", { vm.shiftMonth(1) }, size = 32.dp, iconSize = 16.dp, color = BeamColors.InputFill)
                        }
                    }
                }
                TwoColumns(
                    { Tile("Income", kshShort(ui.income), Tones.Blue.background, Tones.Blue.content) },
                    { Tile("Expenses", kshShort(ui.expenses), Tones.Orange.background, Tones.Orange.content) },
                    Modifier.padding(top = 12.dp),
                )
            }
        }
        item {
            // Income and Expenses are the ledger; the rest are money tools from the billing API
            val selected: Any = if (tab == MoneyTab.Ledger) ui.segment else tab
            PillTabs(
                options = listOf<PillTabOption<Any>>(
                    PillTabOption(LedgerSegment.Income, "Income"),
                    PillTabOption(LedgerSegment.Expenses, "Expenses"),
                    PillTabOption(MoneyTab.Unmatched, "Unmatched", money.receipts?.size),
                    PillTabOption(MoneyTab.Pending, "Pending M‑Pesa", money.pending?.size),
                    PillTabOption(MoneyTab.Report, "Report"),
                ),
                selected = selected,
                onSelect = { option ->
                    when (option) {
                        is LedgerSegment -> {
                            tab = MoneyTab.Ledger
                            vm.setSegment(option)
                        }
                        is MoneyTab -> tab = option
                    }
                },
                scrollable = true,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        when (tab) {
            MoneyTab.Unmatched -> unmatchedSection(money) { sheet = it }
            MoneyTab.Pending -> pendingSection(money) { sheet = it }
            MoneyTab.Report -> reportSection(money)
            MoneyTab.Ledger -> Unit
        }
        if (tab == MoneyTab.Ledger) item {
            ListCard {
                if (ui.rows.isEmpty()) {
                    Text(
                        when {
                            ui.loading -> "Loading…"
                            ui.error != null && ui.segment == LedgerSegment.Income -> ui.error!!
                            ui.segment == LedgerSegment.Income -> "No payments this month."
                            else -> "No expenses this month."
                        },
                        style = BeamType.Small.copy(color = BeamColors.TextMuted),
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                ui.rows.forEachIndexed { index, row ->
                    if (index > 0) BeamDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (row.expenseId != null) Modifier.pressable(BeamShapes.Input, Color.Transparent, BeamColors.PressedCard, onClick = { onEditExpense(row.expenseId) }) else Modifier)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.subtitle, style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(row.amount, style = BeamType.RowTitle.copy(fontWeight = FontWeight.ExtraBold, color = if (row.income) BeamColors.Success else BeamColors.Ink, fontFeatureSettings = "tnum"))
                            Text(row.date, style = BeamType.Meta)
                        }
                    }
                }
            }
        }
        item { BottomSpacer() }
    }
    when (val target = sheet) {
        is com.helper.beamnetworks.data.api.UnmatchedReceiptDto -> AssignReceiptSheet(money, target) { sheet = null }
        is com.helper.beamnetworks.data.api.PendingPaymentDto -> VerifyPaymentSheet(money, target) { sheet = null }
    }
}

enum class MoneyTab { Ledger, Unmatched, Pending, Report }

@Composable
private fun Tile(label: String, value: String, background: Color, labelColor: Color) {
    Column(Modifier.fillMaxWidth().background(background, BeamShapes.Tile).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = BeamType.Tag.copy(color = labelColor))
        Text(value, style = BeamType.RowTitleLarge.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    }
}
