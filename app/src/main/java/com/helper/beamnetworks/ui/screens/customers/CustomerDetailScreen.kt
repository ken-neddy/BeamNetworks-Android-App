package com.helper.beamnetworks.ui.screens.customers

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.CustomerStatus
import com.helper.beamnetworks.data.Payment
import com.helper.beamnetworks.data.api.ActivityEventDto
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.ChangePlanRequest
import com.helper.beamnetworks.data.api.CreditNoteCreate
import com.helper.beamnetworks.data.api.CreditNoteDto
import com.helper.beamnetworks.data.api.CustomerFullDto
import com.helper.beamnetworks.data.api.CustomerUpdate
import com.helper.beamnetworks.data.api.InvoiceCreate
import com.helper.beamnetworks.data.api.InvoiceFullDto
import com.helper.beamnetworks.data.api.NewLineItem
import com.helper.beamnetworks.data.api.PauseRequest
import com.helper.beamnetworks.data.api.SessionDto
import com.helper.beamnetworks.data.api.UsagePointDto
import com.helper.beamnetworks.data.api.WalletDto
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.BottomSpacer
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.IconTile
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.NavRow
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.SwitchRow
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.navigation.CustomerDetailRoute
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamShapes
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.lastOnlineLabel
import com.helper.beamnetworks.util.moneyString
import com.helper.beamnetworks.util.parseAmountInput
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseMoney
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.shortDate
import com.helper.beamnetworks.util.whenLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDate
import java.util.Locale

enum class CustomerTab(val label: String) { Overview("Overview"), Activity("Activity"), Billing("Billing"), Usage("Usage") }

data class CustomerDetailUi(val customer: Customer? = null, val payments: List<Payment> = emptyList(), val loaded: Boolean = false)

