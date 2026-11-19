package com.marketrocket.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarketFormattingTest {
    @Test
    fun usesFrenchDecimalsAndPromotesRoundedUnits() {
        assertEquals("850 €", formatMoney(850))
        assertEquals("14,2 k€", formatMoney(14_200))
        assertEquals("3,5 M€", formatMoney(3_500_000))
        assertEquals("1,2 Md€", formatMoney(1_200_000_000))
        assertEquals("1 M€", formatMoney(999_950))
        assertEquals("1 Md€", formatMoney(999_950_000))
    }
}
