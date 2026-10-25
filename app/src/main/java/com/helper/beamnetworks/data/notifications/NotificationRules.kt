package com.helper.beamnetworks.data.notifications

import kotlinx.serialization.Serializable

/** What the checker remembers between runs, so it only reports changes. */
@Serializable
data class CheckerState(
    /** Sources that have had their silent first pass. */
    val baselined: Set<String> = emptySet(),
    val devices: Map<Int, DeviceSeen> = emptyMap(),
    val openAlerts: Set<Int> = emptySet(),
    val downtime: Map<Int, DowntimeSeen> = emptyMap(),
    /** Network name → when the outage started (epoch ms). */
    val outages: Map<String, Long> = emptyMap(),
    val activitySince: String? = null,
    val activityIds: List<String> = emptyList(),
    val maxTicketId: Int = 0,
    val breachedTickets: Set<Int> = emptySet(),
    val accountState: String? = null,
    val trialNotifiedOn: String? = null,
    val smsLow: Boolean = false,
    val payheroLow: Boolean = false,
    val lowStock: Set<Int> = emptySet(),
)

@Serializable
data class DeviceSeen(val name: String, val online: Boolean, val since: Long)

@Serializable
data class DowntimeSeen(val customerId: Int?, val name: String, val startedAt: Long)

data class DeviceSnapshot(val id: Int, val name: String, val network: String?, val online: Boolean?, val lastSeen: Long?)

data class PeriodSnapshot(val id: Int, val customerId: Int?, val name: String, val network: String?, val startedAt: Long, val expected: Boolean)

/** Pure change-detection rules, kept apart from I/O so they can be unit-tested. */
object NotificationRules {

    data class DeviceChange(val device: DeviceSnapshot, val wentOffline: Boolean, val offlineForMs: Long?)

    /**
     * Routers whose status flipped. Statuses other than online/offline (still provisioning,
     * error…) are ignored, and so are routers seen for the first time.
     */
    fun deviceChanges(previous: Map<Int, DeviceSeen>, current: List<DeviceSnapshot>, now: Long): Pair<List<DeviceChange>, Map<Int, DeviceSeen>> {
        val changes = mutableListOf<DeviceChange>()
        val next = previous.toMutableMap()
        for (device in current) {
            val online = device.online ?: continue
            val before = previous[device.id]
            if (before == null) {
                next[device.id] = DeviceSeen(device.name, online, if (online) now else device.lastSeen ?: now)
                continue
            }
            if (before.online != online) {
                changes += DeviceChange(device, wentOffline = !online, offlineForMs = if (online) now - before.since else null)
                next[device.id] = DeviceSeen(device.name, online, if (online) now else device.lastSeen ?: now)
            }
        }
        // Forget archived routers
        next.keys.retainAll(current.map { it.id }.toSet())
        return changes to next
    }

    data class DowntimeChanges(
        val newlyDown: List<PeriodSnapshot>,
        val backOnline: List<Pair<DowntimeSeen, Long>>,
        val next: Map<Int, DowntimeSeen>,
    )

    /** Customers past the threshold for the first time, and those whose outage ended. */
    fun downtimeChanges(previous: Map<Int, DowntimeSeen>, openPastThreshold: List<PeriodSnapshot>, now: Long): DowntimeChanges {
        val relevant = openPastThreshold.filterNot { it.expected }
        val newlyDown = relevant.filter { it.id !in previous }
        val openIds = relevant.map { it.id }.toSet()
        val back = previous.filterKeys { it !in openIds }.values.map { it to (now - it.startedAt) }
        val next = relevant.associate { it.id to DowntimeSeen(it.customerId, it.name, it.startedAt) }
        return DowntimeChanges(newlyDown, back, next)
    }

    data class OutageChanges(val started: List<Pair<String, List<PeriodSnapshot>>>, val resolved: List<String>, val next: Map<String, Long>)

    /**
     * A network has an outage when at least [minCustomers] customers on it dropped within the
     * last [windowMs] and are still offline. It's resolved once at most one of them is still down.
     */
    fun outageChanges(
        previous: Map<String, Long>,
        recentOpen: List<PeriodSnapshot>,
        now: Long,
        minCustomers: Int,
        windowMs: Long = 30 * 60_000L,
    ): OutageChanges {
        val byNetwork = recentOpen.filterNot { it.expected }.groupBy { it.network ?: "your network" }
        val started = byNetwork
            .filter { (network, periods) -> network !in previous && periods.count { now - it.startedAt <= windowMs } >= minCustomers }
            .map { (network, periods) -> network to periods.filter { now - it.startedAt <= windowMs }.sortedBy { it.startedAt } }
        val resolved = previous.keys.filter { network ->
            (byNetwork[network].orEmpty().count { it.startedAt >= previous.getValue(network) - windowMs }) <= 1
        }
        val next = previous.filterKeys { it !in resolved } + started.associate { (network, periods) -> network to periods.first().startedAt }
        return OutageChanges(started, resolved, next)
    }

    /** "45 min", "2 h 10 min", "3 days". */
    fun duration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(1)
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 48 * 60 -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0L) "$h h" else "$h h $m min"
            }
            else -> "${minutes / (24 * 60)} days"
        }
    }
}