class CustomerDetailViewModel(private val app: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<CustomerDetailRoute>().id

    val ui = combine(app.customers.customers.state, app.payments.payments.state) { customers, payments ->
        val customer = customers.items.firstOrNull { it.id == id }
        CustomerDetailUi(
            customer = customer,
            payments = payments.items.filter { p -> p.customerId == id || (p.customerId == null && customer != null && p.customerName.equals(customer.name, true)) },
            loaded = customers.loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailUi())

    val plans = app.customers.plans.state
    val toasts = app.toasts

    var full by mutableStateOf<CustomerFullDto?>(null)
        private set
    var session by mutableStateOf<SessionDto?>(null)
        private set
    var activity by mutableStateOf<List<ActivityEventDto>?>(null)
        private set
    var usage by mutableStateOf<List<UsagePointDto>?>(null)
        private set
    var invoices by mutableStateOf<List<InvoiceFullDto>?>(null)
        private set
    var credits by mutableStateOf<List<CreditNoteDto>?>(null)
        private set
    var wallet by mutableStateOf<WalletDto?>(null)
        private set
    var tabError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    init {
        app.customers.customers.ensureLoaded()
        app.payments.payments.ensureLoaded()
        app.customers.plans.ensureLoaded()
        loadOverview()
    }

    /** This customer's own confirmed payments (any date), newest first; null until loaded. */
    var paymentHistory by mutableStateOf<List<Payment>?>(null)
        private set

    fun loadOverview() {
        viewModelScope.launch {
            full = runCatching { apiCall { app.api.customer(id) } }.getOrNull()
            session = runCatching { apiCall { app.api.sessions(customer = id) } }.getOrNull()?.firstOrNull { it.status == "active" }
            paymentHistory = runCatching { apiCall { app.api.paymentRecords(status = "confirmed", customerType = "pppoe", customer = id, pageSize = 25) }.results }
                .getOrNull()
                ?.map { p ->
                    Payment(
                        id = p.id,
                        customerId = p.pppoeCustomer,
                        customerName = p.customerName.orEmpty(),
                        amount = parseMoney(p.amount),
                        method = p.method.orEmpty(),
                        reference = p.mpesaTransactionId?.ifBlank { null } ?: p.reference?.ifBlank { null },
                        date = parseDateTime(p.confirmedAt) ?: parseDateTime(p.createdAt),
                    )
                }
                ?.sortedByDescending { it.date }
        }
    }

    fun loadTab(tab: CustomerTab) {
        tabError = null
        viewModelScope.launch {
            try {
                when (tab) {
                    CustomerTab.Overview -> loadOverview()
                    CustomerTab.Activity -> activity = apiCall { app.api.activityLog(customer = id, pageSize = 50) }.results
                    CustomerTab.Usage -> usage = apiCall { app.api.usage(id) }
                    CustomerTab.Billing -> {
                        invoices = apiCall { app.api.customerInvoices(id) }.results
                        credits = runCatching { apiCall { app.api.creditNotes(id) }.results }.getOrNull()
                        wallet = runCatching { apiCall { app.api.wallet(id) } }.getOrNull()
                    }
                }
            } catch (e: ApiException) {
                tabError = e.message
            }
        }
    }

    fun sendReminder(customer: Customer) = act("remind", "Pay link sent to ${customer.phone}") { app.customers.sendPayLink(customer) }

    fun sharePayLink(context: Context, customer: Customer) = act("link", null) {
        val link = apiCall { app.api.payLink(id) }.link
        val text = "Hi ${customer.name.substringBefore(' ')}, pay for your Beam Networks internet here: $link"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share pay link"))
    }

    fun suspend(customer: Customer) = act("status", "${customer.name} suspended") { app.customers.suspend(customer) }
    fun reactivate(customer: Customer) = act("status", "${customer.name} reactivated") { app.customers.reactivate(customer) }

    fun resume() = act("status", "Service resumed") {
        apiCall { app.api.resumeCustomer(id) }
        refreshCustomer()
    }

    fun pause(days: Int, onDone: () -> Unit) = act("pause", "Paused for $days days") {
        apiCall { app.api.pauseCustomer(id, PauseRequest(weeks = days / 7, days = days % 7)) }
        refreshCustomer()
        onDone()
    }

    fun changePlan(planId: Int, onDone: () -> Unit) = act("plan", "Plan changed. A prorated invoice was raised.") {
        apiCall { app.api.changePlan(id, ChangePlanRequest(planId)) }
        refreshCustomer()
        onDone()
    }

    fun resendPortal() = act("portal", "Portal login sent to the customer") { apiCall { app.api.resendPortalCredentials(id) } }

    fun archive(customer: Customer) = act("archive", "${customer.name} archived") { app.customers.archive(customer) }

    fun setStaticIp(enabled: Boolean, address: String, onDone: () -> Unit) = act("ip", if (enabled) "Static IP assigned" else "Static IP released") {
        apiCall { app.api.setStaticIp(id, com.helper.beamnetworks.data.api.StaticIpRequest(enabled, address.trim().ifBlank { null }.takeIf { enabled })) }
        refreshCustomer()
        onDone()
    }

    /** [unit] is "days", "weeks" or "until" (then [until] is used). */
    fun extendService(customer: Customer, unit: String, count: Int, until: LocalDate, reason: String, onDone: () -> Unit) = act("extend", "${customer.name}'s service extended") {
        when (unit) {
            "weeks" -> app.customers.extendService(customer, reason, weeks = count)
            "until" -> app.customers.extendService(customer, reason, resumeAt = com.helper.beamnetworks.util.isoOffset(until, 23, 59))
            else -> app.customers.extendService(customer, reason, days = count)
        }
        refreshCustomer()
        onDone()
    }

    fun update(body: CustomerUpdate, onDone: () -> Unit) = act("edit", "Details saved") {
        apiCall { app.api.updateCustomer(id, body) }
        refreshCustomer()
        onDone()
    }

    fun creditNote(amount: Double, reason: String, onDone: () -> Unit) = act("credit", "Credit of ${kshShort(amount)} added") {
        apiCall { app.api.createCreditNote(CreditNoteCreate(id, moneyString(amount), reason.trim())) }
        refreshCustomer()
        loadTab(CustomerTab.Billing)
        onDone()
    }

    fun invoice(description: String, amount: Double, quantity: Int, dueInDays: Int, onDone: () -> Unit) = act("invoice", "Invoice raised") {
        apiCall { app.api.createInvoice(InvoiceCreate(id, listOf(NewLineItem(description.trim(), quantity.toString(), moneyString(amount))), dueInDays)) }
        refreshCustomer()
        loadTab(CustomerTab.Billing)
        onDone()
    }

    fun sendInvoice(invoice: InvoiceFullDto) = act("send-${invoice.id}", "Invoice ${invoice.invoiceNumber} sent") {
        apiCall { app.api.sendInvoice(invoice.id) }
        loadTab(CustomerTab.Billing)
    }

    /** Downloads the invoice PDF into the cache and opens the share sheet. */
    fun shareInvoice(context: Context, invoice: InvoiceFullDto) = act("pdf-${invoice.id}", null) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
            File(dir, "${invoice.invoiceNumber.ifBlank { "invoice-${invoice.id}" }}.pdf").also { out ->
                apiCall { app.api.invoicePdf(invoice.id) }.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, "Share invoice"))
    }

    fun disconnect() = act("disconnect", null) {
        val s = session ?: return@act
        val result = apiCall { app.api.disconnectSession(s.id) }
        app.toasts.show(if (result.disconnectSent) "Disconnected. The router may reconnect them." else result.detail ?: "Disconnect sent but not confirmed")
        session = null
    }

    private suspend fun refreshCustomer() {
        app.customers.customers.refresh()
        full = runCatching { apiCall { app.api.customer(id) } }.getOrNull()
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
            } catch (e: java.io.IOException) {
                app.toasts.show("Couldn't save the file")
            } finally {
                busy = null
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(onRecordPayment: (Int) -> Unit, onRestore: (Int) -> Unit, onRaiseTicket: (Int) -> Unit) {
    val vm = containerViewModel { app, handle -> CustomerDetailViewModel(app, handle) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(CustomerTab.Overview) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) { if (tab != CustomerTab.Overview) vm.loadTab(tab) }
    val customer = ui.customer
    if (customer == null) {
        EmptyState(if (ui.loaded) "Customer not found" else "Loading…", if (ui.loaded) "They may have been archived." else "")
        return
    }
    val call = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${customer.phone}".toUri())) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        item { ProfileCard(customer) }
        item { PrimaryButton("Record payment", { onRecordPayment(customer.id) }, Modifier.padding(top = 12.dp), height = 50.dp) }
        item {
            TwoColumns(
                { SecondaryButton(if (vm.busy == "remind") "Sending…" else "Send reminder", { vm.sendReminder(customer) }) },
                {
                    when (customer.status) {
                        CustomerStatus.Active -> SecondaryButton("Suspend", { vm.suspend(customer) }, textColor = BeamColors.Danger)
                        CustomerStatus.Overdue -> SecondaryButton("Reactivate", { vm.reactivate(customer) }, textColor = BeamColors.Success)
                        CustomerStatus.Suspended -> SecondaryButton("Reconnect", { onRestore(customer.id) }, textColor = BeamColors.Success)
                        CustomerStatus.Paused -> SecondaryButton("Resume", vm::resume, textColor = BeamColors.Success)
                        CustomerStatus.Archived -> SecondaryButton("Call", call)
                    }
                },
                Modifier.padding(top = 8.dp),
            )
        }
        item {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton("Call", call) }
                Box(Modifier.weight(1f)) { SecondaryButton(if (vm.busy == "link") "…" else "Pay link", { vm.sharePayLink(context, customer) }) }
                Box(Modifier.weight(1f)) { SecondaryButton("More", { sheet = "more" }, bold = true) }
            }
        }
        item {
            PillTabs(
                options = CustomerTab.entries.map { PillTabOption(it, it.label) },
                selected = tab,
                onSelect = { tab = it },
                scrollable = true,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        if (vm.tabError != null && tab != CustomerTab.Overview) {
            item { Text(vm.tabError!!, style = BeamType.Small.copy(color = BeamColors.Danger), modifier = Modifier.padding(8.dp)) }
        }
        when (tab) {
            CustomerTab.Overview -> overview(vm, customer, vm.paymentHistory ?: ui.payments, onRaiseTicket)
            CustomerTab.Activity -> activityTab(vm.activity)
            CustomerTab.Billing -> billingTab(vm, context, onNewInvoice = { sheet = "invoice" }, onCredit = { sheet = "credit" })
            CustomerTab.Usage -> usageTab(vm.usage)
        }
        item { BottomSpacer() }
    }

    when (sheet) {
        "more" -> MoreSheet(vm, customer, onClose = { sheet = null }, open = { sheet = it })
        "plan" -> PlanSheet(vm, customer) { sheet = null }
        "pause" -> PauseSheet(vm) { sheet = null }
        "edit" -> EditSheet(vm, customer) { sheet = null }
        "credit" -> CreditSheet(vm) { sheet = null }
        "invoice" -> InvoiceSheet(vm) { sheet = null }
        "extend" -> ExtendSheet(vm, customer) { sheet = null }
        "ip" -> StaticIpSheet(vm) { sheet = null }
        "archive" -> com.helper.beamnetworks.ui.screens.registers.ConfirmDialog(
            "Archive ${customer.name}?",
            "Their account is closed for good and they drop off the customer list. Use this only when a customer has left.",
            "Archive",
            onConfirm = { vm.archive(customer) },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun ProfileCard(customer: Customer) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BeamShapes.CardLarge)
            .background(BeamColors.Surface)
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InitialsAvatar(customer.name, customer.id, 64.dp, modifier = Modifier.padding(bottom = 8.dp))
        Text(customer.name, style = BeamType.DetailName, textAlign = TextAlign.Center)
        Text(customer.phone, style = BeamType.Meta)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(customer.status.label, customer.status.tone(), large = true)
            if (customer.planName.isNotBlank()) Tag(customer.planName, Tones.Blue, large = true)
            Tag(if (customer.online) "Online" else "Offline", if (customer.online) Tones.Green else Tones.Neutral, large = true)
        }
        BeamDivider(Modifier.padding(top = 16.dp))
        Text("Account balance", style = BeamType.Meta, modifier = Modifier.padding(top = 14.dp))
        Text(ksh(customer.displayBalance), style = BeamType.Hero.copy(color = if (customer.owing) BeamColors.Danger else BeamColors.Ink))
    }
}

