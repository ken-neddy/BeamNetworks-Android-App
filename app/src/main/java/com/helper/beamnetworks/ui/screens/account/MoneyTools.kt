package com.helper.beamnetworks.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.AssignReceiptRequest
import com.helper.beamnetworks.data.api.PendingPaymentDto
import com.helper.beamnetworks.data.api.UnmatchedReceiptDto
import com.helper.beamnetworks.data.api.VerifyPaymentRequest
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.ui.components.BeamCard
import com.helper.beamnetworks.ui.components.BeamDivider
import com.helper.beamnetworks.ui.components.BeamSheet
import com.helper.beamnetworks.ui.components.EmptyState
import com.helper.beamnetworks.ui.components.FormField
import com.helper.beamnetworks.ui.components.InfoRow
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.PillButton
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.SuggestField
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.normalizePhone
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseMoney
import com.helper.beamnetworks.util.relativeAge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Unmatched paybill deposits, stuck M‑Pesa prompts and the revenue report. */
class MoneyViewModel(private val app: AppContainer) : ViewModel() {
    var receipts by mutableStateOf<List<UnmatchedReceiptDto>?>(null)
        private set
    var pending by mutableStateOf<List<PendingPaymentDto>?>(null)
        private set
    var report by mutableStateOf<JsonObject?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    val customers = app.customers.customers.state
    val toasts = app.toasts

    init {
        app.customers.customers.ensureLoaded()
    }

    fun loadReceipts() = load { receipts = apiCall { app.api.unmatchedReceipts() }.results }
    fun loadPending() = load { pending = apiCall { app.api.pendingPayments() }.results.filter { it.method == null || it.method == "mpesa" } }
    fun loadReport() = load { report = apiCall { app.api.revenueReport() } }

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

    /** Best guess for who sent a receipt: same phone, or account code typed as the bill ref. */
    fun suggest(receipt: UnmatchedReceiptDto): Customer? {
        val list = app.customers.customers.items
        val ref = receipt.billRef?.trim().orEmpty()
        return list.firstOrNull { ref.isNotEmpty() && it.accountCode.equals(ref, ignoreCase = true) }
            ?: list.firstOrNull { receipt.msisdn != null && normalizePhone(it.phone) == normalizePhone(receipt.msisdn) && normalizePhone(it.phone).length >= 9 }
            ?: list.firstOrNull { ref.length >= 9 && normalizePhone(it.phone) == normalizePhone(ref) }
    }

    fun assign(receipt: UnmatchedReceiptDto, customer: Customer, onDone: () -> Unit) = act("assign") {
        apiCall { app.api.assignReceipt(receipt.id, AssignReceiptRequest(customerId = customer.id)) }
        receipts = receipts?.filterNot { it.id == receipt.id }
        app.toasts.show("${kshShort(parseMoney(receipt.amount))} assigned to ${customer.name}")
        app.payments.payments.refreshAsync()
        app.customers.customers.refreshAsync()
        onDone()
    }

    fun ignore(receipt: UnmatchedReceiptDto) = act("ignore-${receipt.id}") {
        apiCall { app.api.ignoreReceipt(receipt.id) }
        receipts = receipts?.filterNot { it.id == receipt.id }
        app.toasts.show("Receipt ignored")
    }

