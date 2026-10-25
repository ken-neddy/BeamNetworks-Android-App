package com.helper.beamnetworks.ui.screens.registers

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.InvoiceFullDto
import com.helper.beamnetworks.data.api.NoteRequest
import com.helper.beamnetworks.data.api.Page
import com.helper.beamnetworks.data.api.PaymentRecordDto
import com.helper.beamnetworks.data.api.VerifyPaymentRequest
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseMoney
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.shortDate
import com.helper.beamnetworks.util.whenLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

@Serializable data object PaymentsRoute
@Serializable data object InvoicesRoute

// ---- Payments ----

enum class PaymentFilter(val label: String, val status: String?) {
    Confirmed("Confirmed", "confirmed"),
    Pending("Pending", "pending"),
    Failed("Failed", "failed"),
    Refunded("Refunded", "refunded"),
    All("All", null),
}

class PaymentsViewModel(app: AppContainer) : RegisterViewModel<PaymentRecordDto, PaymentFilter>(app, PaymentFilter.Confirmed) {
    val isAdmin get() = app.sessions.current?.isAdmin == true

    override suspend fun fetch(page: Int, query: String, filter: PaymentFilter): Page<PaymentRecordDto> =
        app.api.paymentRecords(page = page, status = filter.status, search = query.ifBlank { null })

    fun refund(p: PaymentRecordDto, note: String, onDone: () -> Unit) = act("refund", "Refund started") {
        val updated = apiCall { app.api.refundPayment(p.id, NoteRequest(note.ifBlank { null })) }
        replace({ it.id == p.id }, updated)
        app.payments.payments.refreshAsync()
        app.customers.customers.refreshAsync()
        onDone()
    }

    fun retry(p: PaymentRecordDto, onDone: () -> Unit) = act("retry", "M‑Pesa prompt sent to ${p.mpesaPhone.orEmpty()}") {
        val updated = apiCall { app.api.retryPayment(p.id) }
        replace({ it.id == p.id }, updated)
        onDone()
    }

    fun verify(p: PaymentRecordDto, code: String, onDone: () -> Unit) = act("verify", null) {
        val result = apiCall { app.api.verifyPayment(p.id, VerifyPaymentRequest(code.trim().uppercase().ifBlank { null })) }
        app.toasts.show(if (result.status == "confirmed") "Payment confirmed" else "Still ${result.status}")
        reload()
        if (result.status == "confirmed") {
            app.payments.payments.refreshAsync()
            app.customers.customers.refreshAsync()
        }
        onDone()
    }
}

@Composable
fun PaymentsScreen(onCustomer: (Int) -> Unit) {
    val vm = containerViewModel(key = "payments-register") { app, _ -> PaymentsViewModel(app) }
    var open by remember { mutableStateOf<PaymentRecordDto?>(null) }
    RegisterList(
        vm = vm,
        filters = PaymentFilter.entries.map { PillTabOption(it, it.label) },
        searchHint = "Search name, phone or M‑Pesa code",
        emptyTitle = "No payments",
        emptyMessage = "Nothing matches this filter.",
        key = { it.id },
    ) { p ->
        BeamCard(onClick = { open = p }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(p.customerName?.ifBlank { null } ?: p.mpesaPhone ?: "Unknown", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(methodLabel(p.method, p.customerType), p.mpesaTransactionId?.ifBlank { null } ?: p.reference?.ifBlank { null }).joinToString(" · "),
                        style = BeamType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(kshShort(parseMoney(p.amount)), style = BeamType.Amount)
                    Text(shortDate(parseDateTime(p.confirmedAt ?: p.createdAt)?.toLocalDate()), style = BeamType.Meta)
                }
            }
            if (vm.filter == PaymentFilter.All || p.status != vm.filter.status) {
                paymentTone(p.status).let { (label, tone) -> Tag(label, tone, Modifier.padding(top = 6.dp)) }
            }
        }
    }
    open?.let { p -> PaymentSheet(vm, p, onCustomer = { id -> open = null; onCustomer(id) }) { open = null } }
}