private fun LazyListScope.overview(vm: CustomerDetailViewModel, customer: Customer, payments: List<Payment>, onRaiseTicket: (Int) -> Unit) {
    val s = vm.session
    if (s != null) {
        item {
            BeamCard(modifier = Modifier.padding(top = 8.dp), padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconTile(BeamIcons.Activity, Tones.Green, size = 36.dp, iconSize = 16.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Online now", style = BeamType.RowTitle)
                        Text(
                            listOfNotNull(s.framedIp, "up " + com.helper.beamnetworks.data.notifications.NotificationRules.duration(s.durationSeconds * 1000), bytes(s.bytesIn + s.bytesOut)).joinToString(" · "),
                            style = BeamType.Meta,
                        )
                    }
                    PillButton(if (vm.busy == "disconnect") "…" else "Disconnect", Tones.Red, BeamColors.DangerTint, vm::disconnect)
                }
            }
        }
    }
    item {
        val f = vm.full
        ListCard(Modifier.padding(top = 8.dp)) {
            val location = f?.let { listOfNotNull(it.houseNumber, it.building, it.street, it.estate, it.road, it.ward, it.subCounty).filter { v -> v.isNotBlank() }.joinToString(", ") }
                ?.ifBlank { null } ?: customer.location.ifBlank { null }
            val rows = listOfNotNull(
                customer.pppoeUser?.let { "PPPoE username" to it },
                f?.radiusPassword?.ifBlank { null }?.let { "PPPoE password" to it },
                customer.accountCode?.let { "M-Pesa account" to it },
                f?.staticIp?.ifBlank { null }?.let { "Static IP" to it },
                customer.email?.let { "Email" to it },
                customer.networkName?.let { "Network" to it },
                location?.let { "Location" to it },
                f?.commonMarker?.ifBlank { null }?.let { "Landmark" to it },
                parseDateTime(f?.serviceExpiresAt)?.let { "Paid until" to whenLabel(it) },
                parseDateTime(f?.pausedUntil)?.let { "Paused until" to whenLabel(it) },
                parseDateTime(f?.extensionExpiresAt)?.let { "Extension ends" to whenLabel(it) },
                "Last online" to lastOnlineLabel(customer.online, customer.lastSeen),
                parseDateTime(f?.createdAt)?.let { "Customer since" to shortDate(it.toLocalDate()) },
            )
            rows.forEachIndexed { i, (label, value) ->
                if (label == "PPPoE password") {
                    PasswordRow(value, showDivider = i < rows.lastIndex)
                } else {
                    InfoRow(label, value, showDivider = i < rows.lastIndex)
                }
            }
        }
    }
    item { SectionHeader("Recent payments") }
    item {
        ListCard {
            if (payments.isEmpty()) Text("No payments recorded yet.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
            payments.forEachIndexed { index, p ->
                if (index > 0) BeamDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(kshShort(p.amount), style = BeamType.RowTitle)
                        Text(paymentSource(p), style = BeamType.Meta)
                    }
                    Text(shortDate(p.date?.toLocalDate()), style = BeamType.Meta)
                }
            }
        }
    }
    item { SecondaryButton("Raise a ticket", { onRaiseTicket(customer.id) }, Modifier.padding(top = 12.dp)) }
}

