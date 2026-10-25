package com.helper.beamnetworks

import com.helper.beamnetworks.data.PaymentRepository
import com.helper.beamnetworks.util.ksh
import com.helper.beamnetworks.util.kshShort
import com.helper.beamnetworks.util.normalizePhone
import com.helper.beamnetworks.util.parseAmountInput
import com.helper.beamnetworks.util.parseDateTime
import com.helper.beamnetworks.util.parseLegacyDate
import com.helper.beamnetworks.util.relativeAge
import com.helper.beamnetworks.util.whenLabel
import com.helper.beamnetworks.util.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class FormatTest {
    @Test
    fun money() {
        assertEquals("Ksh 8,000.00", ksh(8000.0))
        assertEquals("-Ksh 334.00", ksh(-334.0))
        assertEquals("Ksh 0.00", ksh(-0.001))
        assertEquals("Ksh 29,000", kshShort(29000.4))
        assertEquals("-Ksh 6,009", kshShort(-6009.0))
    }

    @Test
    fun amountInput() {
        assertEquals(1500.0, parseAmountInput("1,500")!!, 0.0)
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("abc"))
    }

    @Test
    fun phonesMatchAcrossKenyanFormats() {
        val expected = "712345678"
        listOf("0712345678", "254712345678", "+254 712 345 678", "712345678").forEach {
            assertEquals(it, expected, normalizePhone(it))
        }
    }

    @Test
    fun apiDates() {
        val parsed = parseDateTime("2026-10-24T19:26:17+03:00")
        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 10, 24), parseDateTime("2026-10-24")!!.toLocalDate())
        assertNull(parseDateTime("not a date"))
        assertEquals(LocalDate.of(2026, 10, 9), parseLegacyDate("09/10/2026"))
    }

    @Test
    fun whenLabels() {
        val today = LocalDate.of(2026, 10, 24)
        val at = { d: LocalDate, h: Int, m: Int -> d.atTime(h, m).atZone(zone) }
        assertEquals("Today · 14:30", whenLabel(at(today, 14, 30), today))
        assertEquals("Tomorrow · 09:00", whenLabel(at(today.plusDays(1), 9, 0), today))
        assertEquals("26 Oct · 10:00", whenLabel(at(today.plusDays(2), 10, 0), today))
    }

    @Test
    fun relativeAges() {
        val now = ZonedDateTime.of(2026, 10, 24, 18, 0, 0, 0, zone)
        assertEquals("Just now", relativeAge(now.minusSeconds(20), now))
        assertEquals("2h ago", relativeAge(now.minusHours(2), now))
        assertEquals("Yesterday", relativeAge(now.minusDays(1), now))
        assertEquals("20 Oct", relativeAge(now.minusDays(4), now))
    }

    @Test
    fun paymentWindowCoversMonthAndChart() {
        // Early in the month the 14-day chart reaches into the previous month
        assertEquals(LocalDate.of(2026, 9, 21), PaymentRepository.defaultSince(LocalDate.of(2026, 10, 4)))
        assertEquals(LocalDate.of(2026, 10, 1), PaymentRepository.defaultSince(LocalDate.of(2026, 10, 24)))
    }
}
