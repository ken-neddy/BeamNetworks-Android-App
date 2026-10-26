package com.helper.beamnetworks.ui.screens.registers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.helper.beamnetworks.data.AppContainer
import com.helper.beamnetworks.data.api.ApiException
import com.helper.beamnetworks.data.api.BalanceDto
import com.helper.beamnetworks.data.api.NotificationLogDto
import com.helper.beamnetworks.data.api.Page
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
import com.helper.beamnetworks.ui.components.ListCard
import com.helper.beamnetworks.ui.components.NavRow
import com.helper.beamnetworks.ui.components.PillTabOption
import com.helper.beamnetworks.ui.components.PrimaryButton
import com.helper.beamnetworks.ui.components.SecondaryButton
import com.helper.beamnetworks.ui.components.SectionHeader
import com.helper.beamnetworks.ui.components.SwitchRow
import com.helper.beamnetworks.ui.components.Tag
import com.helper.beamnetworks.ui.components.TwoColumns
import com.helper.beamnetworks.ui.components.openCallerIdSettings
import com.helper.beamnetworks.ui.components.rememberCallerIdReady
import com.helper.beamnetworks.ui.icons.BeamIcons
import com.helper.beamnetworks.ui.screens.setup.PhoneSetup
import com.helper.beamnetworks.ui.navigation.containerViewModel
import com.helper.beamnetworks.ui.theme.BeamColors
import com.helper.beamnetworks.ui.theme.BeamType
import com.helper.beamnetworks.ui.theme.TonePair
import com.helper.beamnetworks.ui.theme.Tones
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.whenLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Serializable data object SmsLogRoute
@Serializable data object SettingsRoute

// ---- SMS / email log ----

enum class LogFilter(val label: String, val status: String?) {
    All("All", null), Failed("Failed", "failed"), Suppressed("Not sent", "suppressed"), Delivered("Delivered", "delivered"), Sent("Sent", "sent"), Queued("Queued", "queued"),
}

class SmsLogViewModel(app: AppContainer) : RegisterViewModel<NotificationLogDto, LogFilter>(app, LogFilter.All) {
    /** False when the tenant has switched SMS sending off, which explains "Not sent" rows. */
    var smsEnabled by mutableStateOf<Boolean?>(null)
        private set

    init {
        viewModelScope.launch {
            smsEnabled = runCatching { apiCall { app.api.settings() } }.getOrNull()?.get("sms_outbound_enabled")?.let { (it as? JsonPrimitive)?.booleanOrNull }
        }
    }

    override suspend fun fetch(page: Int, query: String, filter: LogFilter): Page<NotificationLogDto> =
        app.api.notificationLogs(page = page, status = filter.status, search = query.ifBlank { null })

    fun retry(log: NotificationLogDto, onDone: () -> Unit) = act("retry", "Sending again") {
        replace({ it.id == log.id }, apiCall { app.api.retryNotification(log.id) })
        onDone()
    }

    fun checkDelivery() = act("check", null) {
        val r = apiCall { app.api.checkDelivery() }
        app.toasts.show("Checked ${r.checked}: ${r.delivered} delivered, ${r.failed} failed, ${r.stillPending} pending")
        reload()
    }
}

