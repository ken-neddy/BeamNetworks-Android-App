package com.helper.beamnetworks

import com.helper.beamnetworks.data.CallerDirectory
import com.helper.beamnetworks.data.Customer
import com.helper.beamnetworks.data.CustomerStatus
import com.helper.beamnetworks.data.Job
import com.helper.beamnetworks.data.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CallerDirectoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun customer(name: String, phone: String) = Customer(
        id = 1, name = name, phone = phone, email = null, planId = null, planName = "Home 5", networkName = null,
        pppoeUser = null, accountCode = null, status = CustomerStatus.Active, amountDue = 0.0, lastSeen = null,
        online = false, identityId = null, location = "Mwiki",
    )

    private fun job(name: String, phone: String) = Job(
        id = 7, customerName = name, identityId = null, phone = phone, location = "Kamakis", notes = "", hasRouter = false,
        scheduled = null, status = JobStatus.Scheduled, technicianId = null, technicianName = null, completedAt = null,
    )

    @Test
    fun savedDirectoryAnswersAfterRestartWithoutNetwork() {
        val file = folder.newFile("caller_directory.json")
        CallerDirectory(file).update(listOf(customer("Jane Wanjiku", "0734944305")), listOf(job("Grace Wambui", "+254 711 000 111")))

        // A new instance only has the file (app was killed, phone offline)
        val offline = CallerDirectory(file)
        assertEquals("Jane Wanjiku", offline.lookup("+254734944305")?.name)
        assertEquals("Active customer · Home 5", offline.lookup("254734944305")?.label)
        assertEquals("Grace Wambui", offline.lookup("0711000111")?.name)
        assertNull(offline.lookup("0700000000"))
    }

    @Test
    fun customerWinsOverInstallationForTheSameNumber() {
        val directory = CallerDirectory(folder.newFile("d.json"))
        directory.update(listOf(customer("Kevin Otieno", "0722118430")), listOf(job("Kevin O.", "0722118430")))
        assertEquals("Kevin Otieno", directory.lookup("0722118430")?.name)
    }

    @Test
    fun signOutClearsTheSavedCopy() {
        val file = folder.newFile("d.json")
        CallerDirectory(file).apply {
            update(listOf(customer("Jane", "0734944305")), emptyList())
            clear()
        }
        assertNull(CallerDirectory(file).lookup("0734944305"))
    }
}