@Composable
private fun PaymentSheet(vm: PaymentsViewModel, p: PaymentRecordDto, onCustomer: (Int) -> Unit, onClose: () -> Unit) {
    val toast by vm.app.toasts.message.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var refundNote by remember { mutableStateOf("") }
    var confirmRefund by remember { mutableStateOf(false) }
    BeamSheet("Payment ${kshShort(parseMoney(p.amount))}", onClose, toast) {
        ListCard {
            val rows = listOfNotNull(
                "Status" to paymentTone(p.status).first,
                "Customer" to (p.customerName?.ifBlank { null } ?: "—"),
                "Type" to (methodLabel(p.method, p.customerType) ?: "—"),
                p.mpesaTransactionId?.ifBlank { null }?.let { "M‑Pesa code" to it },
                p.mpesaPhone?.ifBlank { null }?.let { "Phone" to it },
                p.reference?.ifBlank { null }?.let { "Reference" to it },
                p.recordedByName?.ifBlank { null }?.let { "Recorded by" to it },
                p.note?.ifBlank { null }?.let { "Note" to it },
                p.failureReason?.ifBlank { null }?.let { "Failure" to it },
                "Date" to whenLabel(parseDateTime(p.confirmedAt ?: p.createdAt)),
                parseDateTime(p.refundedAt)?.let { "Refunded" to whenLabel(it) },
            )
            rows.forEachIndexed { i, (label, value) -> InfoRow(label, value, showDivider = i < rows.lastIndex) }
        }
        p.pppoeCustomer?.let { id -> SecondaryButton("Open customer", { onCustomer(id) }, bold = true) }
        if (p.method == "mpesa" && (p.status == "pending" || p.status == "failed")) {
            FormField("M‑Pesa code from the customer's SMS (optional)", code, { code = it.uppercase() }, placeholder = "e.g. SJK4X1P9QA")
            SecondaryButton(if (vm.busy == "verify") "Checking…" else "Check / confirm payment", { vm.verify(p, code, onClose) }, bold = true)
            SecondaryButton(if (vm.busy == "retry") "Sending…" else "Send M‑Pesa prompt again", { vm.retry(p, onClose) }, bold = true)
        }
        if (vm.isAdmin && p.status == "confirmed") {
            FormField("Refund note", refundNote, { refundNote = it }, placeholder = "Why it's being refunded")
            SecondaryButton(if (vm.busy == "refund") "Refunding…" else "Refund payment", { confirmRefund = true }, textColor = BeamColors.Danger, bold = true)
            Text("M‑Pesa refunds go through Safaricom's reversal and finish when they confirm. Cash refunds are immediate.", style = BeamType.Meta)
        }
    }
    if (confirmRefund) {
        ConfirmDialog(
            "Refund ${kshShort(parseMoney(p.amount))}?",
            "This reverses the payment and the customer's balance goes back up.",
            "Refund",
            onConfirm = { vm.refund(p, refundNote, onClose) },
            onDismiss = { confirmRefund = false },
        )
    }
}

private fun methodLabel(method: String?, type: String?): String? {
    val m = when (method) {
        "mpesa" -> "M‑Pesa"
        "cash" -> "Cash"
        "card" -> "Card"
        null, "" -> null
        else -> method.replaceFirstChar { it.uppercase() }
    }
    return listOfNotNull(m, if (type == "hotspot") "Hotspot" else null).joinToString(" · ").ifBlank { null }
}

fun paymentTone(status: String): Pair<String, TonePair> = when (status) {
    "confirmed" -> "Confirmed" to Tones.Green
    "pending" -> "Pending" to Tones.Orange
    "failed" -> "Failed" to Tones.Red
    "refunded" -> "Refunded" to Tones.Purple
    "refund_pending" -> "Refund pending" to Tones.Purple
    "disputed" -> "Disputed" to Tones.Red
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

// ---- Invoices ----

enum class InvoiceFilter(val label: String, val status: String?) {
    Overdue("Overdue", "overdue"),
    Sent("Sent", "sent"),
    Draft("Draft", "draft"),
    Paid("Paid", "paid"),
    All("All", null),
}

class InvoicesViewModel(app: AppContainer) : RegisterViewModel<InvoiceFullDto, InvoiceFilter>(app, InvoiceFilter.Overdue) {
    override suspend fun fetch(page: Int, query: String, filter: InvoiceFilter): Page<InvoiceFullDto> =
        app.api.invoiceRecords(page = page, status = filter.status, search = query.ifBlank { null })

    private fun change(inv: InvoiceFullDto, key: String, success: String, call: suspend () -> InvoiceFullDto, onDone: () -> Unit) = act(key, success) {
        val updated = apiCall { call() }
        replace({ it.id == inv.id }, updated)
        app.customers.customers.refreshAsync()
        app.payments.invoices.refreshAsync()
        onDone()
    }

    fun send(inv: InvoiceFullDto, onDone: () -> Unit) = change(inv, "send", "Invoice sent to the customer", { app.api.sendInvoice(inv.id) }, onDone)
    fun markOverdue(inv: InvoiceFullDto, onDone: () -> Unit) = change(inv, "overdue", "Marked overdue", { app.api.markInvoiceOverdue(inv.id) }, onDone)
    fun waive(inv: InvoiceFullDto, onDone: () -> Unit) = change(inv, "waive", "Invoice waived", { app.api.waiveInvoice(inv.id) }, onDone)
    fun cancel(inv: InvoiceFullDto, onDone: () -> Unit) = change(inv, "cancel", "Invoice cancelled", { app.api.cancelInvoice(inv.id) }, onDone)

    fun share(context: Context, inv: InvoiceFullDto) = act("pdf", null) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
            File(dir, "${inv.invoiceNumber.ifBlank { "invoice-${inv.id}" }}.pdf").also { out ->
                apiCall { app.api.invoicePdf(inv.id) }.byteStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share invoice"))
    }
}

@Composable
fun InvoicesScreen(onCustomer: (Int) -> Unit) {
    val vm = containerViewModel(key = "invoices-register") { app, _ -> InvoicesViewModel(app) }
    var open by remember { mutableStateOf<InvoiceFullDto?>(null) }
    RegisterList(
        vm = vm,
        filters = InvoiceFilter.entries.map { PillTabOption(it, it.label, if (it == vm.filter) vm.total else null) },
        searchHint = "Search invoice number or customer",
        emptyTitle = "No invoices",
        emptyMessage = "Nothing matches this filter.",
        key = { it.id },
    ) { inv ->
        BeamCard(onClick = { open = inv }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(inv.customerName?.ifBlank { null } ?: "Customer ${inv.customer}", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(inv.invoiceNumber.ifBlank { null }, inv.dueDate?.let { "due " + shortDate(runCatching { LocalDate.parse(it) }.getOrNull()) }).joinToString(" · "),
                        style = BeamType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(kshShort(parseMoney(inv.amountDue?.takeIf { parseMoney(it) > 0 } ?: inv.total)), style = BeamType.Amount)
                    invoiceTone(inv.status).let { (label, tone) -> Tag(label, tone) }
                }
            }
        }
    }
    open?.let { inv -> InvoiceSheet(vm, inv, onCustomer = { id -> open = null; onCustomer(id) }) { open = null } }
}