    fun verify(payment: PendingPaymentDto, code: String, onDone: () -> Unit) = act("verify") {
        val result = apiCall { app.api.verifyPayment(payment.id, VerifyPaymentRequest(code.trim().uppercase().ifBlank { null })) }
        app.toasts.show(
            when (result.status) {
                "confirmed" -> "Payment confirmed"
                "failed" -> "M‑Pesa says this payment failed" + (result.failureReason?.let { ": $it" } ?: "")
                else -> "Still pending at M‑Pesa. Try again shortly, or enter the code from the customer's SMS."
            },
        )
        if (result.status != "pending") pending = pending?.filterNot { it.id == payment.id }
        if (result.status == "confirmed") {
            app.payments.payments.refreshAsync()
            app.customers.customers.refreshAsync()
        }
        onDone()
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
}

fun LazyListScope.unmatchedSection(vm: MoneyViewModel, onAssign: (UnmatchedReceiptDto) -> Unit) {
    val receipts = vm.receipts
    item {
        Text(
            "Paybill deposits whose account number matched no customer. Assign each to the right customer to credit them.",
            style = BeamType.Meta,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
    }
    when {
        vm.error != null && receipts == null -> item { EmptyState("Couldn't load receipts", vm.error!!, topPadding = 24.dp) }
        receipts == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
        receipts.isEmpty() -> item { EmptyState("All matched", "No unmatched M‑Pesa deposits.", topPadding = 24.dp) }
    }
    items(receipts.orEmpty(), key = { "r${it.id}" }) { r ->
        val guess = vm.suggest(r)
        BeamCard(modifier = Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.payerName?.ifBlank { null } ?: r.msisdn ?: "Unknown payer", style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(r.transId, r.billRef?.ifBlank { null }?.let { "Acc: $it" }, relativeAge(parseDateTime(r.createdAt))).joinToString(" · "),
                        style = BeamType.Meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(kshShort(parseMoney(r.amount)), style = BeamType.RowTitleLarge.copy(color = BeamColors.Success))
            }
            if (guess != null) Text("Looks like ${guess.name}", style = BeamType.Meta.copy(color = BeamColors.Primary))
            BeamDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Assign", Tones.Blue, BeamColors.PrimaryTintPressed, { onAssign(r) })
                PillButton(if (vm.busy == "ignore-${r.id}") "…" else "Ignore", Tones.Neutral.copy(content = BeamColors.Ink), BeamColors.PressedNeutral, { vm.ignore(r) }, bold = false)
            }
        }
    }
}

fun LazyListScope.pendingSection(vm: MoneyViewModel, onVerify: (PendingPaymentDto) -> Unit) {
    val pending = vm.pending
    item {
        Text(
            "M‑Pesa prompts that haven't confirmed. If the customer says they paid, verify it here with the code from their SMS.",
            style = BeamType.Meta,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
    }
    when {
        vm.error != null && pending == null -> item { EmptyState("Couldn't load payments", vm.error!!, topPadding = 24.dp) }
        pending == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
        pending.isEmpty() -> item { EmptyState("Nothing pending", "Every M‑Pesa prompt has settled.", topPadding = 24.dp) }
    }
    items(pending.orEmpty(), key = { "p${it.id}" }) { p ->
        BeamCard(modifier = Modifier.padding(bottom = 8.dp), onClick = { onVerify(p) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.customerName?.ifBlank { null } ?: p.mpesaPhone ?: "Customer", style = BeamType.RowTitle)
                    Text(listOfNotNull(p.mpesaPhone, relativeAge(parseDateTime(p.createdAt))).joinToString(" · "), style = BeamType.Meta)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(kshShort(parseMoney(p.amount)), style = BeamType.RowTitle)
                    Text("Verify →", style = BeamType.MetaStrong.copy(color = BeamColors.Primary))
                }
            }
        }
    }
}

fun LazyListScope.reportSection(vm: MoneyViewModel) {
    val report = vm.report
    when {
        vm.error != null && report == null -> item { EmptyState("Reports unavailable", vm.error!!, topPadding = 24.dp) }
        report == null -> item { Text("Loading…", style = BeamType.Small.copy(color = BeamColors.TextMuted)) }
        else -> {
            // Shown as returned by the API: each section's figures, labelled from their keys
            listOf("revenue", "collections", "churn", "operations", "credits").forEach { key ->
                val section = report[key] as? JsonObject ?: return@forEach
                val rows = flatten(section)
                if (rows.isEmpty()) return@forEach
                item(key = "h-$key") { SectionHeader(humanize(key)) }
                item(key = "c-$key") {
                    ListCard { rows.forEachIndexed { i, (label, value) -> InfoRow(label, value, showDivider = i < rows.lastIndex) } }
                }
            }
            (report["aging"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { aging ->
                item(key = "h-aging") { SectionHeader("Overdue ageing") }
                item(key = "c-aging") {
                    ListCard {
                        aging.forEachIndexed { i, el ->
                            val obj = el as? JsonObject ?: return@forEachIndexed
                            val label = listOf("bucket", "label", "range", "name", "period").firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.content } ?: "Bucket ${i + 1}"
                            val amount = listOf("amount", "total", "balance", "amount_due").firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.doubleOrNull }
                            val count = listOf("count", "invoices", "customers").firstNotNullOfOrNull { (obj[it] as? JsonPrimitive)?.content }
                            InfoRow(humanize(label), listOfNotNull(amount?.let(::kshShort), count?.let { "$it" }).joinToString(" · ").ifBlank { "—" }, showDivider = i < aging.lastIndex)
                        }
                    }
                }
            }
        }
    }
}

