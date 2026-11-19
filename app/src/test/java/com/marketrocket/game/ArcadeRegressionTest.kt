package com.marketrocket.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeRegressionTest {
    @Test
    fun initialCapitalCannotBeCashedOutBeforeCollectingARing() {
        val engine = GameEngine(PlayerProfile(bank = 100), seed = 12)
        assertTrue(engine.startRound())
        assertFalse(engine.cashOut())
        repeat(30) { engine.advance(1.0 / 60) }
        assertFalse(engine.cashOut())
        assertEquals(100L, engine.snapshot().profile.bank)
    }

    @Test
    fun spendingBankBalanceCannotDemoteAnEarnedStatus() {
        val engine = GameEngine(PlayerProfile(bank = 5_100), seed = 12)
        val tier = engine.snapshot().profile.tierProgress.current
        assertTrue(engine.buy(Upgrade.Capital))
        assertEquals(4_350L, engine.snapshot().profile.bank)
        assertEquals(tier, engine.snapshot().profile.tierProgress.current)
    }
}
