package com.helper.beamnetworks

import com.helper.beamnetworks.data.notifications.DeviceSeen
import com.helper.beamnetworks.data.notifications.DeviceSnapshot
import com.helper.beamnetworks.data.notifications.DowntimeSeen
import com.helper.beamnetworks.data.notifications.NotificationRules
import com.helper.beamnetworks.data.notifications.PeriodSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private val now = 10_000_000_000L
    private val min = 60_000L

    private fun device(id: Int, online: Boolean?) = DeviceSnapshot(id, "Router $id", "Mwiki", online, lastSeen = now - 5 * min)
    private fun period(id: Int, startedMinAgo: Long, network: String = "Mwiki", expected: Boolean = false) =
        PeriodSnapshot(id, customerId = id, name = "Customer $id", network = network, startedAt = now - startedMinAgo * min, expected = expected)

    @Test
    fun routerGoingOfflineAndBack() {
        val start = mapOf(1 to DeviceSeen("Router 1", online = true, since = now - 60 * min))
        val (down, afterDown) = NotificationRules.deviceChanges(start, listOf(device(1, false)), now)
        assertEquals(1, down.size)
        assertTrue(down.single().wentOffline)

        val (up, _) = NotificationRules.deviceChanges(afterDown, listOf(device(1, true)), now + 20 * min)
        assertEquals(1, up.size)
        assertEquals(false, up.single().wentOffline)
        // Offline since its last-seen time (5 min before going down) until now + 20 min
        assertEquals(25 * min, up.single().offlineForMs)
    }

    @Test
    fun newAndProvisioningRoutersAreQuiet() {
        val (changes, next) = NotificationRules.deviceChanges(emptyMap(), listOf(device(1, false), device(2, null)), now)
        assertTrue(changes.isEmpty())
        assertEquals(setOf(1), next.keys)
    }

    @Test
    fun noRepeatWhileStillOffline() {
        val start = mapOf(1 to DeviceSeen("Router 1", online = false, since = now - 10 * min))
        assertTrue(NotificationRules.deviceChanges(start, listOf(device(1, false)), now).first.isEmpty())
    }

    @Test
    fun clientDowntimeNotifiesOnceThenBackOnline() {
        val first = NotificationRules.downtimeChanges(emptyMap(), listOf(period(1, 40), period(2, 35, expected = true)), now)
        assertEquals(listOf(1), first.newlyDown.map { it.id })   // planned maintenance is skipped

        val again = NotificationRules.downtimeChanges(first.next, listOf(period(1, 40)), now)
        assertTrue(again.newlyDown.isEmpty())

        val back = NotificationRules.downtimeChanges(again.next, emptyList(), now)
        assertEquals("Customer 1", back.backOnline.single().first.name)
        assertEquals(40 * min, back.backOnline.single().second)
        assertTrue(back.next.isEmpty())
    }

    @Test
    fun massOutageDetectedPerNetworkAndResolved() {
        val drops = (1..6).map { period(it, startedMinAgo = 10) } + listOf(period(99, 10, network = "Githurai"))
        val started = NotificationRules.outageChanges(emptyMap(), drops, now, minCustomers = 5)
        assertEquals(listOf("Mwiki"), started.started.map { it.first })
        assertEquals(6, started.started.single().second.size)

        // Still down: no second alert
        val ongoing = NotificationRules.outageChanges(started.next, drops, now + 5 * min, minCustomers = 5)
        assertTrue(ongoing.started.isEmpty() && ongoing.resolved.isEmpty())

        // Only one customer left offline: resolved
        val resolved = NotificationRules.outageChanges(ongoing.next, listOf(period(1, 25)), now + 15 * min, minCustomers = 5)
        assertEquals(listOf("Mwiki"), resolved.resolved)
        assertTrue(resolved.next.isEmpty())
    }

    @Test
    fun scatteredDropsAreNotAnOutage() {
        val drops = (1..6).map { period(it, startedMinAgo = 60L * it) }
        assertTrue(NotificationRules.outageChanges(emptyMap(), drops, now, minCustomers = 5).started.isEmpty())
    }

    @Test
    fun durations() {
        assertEquals("1 min", NotificationRules.duration(10_000))
        assertEquals("45 min", NotificationRules.duration(45 * min))
        assertEquals("2 h", NotificationRules.duration(120 * min))
        assertEquals("2 h 10 min", NotificationRules.duration(130 * min))
        assertEquals("3 days", NotificationRules.duration(3 * 24 * 60 * min))
    }
}