private val moneyKeys = Regex("revenue|amount|mrr|arpu|total|collected|billed|outstanding|paid|due|credit|cash|accrual|value", RegexOption.IGNORE_CASE)

/** Top-level and one-level-nested numbers/strings, e.g. revenue.cash → "Cash". */
private fun flatten(obj: JsonObject, prefix: String = ""): List<Pair<String, String>> = obj.entries.flatMap { (k, v) ->
    val label = (if (prefix.isNotEmpty()) "$prefix · " else "") + humanize(k)
    when (v) {
        is JsonPrimitive -> listOf(label to format(k, v))
        is JsonObject -> if (prefix.isEmpty()) flatten(v, humanize(k)) else emptyList()
        else -> emptyList<Pair<String, String>>()
    }
}.filterNot { it.second.isBlank() || it.second == "null" }

private fun format(key: String, value: JsonPrimitive): String {
    val number = value.doubleOrNull ?: value.content.toDoubleOrNull()
    return when {
        number == null -> value.content
        key.contains("pct", true) || key.contains("percent", true) || key.contains("rate", true) -> String.format(java.util.Locale.US, "%.1f%%", number)
        moneyKeys.containsMatchIn(key) -> kshShort(number)
        number % 1.0 == 0.0 -> number.toLong().toString()
        else -> String.format(java.util.Locale.US, "%.2f", number)
    }
}

private fun humanize(key: String): String = key.replace('_', ' ').replace('-', ' ').trim()
    .replace(Regex("\\b(mrr|arpu|ar)\\b", RegexOption.IGNORE_CASE)) { it.value.uppercase() }
    .replaceFirstChar { it.uppercase() }

@Composable
fun AssignReceiptSheet(vm: MoneyViewModel, receipt: UnmatchedReceiptDto, onClose: () -> Unit) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    val guess = remember(receipt) { vm.suggest(receipt) }
    var query by rememberSaveable { mutableStateOf(guess?.name.orEmpty()) }
    var chosenId by rememberSaveable { mutableStateOf(guess?.id) }
    val chosen = customers.items.firstOrNull { it.id == chosenId && it.name == query }
    BeamSheet("Assign ${kshShort(parseMoney(receipt.amount))}", onClose, toast, ctaLabel = if (vm.busy == "assign") "Assigning…" else "Assign", onCta = {
        if (chosen == null) vm.toasts.show("Choose a customer") else vm.assign(receipt, chosen, onClose)
    }, ctaEnabled = vm.busy == null) {
        ListCard {
            val rows = listOfNotNull(
                "From" to (receipt.payerName?.ifBlank { null } ?: "—"),
                receipt.msisdn?.let { "Phone" to it },
                "M‑Pesa code" to receipt.transId,
                "Account typed" to (receipt.billRef?.ifBlank { null } ?: "—"),
            )
            rows.forEachIndexed { i, (k, v) -> InfoRow(k, v, showDivider = i < rows.lastIndex) }
        }
        SuggestField(
            label = "Customer",
            value = query,
            onValueChange = { query = it; chosenId = null },
            suggestions = customers.items,
            suggestionText = { "${it.name} · ${it.phone}" },
            onPick = { c: Customer -> query = c.name; chosenId = c.id },
            placeholder = "Search name or phone",
        )
        Text("The money is applied to their invoices; any extra stays as credit.", style = BeamType.Meta)
    }
}

@Composable
fun VerifyPaymentSheet(vm: MoneyViewModel, payment: PendingPaymentDto, onClose: () -> Unit) {
    val toast by vm.toasts.message.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    BeamSheet("Verify ${kshShort(parseMoney(payment.amount))}", onClose, toast, ctaLabel = if (vm.busy == "verify") "Checking…" else "Verify", onCta = { vm.verify(payment, code, onClose) }, ctaEnabled = vm.busy == null) {
        Text("Leave the code empty to ask M‑Pesa for the status. If that can't tell, enter the code from the customer's SMS (e.g. QGH7XYZ123).", style = BeamType.Small.copy(color = BeamColors.TextSecondary))
        FormField("M‑Pesa code (optional)", code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, placeholder = "QGH7XYZ123")
    }
}


