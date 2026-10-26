package com.helper.beamnetworks.data.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Kinds of staff notification, one Android channel each. They mirror the events the web
 * dashboard raises (device_offline/online, mass_outage_*, alert_fired, payment_received, …).
 */
enum class NotificationCategory(
    val channelId: String,
    val title: String,
    val description: String,
    val importance: Int,
) {
    Routers("beam_routers", "Router status", "A router goes offline or comes back online", NotificationManager.IMPORTANCE_HIGH),
    Outages("beam_outages", "Outages and monitoring alerts", "Many customers drop at once, or a monitoring rule fires", NotificationManager.IMPORTANCE_HIGH),
    Downtime("beam_downtime", "Client downtime", "A customer has been offline longer than your threshold, and when they're back", NotificationManager.IMPORTANCE_DEFAULT),
    Payments("beam_payments", "Payments received", "Customer payments (M‑Pesa, cash, pay link)", NotificationManager.IMPORTANCE_DEFAULT),
    Accounts("beam_accounts", "Suspensions and reconnections", "Customers suspended for non-payment or reconnected", NotificationManager.IMPORTANCE_LOW),
    Tickets("beam_tickets", "Tickets", "New tickets from customers or staff, and SLA breaches", NotificationManager.IMPORTANCE_DEFAULT),
    Billing("beam_billing", "Beam account and balances", "Your Beam subscription, and low SMS or PayHero balances", NotificationManager.IMPORTANCE_HIGH),
    Stock("beam_stock", "Low stock", "A product drops to its reorder level", NotificationManager.IMPORTANCE_LOW),
    Jobs("beam_jobs", "Jobs assigned to you", "An installation or visit is assigned to you", NotificationManager.IMPORTANCE_HIGH),
}

/** One event in the in-app feed (and, usually, a system notification). */
@Serializable
data class AppNotification(
    val id: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val time: Long,
    /** Deep link opened on tap, e.g. beamnetworks://app/customer/42. */
    val link: String? = null,
    /** Monitoring alert id, so the feed can offer Acknowledge / Resolve. */
    val alertId: Int? = null,
)

object Links {
    const val FEED = "beamnetworks://app/notifications"
    const val NETWORK = "beamnetworks://app/network"
    const val TICKETS = "beamnetworks://app/tickets"
    const val ACCOUNT = "beamnetworks://app/account"
    const val STOCK = "beamnetworks://app/stock"
    fun customer(id: Int) = "beamnetworks://app/customer/$id"
    fun ticket(id: Int) = "beamnetworks://app/ticket/$id"
    fun job(id: Int) = "beamnetworks://app/installation_details/$id"
}

/** Which categories are on, and the client-downtime threshold. */
class NotificationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("beam_notifications", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Changes whenever a setting changes, so screens can recompose. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun isEnabled(category: NotificationCategory): Boolean = prefs.getBoolean("on_${category.name}", true)

    fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        prefs.edit { putBoolean("on_${category.name}", enabled) }
        _version.value++
    }

    /** Minutes a customer must be offline before "client downtime" fires. */
    var downtimeMinutes: Int
        get() = prefs.getInt("downtime_minutes", 30)
        set(value) {
            prefs.edit { putInt("downtime_minutes", value) }
            _version.value++
        }

    /** Customers dropping on one network within half an hour that count as an outage. */
    var massOutageCustomers: Int
        get() = prefs.getInt("mass_outage_customers", 5)
        set(value) {
            prefs.edit { putInt("mass_outage_customers", value) }
            _version.value++
        }
}

/** The in-app feed, newest first, kept on disk (last 200 events). */
class NotificationFeed(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "notification_feed.json"))

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AppNotification.serializer())
    private val readFile = File(file.parentFile, file.nameWithoutExtension + "_read.txt")

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    private val _lastRead = MutableStateFlow(readFile.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L)
    val lastRead: StateFlow<Long> = _lastRead.asStateFlow()

    @Synchronized
    fun add(events: List<AppNotification>) {
        if (events.isEmpty()) return
        val known = _items.value.map { it.id }.toSet()
        val merged = (events.filterNot { it.id in known } + _items.value).sortedByDescending { it.time }.take(200)
        _items.value = merged
        runCatching { file.writeText(json.encodeToString(serializer, merged)) }
    }

    @Synchronized
    fun remove(id: String) {
        val kept = _items.value.filterNot { it.id == id }
        _items.value = kept
        runCatching { file.writeText(json.encodeToString(serializer, kept)) }
    }

    fun markAllRead(now: Long = System.currentTimeMillis()) {
        _lastRead.value = now
        runCatching { readFile.writeText(now.toString()) }
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        file.delete()
        readFile.delete()
        _lastRead.value = 0
    }

    private fun load(): List<AppNotification> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
}
