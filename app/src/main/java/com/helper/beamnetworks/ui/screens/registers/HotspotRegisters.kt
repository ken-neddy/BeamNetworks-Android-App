package com.helper.beamnetworks.ui.screens.registers

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.HotspotCustomerDto
import com.helper.beamnetworks.data.api.HotspotSessionDto
import com.helper.beamnetworks.data.api.Page
import com.helper.beamnetworks.data.api.PlanDto
import com.helper.beamnetworks.data.api.VoucherDto
import com.helper.beamnetworks.data.api.VoucherIssueRequest
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.DropdownField
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.InitialsAvatar
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PillTabs
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SwitchRow
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.screens.customers.bytes
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseMoney
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.whenLabel
import kotlinx.serialization.Serializable

@Serializable data object HotspotRoute

enum class HotspotTab(val label: String) { Customers("Customers"), Vouchers("Vouchers"), Live("Live now") }

/** Hotspot (prepaid Wi‑Fi) customers, vouchers and live sessions on one screen. */
@Composable
fun HotspotScreen() {
    var tab by rememberSaveable { mutableStateOf(HotspotTab.Customers) }
    var issuing by remember { mutableStateOf(false) }
    val vouchers = containerViewModel(key = "hotspot-vouchers") { app, _ -> VouchersViewModel(app) }
    Column {
        PillTabs(
            options = HotspotTab.entries.map { PillTabOption(it, it.label) },
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        when (tab) {
            HotspotTab.Customers -> HotspotCustomersList()
            HotspotTab.Vouchers -> VouchersList(vouchers) { issuing = true }
            HotspotTab.Live -> HotspotSessionsList()
        }
    }
    if (issuing) IssueVoucherSheet(vouchers) { issuing = false }
}

// ---- Customers ----

enum class HotspotFilter(val label: String) { All("All"), Active("Active"), Online("Online now"), Suspended("Suspended") }

class HotspotCustomersViewModel(app: AppContainer) : RegisterViewModel<HotspotCustomerDto, HotspotFilter>(app, HotspotFilter.All) {
    override suspend fun fetch(page: Int, query: String, filter: HotspotFilter): Page<HotspotCustomerDto> {
        val result = app.api.hotspotCustomers(page, query.ifBlank { null })
        val filtered = result.results.filter {
            when (filter) {
                HotspotFilter.All -> true
                HotspotFilter.Active -> it.status == "active"
                HotspotFilter.Online -> it.sessionStatus == "active"
                HotspotFilter.Suspended -> it.status == "suspended"
            }
        }
        return result.copy(results = filtered)
    }

    fun suspend(c: HotspotCustomerDto, onDone: () -> Unit) = act("status", "${c.fullName} suspended") {
        replace({ it.id == c.id }, apiCall { app.api.suspendHotspot(c.id) })
        onDone()
    }

    fun reactivate(c: HotspotCustomerDto, onDone: () -> Unit) = act("status", "${c.fullName} reactivated") {
        replace({ it.id == c.id }, apiCall { app.api.reactivateHotspot(c.id) })
        onDone()
    }

    fun resend(c: HotspotCustomerDto) = act("resend", "Login details texted to ${c.phone}") { apiCall { app.api.resendHotspotCredentials(c.id) } }
}

@Composable
private fun HotspotCustomersList() {
    val vm = containerViewModel(key = "hotspot-customers") { app, _ -> HotspotCustomersViewModel(app) }
    var open by remember { mutableStateOf<HotspotCustomerDto?>(null) }
    RegisterList(
        vm = vm,
        filters = HotspotFilter.entries.map { PillTabOption(it, it.label) },
        searchHint = "Search name or phone",
        emptyTitle = "No hotspot customers",
        emptyMessage = "Customers appear here after they buy Wi‑Fi access.",
        key = { it.id },
    ) { c ->
        BeamCard(onClick = { open = c }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialsAvatar(c.fullName.ifBlank { c.phone }, c.id, 40.dp, online = c.sessionStatus == "active")
                Column(Modifier.weight(1f)) {
                    Text(c.fullName.ifBlank { c.phone }, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(c.planName, sessionLabel(c)).joinToString(" · "), style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                statusTone(c.status).let { (label, tone) -> Tag(label, tone) }
            }
        }
    }
    open?.let { c -> HotspotCustomerSheet(vm, vm.items.firstOrNull { it.id == c.id } ?: c) { open = null } }
}

private fun sessionLabel(c: HotspotCustomerDto): String? = when (c.sessionStatus) {
    "active" -> "Online, ends " + whenLabel(parseDateTime(c.sessionExpiresAt))
    "expired" -> "Last used " + relativeAge(parseDateTime(c.sessionLastSeenAt ?: c.sessionExpiresAt))
    "unused" -> "Paid but never connected"
    else -> null
}

@Composable
private fun HotspotCustomerSheet(vm: HotspotCustomersViewModel, c: HotspotCustomerDto, onClose: () -> Unit) {
    val toast by vm.app.toasts.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BeamSheet(c.fullName.ifBlank { c.phone }, onClose, toast) {
        ListCard {
            val rows = listOfNotNull(
                "Phone" to c.phone,
                "Status" to statusTone(c.status).first,
                c.planName?.let { "Plan" to it },
                c.networkName?.let { "Network" to it },
                c.voucherCode?.ifBlank { null }?.let { "Access code" to it },
                c.macAddress?.ifBlank { null }?.let { "Device (MAC)" to it },
                sessionLabel(c)?.let { "Session" to it },
                c.sessionBytesUsed?.takeIf { it > 0 }?.let { "Data used" to bytes(it) },
                parseDateTime(c.createdAt)?.let { "First seen" to whenLabel(it) },
            )
            rows.forEachIndexed { i, (l, v) -> InfoRow(l, v, showDivider = i < rows.lastIndex) }
        }
        SecondaryButton("Call", { context.startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${c.phone}"))) }, bold = true)
        SecondaryButton(if (vm.busy == "resend") "Sending…" else "Text login / access code", { vm.resend(c) }, bold = true)
        if (c.status == "active") {
            SecondaryButton(if (vm.busy == "status") "…" else "Suspend", { vm.suspend(c, onClose) }, textColor = BeamColors.Danger, bold = true)
        } else if (c.status == "suspended" || c.status == "overdue") {
            SecondaryButton(if (vm.busy == "status") "…" else "Reactivate", { vm.reactivate(c, onClose) }, textColor = BeamColors.Success, bold = true)
        }
    }
}

private fun statusTone(status: String): Pair<String, TonePair> = when (status) {
    "active" -> "Active" to Tones.Green
    "suspended" -> "Suspended" to Tones.Red
    "overdue" -> "Overdue" to Tones.Orange
    "archived" -> "Archived" to Tones.Neutral
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

// ---- Vouchers ----

enum class VoucherFilter(val label: String, val status: String?) {
    All("All", null), Active("Active", "active"), Pending("Not used", "pending"), Redeemed("Used", "redeemed"), Expired("Expired", "expired"),
}

class VouchersViewModel(app: AppContainer) : RegisterViewModel<VoucherDto, VoucherFilter>(app, VoucherFilter.All) {
    var plans by mutableStateOf<List<PlanDto>>(emptyList())
        private set
    var issued by mutableStateOf<VoucherDto?>(null)
        private set

    override suspend fun fetch(page: Int, query: String, filter: VoucherFilter): Page<VoucherDto> {
        val result = app.api.vouchers(page, query.ifBlank { null })
        return if (filter.status == null) result else result.copy(results = result.results.filter { it.status == filter.status })
    }

    fun loadPlans() = act("plans", null) { plans = apiCall { app.api.hotspotPlans() }.results.filter { it.isActive } }

    fun issue(planId: Int, phone: String, billed: Boolean) = act("issue", null) {
        val voucher = apiCall { app.api.issueVoucher(VoucherIssueRequest(planId, phone.trim(), billed)) }
        issued = voucher
        prepend(voucher)
        app.toasts.show("Voucher ${voucher.code} issued")
        if (billed) app.payments.payments.refreshAsync()
    }

    fun clearIssued() {
        issued = null
    }

    fun resend(v: VoucherDto) = act("resend-${v.id}", "Voucher texted to ${v.customerPhone.orEmpty()}") { apiCall { app.api.resendVoucher(v.id) } }
}

@Composable
private fun VouchersList(vm: VouchersViewModel, onIssue: () -> Unit) {
    RegisterList(
        vm = vm,
        filters = VoucherFilter.entries.map { PillTabOption(it, it.label) },
        searchHint = "Search code or phone",
        emptyTitle = "No vouchers",
        emptyMessage = "Issue one with the button above.",
        key = { it.id },
        header = { item(key = "issue") { PrimaryButton("Issue a voucher", onIssue, height = 48.dp) } },
    ) { v ->
        BeamCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(v.code.ifBlank { v.voucherId }, style = BeamType.RowTitle)
                    Text(
                        listOfNotNull(v.planName, v.customerPhone, if (v.billed) v.price?.let { kshShort(parseMoney(it)) } else "Free").joinToString(" · "),
                        style = BeamType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    voucherTone(v.status).let { (label, tone) -> Tag(label, tone) }
                    if (!v.customerPhone.isNullOrBlank() && v.status != "redeemed" && v.status != "expired") {
                        PillButton(if (vm.busy == "resend-${v.id}") "…" else "Resend", Tones.Blue, BeamColors.PrimaryTintPressed, { vm.resend(v) })
                    }
                }
            }
        }
    }
}

private fun voucherTone(status: String): Pair<String, TonePair> = when (status) {
    "active" -> "Active" to Tones.Green
    "pending" -> "Not used" to Tones.Blue
    "redeemed" -> "Used" to Tones.Neutral
    "expired" -> "Expired" to Tones.Orange
    "cancelled" -> "Cancelled" to Tones.Red
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

@Composable
private fun IssueVoucherSheet(vm: VouchersViewModel, onClose: () -> Unit) {
    val toast by vm.app.toasts.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var planId by remember { mutableStateOf<Int?>(null) }
    var phone by remember { mutableStateOf("") }
    var billed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        vm.clearIssued()
        vm.loadPlans()
    }
    val issued = vm.issued
    if (issued != null) {
        BeamSheet("Voucher issued", { vm.clearIssued(); onClose() }, toast) {
            Text(issued.code, style = BeamType.Hero)
            Text("${issued.planName.orEmpty()} · texted to ${issued.customerPhone.orEmpty()}", style = BeamType.Meta)
            SecondaryButton("Share code", {
                val text = "Your Beam Networks Wi‑Fi code: ${issued.code} (${issued.planName.orEmpty()})"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share voucher"))
            }, bold = true)
        }
        return
    }
    BeamSheet(
        "Issue a voucher", onClose, toast,
        ctaLabel = if (vm.busy == "issue") "Issuing…" else "Issue voucher",
        onCta = {
            val plan = planId
            when {
                plan == null -> vm.app.toasts.show("Choose a plan")
                phone.isBlank() -> vm.app.toasts.show("Enter the customer's phone")
                else -> vm.issue(plan, phone, billed)
            }
        },
        ctaEnabled = vm.busy == null,
    ) {
        DropdownField(
            "Plan",
            vm.plans.firstOrNull { it.id == planId }?.let { "${it.name} · ${kshShort(parseMoney(it.price))}" }.orEmpty(),
            if (vm.plans.isEmpty()) listOf("Loading plans…") else vm.plans.map { "${it.name} · ${kshShort(parseMoney(it.price))}" },
            { label -> planId = vm.plans.firstOrNull { "${it.name} · ${kshShort(parseMoney(it.price))}" == label }?.id },
        )
        FormField("Customer phone", phone, { phone = it }, placeholder = "07…", keyboardType = KeyboardType.Phone)
        SwitchRow("Customer paid cash for it", billed) { billed = it }
        Text(if (billed) "Records a cash payment for the plan price." else "Free (complimentary) voucher; no payment is recorded.", style = BeamType.Meta)
    }
}

// ---- Live sessions ----

class HotspotSessionsViewModel(app: AppContainer) : RegisterViewModel<HotspotSessionDto, Unit>(app, Unit) {
    override suspend fun fetch(page: Int, query: String, filter: Unit): Page<HotspotSessionDto> {
        val sessions = app.api.hotspotSessions()
        return Page(sessions.size, null, sessions)
    }

    fun terminate(s: HotspotSessionDto) = act("end-${s.id}", "Session ended") {
        apiCall { app.api.terminateHotspotSession(s.id) }
        reload()
    }
}

@Composable
private fun HotspotSessionsList() {
    val vm = containerViewModel(key = "hotspot-sessions") { app, _ -> HotspotSessionsViewModel(app) }
    var ending by remember { mutableStateOf<HotspotSessionDto?>(null) }
    RegisterList(
        vm = vm,
        filters = emptyList(),
        searchHint = null,
        emptyTitle = "Nobody on the hotspot",
        emptyMessage = "Active Wi‑Fi sessions show here.",
        key = { it.id },
    ) { s ->
        BeamCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(s.customerName?.ifBlank { null } ?: s.customerPhone ?: "Device ${s.macAddress.orEmpty()}", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(s.planName, bytes(s.bytesUsed), "ends " + whenLabel(parseDateTime(s.expiresAt))).joinToString(" · "), style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PillButton(if (vm.busy == "end-${s.id}") "…" else "End", Tones.Red, BeamColors.DangerTint, { ending = s })
            }
        }
    }
    ending?.let { s ->
        ConfirmDialog("End this session?", "The device is cut off in Beam. The router enforces it on its next check.", "End session", { vm.terminate(s) }, { ending = null })
    }
}
