package com.helper.beamnetworks

import com.helper.beamnetworks.util.parseMpesaMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MpesaTest {
    @Test
    fun tillPayment() {
        val m = parseMpesaMessage("TJQ4ABC12D Confirmed. Ksh5,000.00 paid to FAIBA OPTICS LTD. on 25/10/26 at 3:15 PM.New M-PESA balance is Ksh12,340.00. Transaction cost, Ksh0.00.")
        assertEquals("TJQ4ABC12D", m.code)
        assertEquals("5000.00", m.amount)
        assertEquals("Faiba Optics Ltd", m.paidTo)
        assertEquals(LocalDate.of(2026, 10, 25), m.date)
    }

    @Test
    fun sendMoney() {
        val m = parseMpesaMessage("TJQ4ABC12E Confirmed. Ksh1,200.00 sent to JOHN DOE 0712345678 on 3/10/26 at 9:02 AM. New M-PESA balance is Ksh800.00.")
        assertEquals("1200.00", m.amount)
        assertEquals("John Doe", m.paidTo)
        assertEquals(LocalDate.of(2026, 10, 3), m.date)
    }

    @Test
    fun paybill() {
        val m = parseMpesaMessage("TJQ4ABC12F Confirmed. Ksh2,500.00 sent to KPLC PREPAID for account 54321 on 1/10/26 at 8:00 PM New M-PESA balance is Ksh100.00.")
        assertEquals("2500.00", m.amount)
        assertEquals("Kplc Prepaid", m.paidTo)
    }

    @Test
    fun notAPayment() {
        val m = parseMpesaMessage("Hello, your bundle expires tomorrow.")
        assertNull(m.code)
        assertNull(m.amount)
        assertNull(m.paidTo)
        assertNull(m.date)
    }
}