@Composable
private fun PasswordRow(password: String, showDivider: Boolean) {
    var shown by rememberSaveable { mutableStateOf(false) }
    InfoRow("PPPoE password", if (shown) password else "••••••••  Show", showDivider = showDivider, onClick = { shown = !shown })
}

private fun LazyListScope.activityTab(events: List<ActivityEventDto>?) {
    item {
        ListCard(Modifier.padding(top = 8.dp)) {
            when {
                events == null -> Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
                events.isEmpty() -> Text("No activity yet.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
            }
            events.orEmpty().forEachIndexed { i, e ->
                if (i > 0) BeamDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (icon, tone) = activityStyle(e.type)
                    IconTile(icon, tone, size = 34.dp, iconSize = 15.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e.title?.ifBlank { null } ?: e.type.replaceFirstChar { it.uppercase() }, style = BeamType.RowTitle)
                        e.detail?.ifBlank { null }?.let { Text(it, style = BeamType.Meta) }
                    }
                    Text(relativeAge(parseDateTime(e.timestamp)), style = BeamType.Meta)
                }
            }
        }
    }
}

private fun activityStyle(type: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, TonePair> = when (type) {
    "payment" -> BeamIcons.Banknote to Tones.Green
    "online" -> BeamIcons.Activity to Tones.Green
    "offline" -> BeamIcons.Activity to Tones.Red
    "suspended" -> BeamIcons.PauseCircle to Tones.Red
    "reactivated", "resumed" -> BeamIcons.Check to Tones.Green
    "paused" -> BeamIcons.PauseCircle to Tones.Orange
    "extended" -> BeamIcons.Clock to Tones.Blue
    else -> BeamIcons.Clock to Tones.Neutral
}

private fun LazyListScope.billingTab(vm: CustomerDetailViewModel, context: Context, onNewInvoice: () -> Unit, onCredit: () -> Unit) {
    item {
        TwoColumns(
            { SecondaryButton("New invoice", onNewInvoice, bold = true) },
            { SecondaryButton("Add credit", onCredit, bold = true) },
            Modifier.padding(top = 8.dp),
        )
    }
    vm.wallet?.let { w ->
        item {
            BeamCard(modifier = Modifier.padding(top = 8.dp), padding = PaddingValues(14.dp)) {
                Text("Wallet", style = BeamType.Meta)
                Text(ksh(parseMoney(w.balance)), style = BeamType.Stat)
                w.transactions.take(5).forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(t.text("description", "note", "kind", "type", "transaction_type") ?: "Transaction", style = BeamType.Small, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.text("amount")?.let { kshShort(parseMoney(it)) } ?: "", style = BeamType.Amount)
                    }
                }
            }
        }
    }
    item { SectionHeader("Invoices") }
    item {
        ListCard {
            val invoices = vm.invoices
            when {
                invoices == null -> Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
                invoices.isEmpty() -> Text("No invoices yet.", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(vertical = 14.dp))
            }
            invoices.orEmpty().forEachIndexed { i, inv ->
                if (i > 0) BeamDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.invoiceNumber.ifBlank { "Invoice ${inv.id}" }, style = BeamType.RowTitle)
                            Text(
                                listOfNotNull(
                                    inv.lineItems.firstOrNull()?.description,
                                    inv.dueDate?.let { "due " + shortDate(runCatching { LocalDate.parse(it) }.getOrNull()) },
                                ).joinToString(" · "),
                                style = BeamType.Meta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(kshShort(parseMoney(inv.total)), style = BeamType.RowTitle)
                            invoiceTone(inv.status).let { (label, tone) -> Tag(label, tone) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(if (vm.busy == "pdf-${inv.id}") "…" else "Share PDF", Tones.Blue, BeamColors.PrimaryTintPressed, { vm.shareInvoice(context, inv) })
                        if (inv.status == "draft" || inv.status == "overdue" || inv.status == "sent") {
                            PillButton(if (vm.busy == "send-${inv.id}") "…" else "Send to customer", Tones.Neutral.copy(content = BeamColors.Ink), BeamColors.PressedNeutral, { vm.sendInvoice(inv) }, bold = false)
                        }
                    }
                }
            }
        }
    }
    vm.credits?.takeIf { it.isNotEmpty() }?.let { credits ->
        item { SectionHeader("Credit notes") }
        item {
            ListCard {
                credits.forEachIndexed { i, c ->
                    InfoRow(c.reason.ifBlank { "Credit" }, kshShort(parseMoney(c.amount)) + (c.remaining?.let { " · ${kshShort(parseMoney(it))} left" } ?: ""), showDivider = i < credits.lastIndex)
                }
            }
        }
    }
}