@Composable
fun SmsLogScreen(onSettings: () -> Unit) {
    val vm = containerViewModel(key = "sms-log") { app, _ -> SmsLogViewModel(app) }
    var open by remember { mutableStateOf<NotificationLogDto?>(null) }
    RegisterList(
        vm = vm,
        filters = LogFilter.entries.map { PillTabOption(it, it.label) },
        searchHint = "Search phone, email or customer",
        emptyTitle = "No messages",
        emptyMessage = "Nothing matches this filter.",
        key = { it.id },
        header = {
            if (vm.smsEnabled == false) {
                item(key = "off") {
                    NavRow(BeamIcons.Alert, Tones.Red, "SMS sending is switched off", "Customers get no texts. Tap to turn it on.", onSettings)
                }
            }
            item(key = "check") { SecondaryButton(if (vm.busy == "check") "Checking…" else "Check delivery status", vm::checkDelivery, bold = true) }
        },
    ) { log ->
        BeamCard(onClick = { open = log }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(if (log.channel == "email") BeamIcons.Send else BeamIcons.MessageSquare, logTone(log.status).second, size = 34.dp, iconSize = 15.dp)
                Column(Modifier.weight(1f)) {
                    Text(log.customerName?.ifBlank { null } ?: log.recipient, style = BeamType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(eventLabel(log.eventType), style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    logTone(log.status).let { (l, t) -> Tag(l, t) }
                    Text(relativeAge(parseDateTime(log.createdAt)), style = BeamType.Meta)
                }
            }
            log.errorMessage?.ifBlank { null }?.let {
                Text(it, style = BeamType.Meta.copy(color = BeamColors.Danger), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    open?.let { log ->
        val toast by vm.app.toasts.message.collectAsStateWithLifecycle()
        BeamSheet(eventLabel(log.eventType), { open = null }, toast) {
            ListCard {
                val rows = listOfNotNull(
                    "To" to log.recipient,
                    log.customerName?.ifBlank { null }?.let { "Customer" to it },
                    "Channel" to log.channel.uppercase(),
                    "Status" to logTone(log.status).first,
                    "Created" to whenLabel(parseDateTime(log.createdAt)),
                    parseDateTime(log.sentAt)?.let { "Sent" to whenLabel(it) },
                    log.retryCount.takeIf { it > 0 }?.let { "Retries" to it.toString() },
                )
                rows.forEachIndexed { i, (l, v) -> InfoRow(l, v, showDivider = i < rows.lastIndex) }
            }
            Text(log.body, style = BeamType.Body)
            log.errorMessage?.ifBlank { null }?.let { Text(it, style = BeamType.Small.copy(color = BeamColors.Danger)) }
            if (log.status == "failed" || log.status == "suppressed" || log.status == "bounced") {
                SecondaryButton(if (vm.busy == "retry") "Sending…" else "Send again", { vm.retry(log) { open = null } }, bold = true)
            }
        }
    }
}

private fun logTone(status: String): Pair<String, TonePair> = when (status) {
    "delivered" -> "Delivered" to Tones.Green
    "sent" -> "Sent" to Tones.Blue
    "queued" -> "Queued" to Tones.Neutral
    "failed", "bounced" -> "Failed" to Tones.Red
    "suppressed" -> "Not sent" to Tones.Orange
    else -> status.replaceFirstChar { it.uppercase() } to Tones.Neutral
}

private fun eventLabel(event: String): String = event.replace('_', ' ').replaceFirstChar { it.uppercase() }

// ---- Settings ----

/**
 * Organisation settings from Beam (`/settings/`, admins only) plus this phone's own settings.
 * Edits are kept in [changes] and only those fields are PATCHed.
 */
class SettingsViewModel(val app: AppContainer) : ViewModel() {
    var settings by mutableStateOf<JsonObject?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var smsBalance by mutableStateOf<BalanceDto?>(null)
        private set
    var payheroBalance by mutableStateOf<BalanceDto?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    val changes = mutableStateMapOf<String, JsonElement>()

    val isAdmin get() = app.sessions.current?.isAdmin == true
    val email get() = app.sessions.current?.email.orEmpty()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            error = null
            try {
                settings = apiCall { app.api.settings() }
                changes.clear()
            } catch (e: ApiException) {
                error = e.message
            }
            smsBalance = runCatching { apiCall { app.api.smsBalance() } }.getOrNull()
            payheroBalance = runCatching { apiCall { app.api.payheroBalance() } }.getOrNull()?.takeIf { it.applicable }
        }
    }

    fun text(key: String): String = (changes[key] ?: settings?.get(key))?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    fun flag(key: String): Boolean = (changes[key] ?: settings?.get(key))?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
    fun list(key: String): String = ((changes[key] ?: settings?.get(key)) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(", ").orEmpty()

    fun setText(key: String, value: String) {
        changes[key] = JsonPrimitive(value)
    }

    fun setNumber(key: String, value: String) {
        value.filter { it.isDigit() || it == '.' }.let { v -> changes[key] = v.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(v) }
    }

    fun setFlag(key: String, value: Boolean) {
        changes[key] = JsonPrimitive(value)
    }

    fun setList(key: String, value: String) {
        changes[key] = JsonArray(value.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
    }

    fun save() {
        if (changes.isEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            try {
                settings = apiCall { app.api.updateSettings(JsonObject(changes.toMap())) }
                changes.clear()
                app.toasts.show("Settings saved")
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't save settings")
            } finally {
                saving = false
            }
        }
    }

    fun testSms() {
        val phones = list("system_alert_phones").split(", ").filter { it.isNotBlank() }
        if (phones.isEmpty()) return app.toasts.show("Add an alert phone first")
        viewModelScope.launch {
            try {
                apiCall { app.api.testAlertSms(JsonObject(mapOf("phones" to JsonArray(phones.map(::JsonPrimitive))))) }
                app.toasts.show("Test SMS sent to ${phones.joinToString()}")
            } catch (e: ApiException) {
                app.toasts.show(e.message ?: "Couldn't send the test SMS")
            }
        }
    }

    fun signOut() = app.logout()
}

@Composable
fun SettingsScreen(onAppNotifications: () -> Unit, onSmsLog: () -> Unit) {
    val vm = containerViewModel(key = "settings") { app, _ -> SettingsViewModel(app) }
    val context = LocalContext.current
    val callerIdReady = rememberCallerIdReady()
    LaunchedEffect(Unit) { if (vm.settings == null) vm.load() }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp), modifier = Modifier.fillMaxSize()) {
        if (vm.smsBalance != null || vm.payheroBalance != null) {
            item { SectionHeader("Balances") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.payheroBalance?.let { b -> BalanceCard("PayHero wallet", b, Modifier.weight(1f)) { amount -> ksh(amount) } }
                    vm.smsBalance?.let { b ->
                        BalanceCard("SMS balance", b, Modifier.weight(1f)) { amount ->
                            String.format(java.util.Locale.US, "%,.0f", amount) + (b.unit?.let { " $it" } ?: "")
                        }
                    }
                }
            }
        }
        // This phone
        item { SectionHeader("This phone") }
        item {
            ListCard(horizontalPadding = 14.dp) {
                NavRow(BeamIcons.Bell, Tones.Orange, "App alerts", "Which notifications this phone shows", onAppNotifications, tileSize = 38.dp, inCard = true)
                BeamDivider()
                NavRow(
                    BeamIcons.Phone, Tones.Teal, "Caller ID popup", if (callerIdReady) "On — shows who is calling" else "Off — tap to allow",
                    { openCallerIdSettings(context) }, tileSize = 38.dp, inCard = true,
                    trailing = { Tag(if (callerIdReady) "On" else "Off", if (callerIdReady) Tones.Green else Tones.Orange) },
                )
                BeamDivider()
                val allGranted = remember(callerIdReady, PhoneSetup.visible) { PhoneSetup.allGranted(context) }
                NavRow(
                    BeamIcons.Check, Tones.Blue, "Phone permissions", if (allGranted) "Everything is allowed" else "Some are off — tap to set up",
                    { PhoneSetup.open(context) }, tileSize = 38.dp, inCard = true,
                    trailing = { Tag(if (allGranted) "All on" else "Missing", if (allGranted) Tones.Green else Tones.Orange) },
                )
            }
        }
        val s = vm.settings
        when {
            s == null && vm.error != null -> item {
                EmptyState(
                    if (vm.isAdmin) "Couldn't load Beam settings" else "Beam settings are for admins",
                    vm.error!!, actionLabel = "Try again", onAction = vm::load, topPadding = 24.dp,
                )
            }
            s == null -> item { Text("Loading Beam settings…", style = BeamType.Small.copy(color = BeamColors.TextMuted), modifier = Modifier.padding(12.dp)) }
            else -> organisationSettings(vm, onSmsLog)
        }
        item { SecondaryButton("Sign out", vm::signOut, Modifier.padding(top = 20.dp), textColor = BeamColors.Danger, height = 48.dp, bold = true) }
        item { Text("Signed in as ${vm.email}", style = BeamType.Meta, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        item { BottomSpacer() }
    }
}

private fun LazyListScope.organisationSettings(vm: SettingsViewModel, onSmsLog: () -> Unit) {
    if (!vm.flag("sms_outbound_enabled")) {
        item {
            BeamCard(modifier = Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(BeamIcons.Alert, Tones.Red, size = 36.dp, iconSize = 16.dp)
                    Column(Modifier.weight(1f)) {
                        Text("SMS sending is off", style = BeamType.RowTitle)
                        Text("Reminders, receipts, pay links and logins are not texted to customers.", style = BeamType.Meta)
                    }
                }
            }
        }
    }
    item { SectionHeader("Messages") }
    item {
        SettingsCard {
            SwitchRow("Send SMS to customers", vm.flag("sms_outbound_enabled")) { vm.setFlag("sms_outbound_enabled", it) }
            SwitchRow("Send email to customers", vm.flag("email_outbound_enabled")) { vm.setFlag("email_outbound_enabled", it) }
            val channels = listOf("sms" to "SMS", "email" to "Email", "both" to "SMS and email")
            DropdownField(
                "Customer messages go by",
                channels.firstOrNull { it.first == vm.text("customer_notify_channel") }?.second ?: vm.text("customer_notify_channel"),
                channels.map { it.second },
                { label -> channels.firstOrNull { it.second == label }?.let { vm.setText("customer_notify_channel", it.first) } },
            )
            FormField("Admin alert phones (comma separated)", vm.list("system_alert_phones"), { vm.setList("system_alert_phones", it) }, keyboardType = KeyboardType.Phone)
            TwoColumns(
                { SecondaryButton("Send test SMS", vm::testSms, bold = true) },
                { SecondaryButton("SMS log", onSmsLog, bold = true) },
            )
        }
    }
    item { SectionHeader("Business") }
    item {
        SettingsCard {
            FormField("Support phone", vm.text("support_phone"), { vm.setText("support_phone", it) }, keyboardType = KeyboardType.Phone)
            FormField("Address", vm.text("address"), { vm.setText("address", it) })
            FormField("Tagline", vm.text("tagline"), { vm.setText("tagline", it) })
        }
    }
    item { SectionHeader("Billing") }
    item {
        SettingsCard {
            SwitchRow("Grace period before suspension", vm.flag("grace_period_enabled")) { vm.setFlag("grace_period_enabled", it) }
            if (vm.flag("grace_period_enabled")) {
                FormField("Grace period (days)", vm.text("grace_period_days"), { vm.setNumber("grace_period_days", it) }, keyboardType = KeyboardType.Number)
            }
            SwitchRow("Charge VAT", vm.flag("vat_enabled")) { vm.setFlag("vat_enabled", it) }
            if (vm.flag("vat_enabled")) {
                FormField("VAT rate (%)", vm.text("vat_rate"), { vm.setText("vat_rate", it) }, keyboardType = KeyboardType.Decimal)
            }
            TwoColumns(
                { FormField("Invoice prefix", vm.text("invoice_prefix"), { vm.setText("invoice_prefix", it) }) },
                { FormField("Min. pause (days)", vm.text("min_pause_days"), { vm.setNumber("min_pause_days", it) }, keyboardType = KeyboardType.Number) },
            )
            FormField("Invoice footer", vm.text("invoice_footer"), { vm.setText("invoice_footer", it) }, singleLine = false)
        }
    }
    item { SectionHeader("Outage detection") }
    item {
        SettingsCard {
            TwoColumns(
                { FormField("Min. customers", vm.text("mass_outage_min_customers"), { vm.setNumber("mass_outage_min_customers", it) }, keyboardType = KeyboardType.Number) },
                { FormField("Within (min)", vm.text("mass_outage_window_minutes"), { vm.setNumber("mass_outage_window_minutes", it) }, keyboardType = KeyboardType.Number) },
            )
            FormField("Ignore drops shorter than (min)", vm.text("mass_outage_min_duration_minutes"), { vm.setNumber("mass_outage_min_duration_minutes", it) }, keyboardType = KeyboardType.Number)
            SwitchRow("Text admins about mass outages", vm.flag("mass_outage_notify_admins")) { vm.setFlag("mass_outage_notify_admins", it) }
        }
    }
    item {
        Text(
            "Payment gateway, M‑Pesa and SMS provider credentials stay on the Beam web dashboard.",
            style = BeamType.Meta,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
    if (vm.changes.isNotEmpty()) {
        item { PrimaryButton(if (vm.saving) "Saving…" else "Save ${vm.changes.size} change${if (vm.changes.size == 1) "" else "s"}", vm::save, Modifier.padding(top = 12.dp), enabled = !vm.saving) }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    BeamCard(padding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** A wallet or SMS balance; turns red with a "Low" tag when Beam says it is below its alert level. */
@Composable
private fun BalanceCard(label: String, balance: BalanceDto, modifier: Modifier, format: (Double) -> String) {
    val amount = balance.amount
    BeamCard(modifier, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = BeamType.Meta, modifier = Modifier.weight(1f))
            if (balance.low) Tag("Low", Tones.Red)
        }
        Text(
            if (balance.available == false || amount == null) "Unavailable" else format(amount),
            style = BeamType.Stat.copy(color = if (balance.low) BeamColors.Danger else BeamColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val note = when {
            balance.available == false || amount == null -> balance.reason?.ifBlank { null } ?: "Couldn't reach the provider"
            balance.low -> "Top up soon"
            balance.lowAt != null -> "Alert below " + format(balance.lowAt!!)
            else -> null
        }
        if (note != null) Text(note, style = BeamType.Meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
