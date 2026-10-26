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
const val LIPWA_LINK = "https://app.payhero.co.ke/lipwa/1787"

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

    val staff get() = app.jobs.staff
    val toasts get() = app.toasts
    var movements by mutableStateOf<List<com.helper.beamnetworks.data.api.InventoryMovementDto>?>(null)
        private set
    var moving by mutableStateOf(false)
        private set

    fun loadMovements() {
        viewModelScope.launch {
            movements = try {
                com.helper.beamnetworks.data.api.apiCall { app.api.movements() }.results
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't load stock history")
                emptyList()
            }
        }
    }

    fun move(item: StockItem, type: String, quantity: Int, staffUserId: Int?, notes: String, onDone: () -> Unit) {
        if (moving) return
        moving = true
        viewModelScope.launch {
            try {
                app.inventory.move(item, type, quantity, staffUserId, notes)
                app.toasts.show("${item.name}: ${movementLabel(type).lowercase()} $quantity")
                movements = null
                onDone()
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't record that")
            } finally {
                moving = false
            }
        }
    }

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

/** Movement types Beam allows per item type (API.md §17). */
private fun movementsFor(itemType: String): List<String> = when (itemType) {
    "tool" -> listOf("assign", "return", "receive", "adjust_out")
    "asset" -> listOf("assign", "install", "return", "receive", "adjust_out")
    else -> listOf("issue", "receive", "adjust_out")
}

private fun movementLabel(type: String): String = when (type) {
    "issue" -> "Issue to technician"
    "assign" -> "Assign to technician"
    "install" -> "Install at customer"
    "return" -> "Returned"
    "receive" -> "Receive new stock"
    "adjust_out" -> "Write off (lost/damaged)"
    "transfer" -> "Transfer"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun movementNeedsStaff(type: String) = type == "issue" || type == "assign" || type == "return"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(onEdit: (Int) -> Unit) {
    val vm = containerViewModel { app, _ -> StockViewModel(app) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var history by rememberSaveable { mutableStateOf(false) }
    var moveItem by remember { mutableStateOf<StockItem?>(null) }
    LaunchedEffect(history, vm.movements) { if (history && vm.movements == null) vm.loadMovements() }
    PullToRefreshBox(isRefreshing = ui.loading && ui.loaded, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                TwoColumns(
                    { SmallStat(ui.items.size.toString(), "Products", BeamColors.Ink) },
                    { SmallStat(ui.items.count { it.isLow }.toString(), "Running low", BeamColors.Warning) },
                    Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                PillTabs(
                    options = listOf(PillTabOption(false, "Products"), PillTabOption(true, "History")),
                    selected = history,
                    onSelect = { history = it },
                )
            }
            if (history) {
                val list = vm.movements
                item {
                    ListCard {
                        when {
                            list == null -> Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
                            list.isEmpty() -> Text("No stock movements yet.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
                        }
                        list.orEmpty().forEachIndexed { i, m ->
                            if (i > 0) BeamDivider()
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.itemName ?: "Item"} · ${movementLabel(m.movementType)}", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(m.assignedToName ?: m.assignedToEmail, m.customerName, m.notes?.ifBlank { null }).joinToString(" · ").ifBlank { "—" },
                                        style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("×${m.quantity}", style = BeamType.RowTitle)
                                    Text(shortDate(com.helper.beamnetworks.util.parseDateTime(m.createdAt)?.toLocalDate()), style = BeamType.Meta)
                                }
                            }
                        }
                    }
                }
                item { BottomSpacer() }
                return@LazyColumn
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
                            Text("Reorder at ${item.reorderAt} · ${item.itemType.replaceFirstChar { it.uppercase() }}", style = BeamType.Meta)
                        }
                        com.helper.beamnetworks.ui.components.PillButton("Move", Tones.Blue, BeamColors.PrimaryTintPressed, { moveItem = item })
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
    moveItem?.let { item -> MoveSheet(vm, ui.items.firstOrNull { it.id == item.id } ?: item) { moveItem = null } }
}

@Composable
private fun MoveSheet(vm: StockViewModel, item: StockItem, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val staff by vm.staff.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.staff.ensureLoaded() }
    val types = movementsFor(item.itemType)
    var type by rememberSaveable { mutableStateOf(types.first()) }
    var qty by rememberSaveable { mutableStateOf("1") }
    var staffId by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    com.helper.beamnetworks.ui.components.BeamSheet(
        "Move ${item.name}", onClose, toast,
        ctaLabel = if (vm.moving) "Saving…" else "Save",
        onCta = {
            val q = qty.toIntOrNull()
            when {
                q == null || q < 1 -> vm.toasts.show("Quantity must be at least 1")
                movementNeedsStaff(type) && staffId == null -> vm.toasts.show("Choose the technician")
                else -> vm.move(item, type, q, staffId.takeIf { movementNeedsStaff(type) }, notes, onClose)
            }
        },
        ctaEnabled = !vm.moving,
    ) {
        Text("${item.quantity} in stock", style = BeamType.Meta)
        com.helper.beamnetworks.ui.components.DropdownField("What happened", movementLabel(type), types.map(::movementLabel), { label -> type = types.first { movementLabel(it) == label } })
        com.helper.beamnetworks.ui.components.FormField("Quantity", qty, { qty = it.filter(Char::isDigit).take(4) }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        if (movementNeedsStaff(type)) {
            com.helper.beamnetworks.ui.components.DropdownField(
                "Technician",
                staff.items.firstOrNull { it.userId == staffId }?.name.orEmpty(),
                if (staff.items.isEmpty()) listOf("Loading staff…") else staff.items.map { it.name },
                { name -> staffId = staff.items.firstOrNull { it.name == name }?.userId },
            )
        }
        com.helper.beamnetworks.ui.components.FormField("Notes (optional)", notes, { notes = it })
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
                monthExpenses.map { e -> LedgerRow("e${e.id}", e.title, listOf(e.shop, e.category).filter { it.isNotBlank() }.joinToString(" · "),"−" + kshShort(e.amount), shortDate(e.date), false, e.id) }
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