private fun invoiceTone(status: String): Pair<String, TonePair> = when (status) {
    "paid" -> "Paid" to Tones.Green
    "overdue" -> "Overdue" to Tones.Red
    "sent" -> "Sent" to Tones.Blue
    "draft" -> "Draft" to Tones.Neutral
    "waived" -> "Waived" to Tones.Purple
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

private fun LazyListScope.usageTab(usage: List<UsagePointDto>?) {
    item {
        BeamCard(modifier = Modifier.padding(top = 8.dp), shape = BeamShapes.CardLarge, padding = PaddingValues(16.dp)) {
            if (usage == null) {
                Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted))
                return@BeamCard
            }
            val total = usage.sumOf { it.bytes }
            Text("Last 30 days", style = BeamType.Meta)
            Text(bytes(total), style = BeamType.Stat)
            val max = (usage.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                usage.forEach { p ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((p.bytes.toDouble() / max * 110).dp.coerceAtLeast(2.dp))
                                .background(if (p.bytes > 0) BeamColors.Primary else BeamColors.Divider, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(usage.firstOrNull()?.date?.let { shortDate(runCatching { LocalDate.parse(it) }.getOrNull()) }.orEmpty(), style = BeamType.Meta, modifier = Modifier.weight(1f))
                Text("Today", style = BeamType.Meta)
            }
            val busiest = usage.maxByOrNull { it.bytes }
            if (busiest != null && busiest.bytes > 0) {
                Text("Busiest day: ${shortDate(runCatching { LocalDate.parse(busiest.date) }.getOrNull())} · ${bytes(busiest.bytes)}", style = BeamType.Meta, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

fun bytes(value: Long): String {
    val gb = value / 1_000_000_000.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        value >= 1_000_000 -> String.format(Locale.US, "%.0f MB", value / 1_000_000.0)
        else -> String.format(Locale.US, "%.0f KB", value / 1_000.0)
    }
}

private fun JsonObject.text(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.content?.takeIf { v -> v.isNotBlank() && v != "null" } }

// ---- Sheets ----

@Composable
private fun MoreSheet(vm: CustomerDetailViewModel, customer: Customer, onClose: () -> Unit, open: (String) -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    BeamSheet("Manage ${customer.name.substringBefore(' ')}", onClose, toast) {
        Column {
            NavRow(BeamIcons.Package, Tones.Blue, "Change plan", "Prorated automatically", { open("plan") }, tileSize = 38.dp, inCard = true)
            BeamDivider()
            if (customer.status == CustomerStatus.Paused) {
                NavRow(BeamIcons.Check, Tones.Green, "Resume service", "End the pause now", { vm.resume(); onClose() }, tileSize = 38.dp, inCard = true)
            } else {
                NavRow(BeamIcons.PauseCircle, Tones.Orange, "Pause service", "Customer travelling or away", { open("pause") }, tileSize = 38.dp, inCard = true)
            }
            BeamDivider()
            NavRow(BeamIcons.MessageSquare, Tones.Purple, "Resend portal login", "Texts a new password for the customer portal", { vm.resendPortal(); onClose() }, tileSize = 38.dp, inCard = true)
            BeamDivider()
            NavRow(BeamIcons.Pencil, Tones.Teal, "Edit details", "Name, phone, email, location", { open("edit") }, tileSize = 38.dp, inCard = true)
            BeamDivider()
            NavRow(BeamIcons.Banknote, Tones.Green, "Add credit", "Goodwill or outage credit", { open("credit") }, tileSize = 38.dp, inCard = true)
            BeamDivider()
            NavRow(BeamIcons.Ticket, Tones.Red, "New invoice", "One-off charge, e.g. router or relocation", { open("invoice") }, tileSize = 38.dp, inCard = true)
            if (customer.status != CustomerStatus.Paused && customer.status != CustomerStatus.Archived) {
                BeamDivider()
                NavRow(BeamIcons.Clock, Tones.Blue, "Extend service", "Extra days or weeks, or until a date", { open("extend") }, tileSize = 38.dp, inCard = true)
            }
            BeamDivider()
            NavRow(
                BeamIcons.Link, Tones.Teal, "Static IP",
                vm.full?.staticIp?.ifBlank { null }?.let { "Has $it" } ?: "Give this customer a fixed IP",
                { open("ip") }, tileSize = 38.dp, inCard = true,
            )
            if (customer.status != CustomerStatus.Archived) {
                BeamDivider()
                NavRow(BeamIcons.Archive, Tones.Red, "Archive customer", "Customer has left; this can't be undone", { open("archive") }, tileSize = 38.dp, inCard = true)
            }
        }
    }
}

@Composable
private fun ExtendSheet(vm: CustomerDetailViewModel, customer: Customer, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val units = listOf("days" to "Days", "weeks" to "Weeks", "until" to "Until a date")
    var unit by rememberSaveable { mutableStateOf("days") }
    var count by rememberSaveable { mutableStateOf("7") }
    var until by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var reason by rememberSaveable { mutableStateOf("") }
    BeamSheet("Extend service", onClose, toast, ctaLabel = if (vm.busy == "extend") "Saving…" else "Extend", onCta = {
        val n = count.toIntOrNull()
        val date = runCatching { LocalDate.parse(until) }.getOrNull()
        when {
            reason.trim().length < 5 -> vm.toasts.show("Give a reason (at least 5 characters)")
            unit == "days" && (n == null || n !in 1..90) -> vm.toasts.show("Days must be between 1 and 90")
            unit == "weeks" && (n == null || n !in 1..52) -> vm.toasts.show("Weeks must be between 1 and 52")
            unit == "until" && (date == null || !date.isAfter(LocalDate.now())) -> vm.toasts.show("Pick a date after today")
            else -> vm.extendService(customer, unit, n ?: 0, date ?: LocalDate.now(), reason.trim(), onClose)
        }
    }, ctaEnabled = vm.busy == null) {
        Text("Keeps the customer connected without a payment. They're suspended again when it ends unless they pay.", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
        PillTabs(options = units.map { PillTabOption(it.first, it.second) }, selected = unit, onSelect = { unit = it })
        if (unit == "until") {
            com.helper.beamnetworks.ui.components.DateField("Until", runCatching { LocalDate.parse(until) }.getOrDefault(LocalDate.now()), { until = it.toString() })
        } else {
            FormField(if (unit == "weeks") "Weeks" else "Days", count, { count = it.filter(Char::isDigit).take(2) }, keyboardType = KeyboardType.Number)
        }
        FormField("Reason", reason, { reason = it }, placeholder = "e.g. Promised to pay on Friday")
    }
}

@Composable
private fun StaticIpSheet(vm: CustomerDetailViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val current = vm.full?.staticIp?.ifBlank { null }
    var address by rememberSaveable { mutableStateOf("") }
    BeamSheet("Static IP", onClose, toast, ctaLabel = if (vm.busy == "ip") "Saving…" else if (current == null) "Assign static IP" else "Change IP", onCta = {
        vm.setStaticIp(true, address, onClose)
    }, ctaEnabled = vm.busy == null) {
        Text(current?.let { "Currently $it." } ?: "No static IP yet.", style = BeamType.Body)
        FormField("Address (optional)", address, { address = it.filter { c -> c.isDigit() || c == '.' } }, placeholder = "Leave blank to pick the next free one", keyboardType = KeyboardType.Decimal)
        Text("The customer's session is updated; they may need to reconnect.", style = BeamType.Meta)
        if (current != null) {
            SecondaryButton(if (vm.busy == "ip") "…" else "Release static IP", { vm.setStaticIp(false, "", onClose) }, textColor = BeamColors.Danger, bold = true)
        }
    }
}

@Composable
private fun PlanSheet(vm: CustomerDetailViewModel, customer: Customer, onClose: () -> Unit) {
    val plans by vm.plans.collectAsStateWithLifecycle()
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var planId by rememberSaveable { mutableStateOf(customer.planId) }
    BeamSheet("Change plan", onClose, toast, ctaLabel = if (vm.busy == "plan") "Saving…" else "Change plan", onCta = { planId?.let { vm.changePlan(it, onClose) } }, ctaEnabled = vm.busy == null && planId != customer.planId) {
        DropdownField("New plan", plans.items.firstOrNull { it.id == planId }?.name ?: customer.planName, plans.items.map { it.name }, { name -> planId = plans.items.firstOrNull { it.name == name }?.id })
        Text("The customer is charged or credited for the rest of the cycle, and their speed changes right away.", style = BeamType.Meta)
    }
}

@Composable
private fun PauseSheet(vm: CustomerDetailViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableStateOf("14") }
    BeamSheet("Pause service", onClose, toast, ctaLabel = if (vm.busy == "pause") "Pausing…" else "Pause", onCta = {
        val d = days.toIntOrNull()
        if (d == null || d < 1) vm.toasts.show("Enter the number of days") else vm.pause(d, onClose)
    }, ctaEnabled = vm.busy == null) {
        FormField("Days", days, { days = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number)
        Text("Billing stops while paused and service resumes automatically. Your Beam settings may set a minimum pause.", style = BeamType.Meta)
    }
}

@Composable
private fun EditSheet(vm: CustomerDetailViewModel, customer: Customer, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val f = vm.full
    val loaded = f != null
    var name by rememberSaveable { mutableStateOf(customer.name) }
    var phone by rememberSaveable { mutableStateOf(customer.phone) }
    var email by rememberSaveable { mutableStateOf(customer.email.orEmpty()) }
    // Location and billing are seeded from the full record, so they re-seed once it has loaded
    var billed by rememberSaveable(loaded) { mutableStateOf(f?.isBilled ?: true) }
    var country by rememberSaveable(loaded) { mutableStateOf(f?.country.orEmpty()) }
    var county by rememberSaveable(loaded) { mutableStateOf(f?.city.orEmpty()) }
    var subCounty by rememberSaveable(loaded) { mutableStateOf(f?.subCounty.orEmpty()) }
    var ward by rememberSaveable(loaded) { mutableStateOf(f?.ward.orEmpty()) }
    var road by rememberSaveable(loaded) { mutableStateOf(f?.road.orEmpty()) }
    var estate by rememberSaveable(loaded) { mutableStateOf(f?.estate.orEmpty()) }
    var street by rememberSaveable(loaded) { mutableStateOf(f?.street.orEmpty()) }
    var building by rememberSaveable(loaded) { mutableStateOf(f?.building.orEmpty()) }
    var house by rememberSaveable(loaded) { mutableStateOf(f?.houseNumber.orEmpty()) }
    var landmark by rememberSaveable(loaded) { mutableStateOf(f?.commonMarker.orEmpty()) }
    var details by rememberSaveable(loaded) { mutableStateOf(f?.moreDetails.orEmpty()) }
    BeamSheet("Edit details", onClose, toast, ctaLabel = if (vm.busy == "edit") "Saving…" else "Save", onCta = {
        if (name.isBlank() || phone.isBlank()) vm.toasts.show("Name and phone are required")
        // Location and billing are only sent once the full record has loaded, so blanks never overwrite real values
        else if (f == null) vm.update(CustomerUpdate(fullName = name.trim(), phone = phone.trim(), email = email.trim()), onClose)
        else vm.update(
            CustomerUpdate(
                fullName = name.trim(), phone = phone.trim(), email = email.trim(),
                country = country.trim(), city = county.trim(), subCounty = subCounty.trim(), ward = ward.trim(),
                road = road.trim(), estate = estate.trim(), street = street.trim(), building = building.trim(),
                houseNumber = house.trim(), commonMarker = landmark.trim(), moreDetails = details.trim(),
                isBilled = billed,
            ),
            onClose,
        )
    }, ctaEnabled = vm.busy == null) {
        FormField("Full name", name, { name = it })
        FormField("Phone", phone, { phone = it }, keyboardType = KeyboardType.Phone)
        FormField("Email", email, { email = it }, keyboardType = KeyboardType.Email)
        if (f != null) {
            SwitchRow("Billed account", billed) { billed = it }
            Text("Location", style = BeamType.SectionTitle, modifier = Modifier.padding(top = 6.dp))
            TwoColumns({ FormField("Country", country, { country = it }) }, { FormField("County", county, { county = it }) })
            TwoColumns({ FormField("Sub-county", subCounty, { subCounty = it }) }, { FormField("Ward", ward, { ward = it }) })
            TwoColumns({ FormField("Road", road, { road = it }) }, { FormField("Estate", estate, { estate = it }) })
            TwoColumns({ FormField("Street", street, { street = it }) }, { FormField("Building", building, { building = it }) })
            TwoColumns({ FormField("House / unit no.", house, { house = it }) }, { FormField("Another common marker", landmark, { landmark = it }) })
            FormField("More details", details, { details = it }, singleLine = false)
        } else {
            Text("Location and billing details couldn't be loaded, so they're left unchanged.", style = BeamType.Meta)
        }
    }
}

@Composable
private fun CreditSheet(vm: CustomerDetailViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    BeamSheet("Add credit", onClose, toast, ctaLabel = if (vm.busy == "credit") "Saving…" else "Add credit", onCta = {
        val a = parseAmountInput(amount)
        when {
            a == null -> vm.toasts.show("Enter an amount")
            reason.isBlank() -> vm.toasts.show("Give a reason")
            else -> vm.creditNote(a, reason, onClose)
        }
    }, ctaEnabled = vm.busy == null) {
        FormField("Amount (Ksh)", amount, { amount = it }, keyboardType = KeyboardType.Decimal, large = true, placeholder = "0.00")
        FormField("Reason", reason, { reason = it }, placeholder = "e.g. Goodwill for outage on 24 Oct")
        Text("Credit is used against their next invoices automatically.", style = BeamType.Meta)
    }
}

@Composable
private fun InvoiceSheet(vm: CustomerDetailViewModel, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var description by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var due by rememberSaveable { mutableStateOf("7 days") }
    BeamSheet("New invoice", onClose, toast, ctaLabel = if (vm.busy == "invoice") "Raising…" else "Raise invoice", onCta = {
        val a = parseAmountInput(amount)
        when {
            description.isBlank() -> vm.toasts.show("Describe the charge")
            a == null -> vm.toasts.show("Enter an amount")
            (quantity.toIntOrNull() ?: 0) < 1 -> vm.toasts.show("Quantity must be at least 1")
            else -> vm.invoice(description, a, quantity.toInt(), due.substringBefore(' ').toInt(), onClose)
        }
    }, ctaEnabled = vm.busy == null) {
        FormField("What for", description, { description = it }, placeholder = "e.g. Router installation")
        TwoColumns(
            { FormField("Price each (Ksh)", amount, { amount = it }, keyboardType = KeyboardType.Decimal, placeholder = "0.00") },
            { FormField("Quantity", quantity, { quantity = it.filter(Char::isDigit).take(3) }, keyboardType = KeyboardType.Number) },
        )
        DropdownField("Due in", due, listOf("0 days", "3 days", "7 days", "14 days", "30 days"), { due = it })
    }
}


fun paymentSource(p: Payment): String {
    val method = when (p.method) {
        "mpesa" -> "M‑Pesa"
        "cash" -> "Cash"
        "card" -> "Card"
        else -> p.method.replaceFirstChar { it.uppercase() }
    }
    return listOfNotNull(method.ifBlank { null }, p.reference).joinToString(" · ")
}
