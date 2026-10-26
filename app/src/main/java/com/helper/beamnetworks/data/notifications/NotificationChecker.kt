package com.helper.beamnetworks.data.notifications

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.helper.beamnetworks.data.api.BeamApi
import com.helper.beamnetworks.data.api.OfflinePeriodDto
import com.helper.beamnetworks.data.api.SessionStore
import com.helper.beamnetworks.data.api.apiCall
import com.helper.beamnetworks.data.api.fetchAllPages
import com.helper.beamnetworks.BuildConfig
import com.helper.beamnetworks.data.JobType
import com.helper.beamnetworks.data.update.AppUpdater
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Polls the billing API for the events the web dashboard reports and turns changes since the
 * last run into [AppNotification]s. The backend has no push channel for staff, so this runs from
 * WorkManager every 15 minutes and every minute while the app is open.
 */
class NotificationChecker(
    context: Context,
    private val api: BeamApi,
    private val sessions: SessionStore,
    private val settings: NotificationSettings,
    private val feed: NotificationFeed,
    private val notifier: Notifier,
) {
    private val prefs = context.getSharedPreferences("beam_notification_state", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun check(): List<AppNotification> = mutex.withLock {
        val session = sessions.current ?: return emptyList()
        if (session.mustChangePassword) return emptyList()
        val now = System.currentTimeMillis()
        var state = loadState()
        val events = mutableListOf<AppNotification>()

        /** Runs one source; the first successful run only records a baseline. */
        suspend fun source(key: String, category: NotificationCategory, block: suspend (CheckerState, MutableList<AppNotification>) -> CheckerState) {
            if (!settings.isEnabled(category)) {
                // Re-baseline when switched back on, instead of reporting everything missed
                state = state.copy(baselined = state.baselined - key)
                return
            }
            val found = mutableListOf<AppNotification>()
            try {
                val next = block(state, found)
                if (key in state.baselined) events += found
                state = next.copy(baselined = next.baselined + key)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Missing module or permission, or offline: skip this source this time
                Log.d(TAG, "Skipped $key: ${e.message}")
            }
        }

        source("devices", NotificationCategory.Routers) { s, out -> checkDevices(s, out, now) }
        source("downtime", NotificationCategory.Downtime) { s, out -> checkDowntime(s, out, now) }
        source("outages", NotificationCategory.Outages) { s, out -> checkOutages(s, out, now) }
        source("alerts", NotificationCategory.Outages) { s, out -> checkAlerts(s, out, now) }
        source("activity", NotificationCategory.Payments) { s, out -> checkActivity(s, out, now, setOf("payment"), NotificationCategory.Payments, "payments") }
        source("suspensions", NotificationCategory.Accounts) { s, out -> checkActivity(s, out, now, setOf("suspended", "reactivated"), NotificationCategory.Accounts, "suspensions") }
        source("tickets", NotificationCategory.Tickets) { s, out -> checkTickets(s, out, now, session.userId) }
        source("account", NotificationCategory.Billing) { s, out -> checkAccount(s, out, now) }
        source("balances", NotificationCategory.Billing) { s, out -> checkBalances(s, out, now) }
        source("stock", NotificationCategory.Stock) { s, out -> checkStock(s, out, now) }
        source("jobs", NotificationCategory.Jobs) { s, out -> checkMyJobs(s, out, now) }
        source("update", NotificationCategory.Updates) { s, out -> checkUpdate(s, out, now) }

        saveState(state)
        feed.add(events)
        notifier.post(events)
        events
    }

    /** Forgets everything (sign out). */
    fun reset() {
        prefs.edit { clear() }
    }

    // ---- Sources ----

    private suspend fun checkDevices(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val devices = apiCall { api.devices() }.map {
            DeviceSnapshot(
                id = it.id,
                name = it.name.ifBlank { "Router ${it.id}" },
                network = it.networkName,
                online = when (it.status) {
                    "online" -> true
                    "offline" -> false
                    else -> null
                },
                lastSeen = parseDateTime(it.lastSeenAt)?.toInstant()?.toEpochMilli(),
            )
        }
        val (changes, next) = NotificationRules.deviceChanges(s.devices, devices, now)
        changes.forEach { change ->
            val where = change.device.network?.let { " ($it)" }.orEmpty()
            out += AppNotification(
                id = "device-${change.device.id}-${if (change.wentOffline) "down" else "up"}-$now",
                category = NotificationCategory.Routers,
                title = if (change.wentOffline) "Router offline: ${change.device.name}" else "Router back online: ${change.device.name}",
                body = if (change.wentOffline) "${change.device.name}$where stopped responding. Customers on it may have no internet."
                else "${change.device.name}$where is responding again" + (change.offlineForMs?.let { " after ${NotificationRules.duration(it)}" } ?: "") + ".",
                time = now,
                link = Links.NETWORK,
            )
        }
        return s.copy(devices = next)
    }

    private suspend fun checkDowntime(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val periods = apiCall {
            fetchAllPages(maxPages = 5) { api.offlinePeriods(it, status = "open", minDurationMinutes = settings.downtimeMinutes) }
        }.map { it.snapshot() }
        val changes = NotificationRules.downtimeChanges(s.downtime, periods, now)
        val down = changes.newlyDown
        if (down.size > MAX_SINGLE) {
            out += AppNotification(
                id = "downtime-group-$now",
                category = NotificationCategory.Downtime,
                title = "${down.size} customers offline",
                body = down.joinToString(", ") { it.name } + " — offline for over ${settings.downtimeMinutes} min.",
                time = now,
                link = Links.NETWORK,
            )
        } else {
            down.forEach { p ->
                out += AppNotification(
                    id = "downtime-${p.id}",
                    category = NotificationCategory.Downtime,
                    title = "${p.name} is offline",
                    body = "Offline for ${NotificationRules.duration(now - p.startedAt)}" + (p.network?.let { " · $it" } ?: "") + ".",
                    time = now,
                    link = p.customerId?.let(Links::customer) ?: Links.NETWORK,
                )
            }
        }
        val back = changes.backOnline
        if (back.size > MAX_SINGLE) {
            out += AppNotification(
                id = "back-group-$now",
                category = NotificationCategory.Downtime,
                title = "${back.size} customers back online",
                body = back.joinToString(", ") { it.first.name },
                time = now,
                link = Links.NETWORK,
            )
        } else {
            back.forEach { (seen, downFor) ->
                out += AppNotification(
                    id = "back-${seen.customerId}-$now",
                    category = NotificationCategory.Downtime,
                    title = "${seen.name} is back online",
                    body = "Back after ${NotificationRules.duration(downFor)} offline.",
                    time = now,
                    link = seen.customerId?.let(Links::customer),
                )
            }
        }
        return s.copy(downtime = changes.next)
    }

    private suspend fun checkOutages(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val earliest = (s.outages.values.minOrNull() ?: now).coerceAtMost(now) - 30 * 60_000L
        val from = Instant.ofEpochMilli(earliest).atZone(zone).toOffsetDateTime().toString()
        val recent = apiCall { fetchAllPages(maxPages = 5) { api.offlinePeriods(it, status = "open", dateFrom = from) } }.map { it.snapshot() }
        val changes = NotificationRules.outageChanges(s.outages, recent, now, settings.massOutageCustomers)
        changes.started.forEach { (network, periods) ->
            val since = ZonedDateTime.ofInstant(Instant.ofEpochMilli(periods.first().startedAt), zone)
            out += AppNotification(
                id = "outage-$network-$now",
                category = NotificationCategory.Outages,
                title = "Possible outage on $network",
                body = "${periods.size} customers dropped since ${"%02d:%02d".format(since.hour, since.minute)}: " +
                    periods.take(5).joinToString(", ") { it.name } + if (periods.size > 5) "…" else "",
                time = now,
                link = Links.NETWORK,
            )
        }
        changes.resolved.forEach { network ->
            out += AppNotification(
                id = "outage-resolved-$network-$now",
                category = NotificationCategory.Outages,
                title = "Outage on $network looks resolved",
                body = "Most customers on $network are back online.",
                time = now,
                link = Links.NETWORK,
            )
        }
        return s.copy(outages = changes.next)
    }

    private suspend fun checkAlerts(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val alerts = apiCall { api.monitoringAlerts().results }
        alerts.filter { it.id !in s.openAlerts }.forEach { a ->
            val where = listOfNotNull(a.deviceName?.ifBlank { null }, a.networkName?.ifBlank { null }).joinToString(" · ")
            out += AppNotification(
                id = "alert-${a.id}",
                category = NotificationCategory.Outages,
                title = a.title.ifBlank { "Monitoring alert" } + if (a.severity.isNotBlank()) " (${a.severity})" else "",
                body = listOf(a.message, where).filter { it.isNotBlank() }.joinToString(" — "),
                time = parseDateTime(a.createdAt)?.toInstant()?.toEpochMilli() ?: now,
                link = Links.FEED,
                alertId = a.id,
            )
        }
        return s.copy(openAlerts = alerts.map { it.id }.toSet())
    }

    private suspend fun checkActivity(
        s: CheckerState,
        out: MutableList<AppNotification>,
        now: Long,
        types: Set<String>,
        category: NotificationCategory,
        key: String,
    ): CheckerState {
        // Each activity source keeps its own cursor, stored under its key
        val cursorKey = "cursor_$key"
        val since = prefs.getString(cursorKey, null)
            ?: Instant.ofEpochMilli(now - 60 * 60_000L).atZone(zone).toOffsetDateTime().toString()
        val events = apiCall { api.activityLog(dateFrom = since, eventTypes = types.joinToString(",")).results }
            .filter { it.type in types }
        val seen = s.activityIds.toSet()
        val fresh = events.filter { idOf(it.id, it.type, it.timestamp, it.customerId) !in seen }
        val items = fresh.map { e ->
            val name = e.customerName.orEmpty().ifBlank { "A customer" }
            AppNotification(
                id = "activity-" + idOf(e.id, e.type, e.timestamp, e.customerId),
                category = category,
                title = e.title?.takeIf { it.isNotBlank() } ?: when (e.type) {
                    "payment" -> "Payment from $name"
                    "suspended" -> "$name suspended"
                    else -> "$name reconnected"
                },
                body = listOfNotNull(e.customerName?.takeIf { e.title?.contains(it) != true }, e.detail?.ifBlank { null }).joinToString(" · "),
                time = parseDateTime(e.timestamp)?.toInstant()?.toEpochMilli() ?: now,
                link = e.customerId?.let(Links::customer),
            )
        }
        // Each event stays separate in the feed; the Notifier folds bursts into one tray summary
        out += items
        val newest = events.mapNotNull { parseDateTime(it.timestamp) }.maxOrNull()
        newest?.let { prefs.edit { putString(cursorKey, it.minusMinutes(1).toOffsetDateTime().toString()) } }
        val ids = (fresh.map { idOf(it.id, it.type, it.timestamp, it.customerId) } + s.activityIds).take(400)
        return s.copy(activityIds = ids)
    }

    private suspend fun checkTickets(s: CheckerState, out: MutableList<AppNotification>, now: Long, myUserId: Int): CheckerState {
        val tickets = apiCall { api.tickets(1) }.results
        tickets.filter { it.id > s.maxTicketId && it.createdBy != myUserId && it.status in setOf("open", "in_progress") }.forEach { t ->
            out += AppNotification(
                id = "ticket-${t.id}",
                category = NotificationCategory.Tickets,
                title = "New ticket" + (t.customerName?.takeIf { it.isNotBlank() }?.let { " from $it" } ?: ""),
                body = "#T-${t.id} · ${t.subject}",
                time = parseDateTime(t.createdAt)?.toInstant()?.toEpochMilli() ?: now,
                link = Links.ticket(t.id),
            )
        }
        val breached = tickets.filter { it.status in setOf("open", "in_progress") && it.slaStatus?.contains("breach", ignoreCase = true) == true }
        breached.filter { it.id !in s.breachedTickets }.forEach { t ->
            out += AppNotification(
                id = "sla-${t.id}",
                category = NotificationCategory.Tickets,
                title = "Ticket #T-${t.id} is past its SLA",
                body = listOfNotNull(t.customerName?.ifBlank { null }, t.subject).joinToString(" · "),
                time = now,
                link = Links.ticket(t.id),
            )
        }
        return s.copy(maxTicketId = maxOf(s.maxTicketId, tickets.maxOfOrNull { it.id } ?: 0), breachedTickets = breached.map { it.id }.toSet())
    }

    private suspend fun checkAccount(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val status = apiCall { api.accountStatus() }
        val current = when {
            status.suspended -> "suspended"
            status.readOnly -> "read_only"
            else -> "ok"
        }
        if (s.accountState != null && current != s.accountState) {
            out += AppNotification(
                id = "account-$current-$now",
                category = NotificationCategory.Billing,
                title = when (current) {
                    "suspended" -> "Your Beam account is suspended"
                    "read_only" -> "Your Beam account is read-only"
                    else -> "Your Beam account is active again"
                },
                body = when (current) {
                    "ok" -> "You can make changes again."
                    else -> "Pay ${status.amountDue?.let { "Ksh $it " } ?: ""}on the Beam web dashboard to restore full access."
                },
                time = now,
                link = Links.ACCOUNT,
            )
        }
        var trialNotified = s.trialNotifiedOn
        val trialEnds = parseDateTime(status.trialEndsAt)?.toLocalDate()
        val today = LocalDate.now(zone)
        if (status.status == "trial" && trialEnds != null && !trialEnds.isBefore(today) && trialEnds <= today.plusDays(3) && trialNotified != today.toString()) {
            out += AppNotification(
                id = "trial-$today",
                category = NotificationCategory.Billing,
                title = "Your Beam trial ends ${if (trialEnds == today) "today" else "on ${trialEnds.dayOfMonth} ${trialEnds.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"}",
                body = "Choose a plan on the Beam web dashboard to keep billing running.",
                time = now,
                link = Links.ACCOUNT,
            )
            trialNotified = today.toString()
        }
        return s.copy(accountState = current, trialNotifiedOn = trialNotified)
    }

    private suspend fun checkBalances(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val sms = runCatching { apiCall { api.smsBalance() } }.getOrNull()
        val payhero = runCatching { apiCall { api.payheroBalance() } }.getOrNull()
        if (sms == null && payhero == null) throw IllegalStateException("No balance access")
        val smsLow = sms?.low == true
        val payheroLow = payhero?.applicable == true && payhero.low
        if (smsLow && !s.smsLow) {
            out += AppNotification(
                id = "sms-low-$now",
                category = NotificationCategory.Billing,
                title = "SMS balance is low",
                body = "Balance ${sms?.amount?.let { "%,.0f".format(java.util.Locale.US, it) } ?: "low"}. Top up so customers keep getting receipts and reminders.",
                time = now,
            )
        }
        if (payheroLow && !s.payheroLow) {
            out += AppNotification(
                id = "payhero-low-$now",
                category = NotificationCategory.Billing,
                title = "PayHero wallet is low",
                body = "Balance Ksh ${payhero?.amount?.let { "%,.2f".format(java.util.Locale.US, it) } ?: "low"}. Top up so M‑Pesa prompts keep working.",
                time = now,
            )
        }
        return s.copy(smsLow = smsLow, payheroLow = payheroLow)
    }

    /** Tells staff when a newer BeamOps has been published, once per version. */
    private suspend fun checkUpdate(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val latest = AppUpdater.fetchLatest()
        if (!NotificationRules.shouldAnnounceUpdate(BuildConfig.VERSION_CODE, latest.versionCode, s.updateNotified)) return s
        // The first run of a source only records a baseline and reports nothing, so wait for the next one
        if ("update" !in s.baselined) return s
        val headline = latest.notes.ifBlank { latest.changesSince(BuildConfig.VERSION_NAME).firstOrNull()?.changes?.firstOrNull()?.title.orEmpty() }
        out += AppNotification(
            id = "update-${latest.versionCode}",
            category = NotificationCategory.Updates,
            title = "BeamOps ${latest.versionName} is available",
            body = listOf("Open the app and tap Update now.", headline).filter { it.isNotBlank() }.joinToString(" "),
            time = now,
            link = Links.FEED,
        )
        return s.copy(updateNotified = latest.versionCode)
    }

    /** Tells a technician when a job lands on them, with what the customer already has. */
    private suspend fun checkMyJobs(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val open = apiCall { api.jobs(1, mine = true) }.results.filter { it.status in setOf("open", "scheduled") }
        open.filter { it.id !in s.myJobs }.forEach { job ->
            val lines = job.description.orEmpty().lines()
            fun answer(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
            val equipment = if (job.jobType == "installation" && answer("Router: ") != null) {
                " Router: ${if (answer("Router: ") == "yes") "yes" else "no"}, PoE: ${if (answer("PoE: ") == "yes") "yes" else "no"}."
            } else ""
            out += AppNotification(
                id = "job-${job.id}",
                category = NotificationCategory.Jobs,
                title = "New ${JobType.from(job.jobType).label.lowercase()} assigned to you",
                body = listOfNotNull(
                    job.customerName?.ifBlank { null } ?: job.title.ifBlank { null },
                    parseDateTime(job.scheduledFor)?.let { whenLabel(it) },
                ).joinToString(" · ") + "." + equipment,
                time = now,
                link = Links.job(job.id),
            )
        }
        return s.copy(myJobs = open.map { it.id }.toSet())
    }

    private suspend fun checkStock(s: CheckerState, out: MutableList<AppNotification>, now: Long): CheckerState {
        val items = apiCall { api.inventory(1) }.results
        val low = items.filter { it.reorderThreshold > 0 && it.quantity <= it.reorderThreshold }
        low.filter { it.id !in s.lowStock }.forEach { item ->
            out += AppNotification(
                id = "stock-${item.id}-$now",
                category = NotificationCategory.Stock,
                title = "${item.name} is running low",
                body = "${item.quantity} left (reorder at ${item.reorderThreshold}).",
                time = now,
                link = Links.STOCK,
            )
        }
        return s.copy(lowStock = low.map { it.id }.toSet())
    }

    // ---- Helpers ----

    private fun OfflinePeriodDto.snapshot() = PeriodSnapshot(
        id = id,
        customerId = customer,
        name = customerName.ifBlank { "Customer ${customer ?: id}" },
        network = networkName?.ifBlank { null },
        startedAt = parseDateTime(startedAt)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis(),
        // Planned maintenance, or offline because suspended/paused/archived: not news
        expected = scheduledMaintenance != null || customerStatus in setOf("suspended", "paused", "archived"),
    )

    private fun idOf(id: kotlinx.serialization.json.JsonElement?, type: String, timestamp: String?, customer: Int?): String =
        (id as? JsonPrimitive)?.content ?: "$type-$customer-$timestamp"

    private fun loadState(): CheckerState =
        prefs.getString("state", null)?.let { runCatching { json.decodeFromString(CheckerState.serializer(), it) }.getOrNull() } ?: CheckerState()

    private fun saveState(state: CheckerState) {
        prefs.edit { putString("state", json.encodeToString(CheckerState.serializer(), state)) }
    }

    private companion object {
        const val TAG = "NotificationChecker"
        /** More events than this in one category become a single summary notification. */
        const val MAX_SINGLE = 3
    }
}
