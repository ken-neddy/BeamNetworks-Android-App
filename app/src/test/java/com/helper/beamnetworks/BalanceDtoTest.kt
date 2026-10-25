package com.helper.beamnetworks

import com.helper.beamnetworks.data.api.ApiJson
import com.helper.beamnetworks.data.api.BalanceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceDtoTest {
    private fun parse(json: String) = ApiJson.decodeFromString(BalanceDto.serializer(), json)

    @Test
    fun readsTheSmsBalanceBeamActuallySends() {
        val b = parse("""{"provider":"beamsms","available":true,"balance":6030,"unit":"SMS","raw":{"remaining_balance":"Ksh6,030"},"threshold":100,"low":false}""")
        assertEquals(6030.0, b.amount!!, 0.0)
        assertEquals(100.0, b.lowAt!!, 0.0)
        assertEquals("SMS", b.unit)
        assertFalse(b.low)
    }

    @Test
    fun readsADecimalWalletBalance() {
        val b = parse("""{"provider":"payhero","available":true,"balance":1407.5,"reason":"","applicable":true,"threshold":500,"low":true}""")
        assertEquals(1407.5, b.amount!!, 0.0)
        assertTrue(b.low)
        assertTrue(b.applicable)
    }

    @Test
    fun stillAcceptsTextAmounts() {
        assertEquals(6030.0, parse("""{"balance":"Ksh6,030"}""").amount!!, 0.0)
        assertEquals(12.5, parse("""{"balance":"12.50"}""").amount!!, 0.0)
    }

    @Test
    fun missingOrNullBalanceHasNoAmount() {
        assertNull(parse("""{"available":false,"reason":"Provider unreachable"}""").amount)
        assertNull(parse("""{"balance":null}""").amount)
    }

    @Test
    fun notApplicableWhenPayheroIsNotTheGateway() {
        assertFalse(parse("""{"provider":"payhero","applicable":false}""").applicable)
    }
}