@Composable
private fun InvoiceSheet(vm: InvoicesViewModel, inv: InvoiceFullDto, onCustomer: (Int) -> Unit, onClose: () -> Unit) {
    val toast by vm.app.toasts.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    val open = inv.status == "draft" || inv.status == "sent" || inv.status == "overdue"
    BeamSheet(inv.invoiceNumber.ifBlank { "Invoice" }, onClose, toast) {
        ListCard {
            val rows = listOfNotNull(
                "Customer" to (inv.customerName ?: "—"),
                "Status" to invoiceTone(inv.status).first,
                inv.periodStart?.let { "Period" to listOfNotNull(it, inv.periodEnd).joinToString(" – ") },
                inv.dueDate?.let { "Due" to it },
                "Total" to kshShort(parseMoney(inv.total)),
                inv.amountPaid?.let { "Paid" to kshShort(parseMoney(it)) },
                inv.amountDue?.let { "Still due" to kshShort(parseMoney(it)) },
            )
            rows.forEachIndexed { i, (l, v) -> InfoRow(l, v, showDivider = i < rows.lastIndex) }
        }
        if (inv.lineItems.isNotEmpty()) {
            ListCard {
                inv.lineItems.forEachIndexed { i, li -> InfoRow(li.description.ifBlank { "Item" }, kshShort(parseMoney(li.amount ?: li.unitPrice)), showDivider = i < inv.lineItems.lastIndex) }
            }
        }
        inv.customer?.let { id -> SecondaryButton("Open customer", { onCustomer(id) }, bold = true) }
        SecondaryButton(if (vm.busy == "pdf") "Preparing…" else "Share PDF", { vm.share(context, inv) }, bold = true)
        if (open) SecondaryButton(if (vm.busy == "send") "Sending…" else "Send to customer", { vm.send(inv, onClose) }, bold = true)
        if (inv.status == "sent") SecondaryButton("Mark overdue", { confirm = "overdue" }, bold = true)
        if (open) {
            SecondaryButton("Waive (forgive) invoice", { confirm = "waive" }, textColor = BeamColors.Warning, bold = true)
            SecondaryButton("Cancel invoice", { confirm = "cancel" }, textColor = BeamColors.Danger, bold = true)
        }
    }
    when (confirm) {
        "overdue" -> ConfirmDialog("Mark overdue?", "The customer may be suspended by the normal overdue rules.", "Mark overdue", { vm.markOverdue(inv, onClose) }, { confirm = null })
        "waive" -> ConfirmDialog("Waive this invoice?", "The customer won't have to pay ${kshShort(parseMoney(inv.total))}. This can't be undone.", "Waive", { vm.waive(inv, onClose) }, { confirm = null })
        "cancel" -> ConfirmDialog("Cancel this invoice?", "Use this for invoices raised by mistake. This can't be undone.", "Cancel invoice", { vm.cancel(inv, onClose) }, { confirm = null })
    }
}

fun invoiceTone(status: String): Pair<String, TonePair> = when (status) {
    "paid" -> "Paid" to Tones.Green
    "overdue" -> "Overdue" to Tones.Red
    "sent" -> "Sent" to Tones.Blue
    "draft" -> "Draft" to Tones.Neutral
    "waived" -> "Waived" to Tones.Purple
    "cancelled" -> "Cancelled" to Tones.Neutral
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

