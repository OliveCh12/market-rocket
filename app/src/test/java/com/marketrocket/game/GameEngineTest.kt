package com.marketrocket.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun sameFlightAndGrowthAt60And120Hz() {
        fun flight(hz: Int): GameState {
            val engine = GameEngine(PlayerProfile(), seed = 12)
            engine.resize(380, 430)
            engine.startRound()
            repeat(8) {
                engine.setThrust(true)
                repeat(hz / 6) { engine.advance(1.0 / hz) }
                engine.setThrust(false)
                repeat(hz / 6) { engine.advance(1.0 / hz) }
            }
            return engine.snapshot()
        }

        val at60 = flight(60)
        val at120 = flight(120)
        assertEquals(Phase.Playing, at60.phase)
        assertEquals(at60.phase, at120.phase)
        assertEquals(at60.player.x, at120.player.x, 0.0001f)
        assertEquals(at60.player.y, at120.player.y, 0.0001f)
        assertEquals(at60.roundAmount, at120.roundAmount)
    }

    @Test
    fun collectingARingUpdatesThePotAndPublishesGoldEffects() {
        val engine = GameEngine(PlayerProfile(), seed = 12)
        engine.resize(380, 430)
        engine.startRound()

        var collected: GameState? = null
        repeat(240) {
            if (collected == null) {
                engine.setThrust(engine.snapshot().player.y > 0.44f)
                if (engine.advance(1.0 / 120)) collected = engine.snapshot()
            }
        }

        assertNotNull(collected)
        val frame = checkNotNull(collected)
        assertTrue(frame.roundAmount >= 600L)
        assertEquals(BonusKind.Cash, frame.rewards.single().kind)
        assertTrue(frame.particles.any { it.color == ParticleColor.Gold })
        assertFalse(frame.bonuses.any { it.center.x < 0.5f })
        assertEquals(0L, frame.profile.bank)
    }

    @Test
    fun cashOutCreditsTheBankExactlyOnce() {
        val engine = GameEngine(PlayerProfile(bank = 200), seed = 12)
        engine.startRound()
        repeat(30) { engine.advance(1.0 / 60) }
        val pot = engine.snapshot().roundAmount

        assertFalse(engine.buy(Upgrade.Capital))
        assertTrue(engine.cashOut())
        assertFalse(engine.cashOut())
        assertEquals(Phase.CashedOut, engine.snapshot().phase)
        assertEquals(200 + pot, engine.snapshot().profile.bank)
        assertEquals(pot, engine.snapshot().result?.amount)

        assertTrue(engine.startRound())
        assertEquals(250L, engine.snapshot().roundAmount)
        assertEquals(200 + pot, engine.snapshot().profile.bank)
    }

    @Test
    fun floorCollisionDiscardsThePotEvenWithAShield() {
        val engine = GameEngine(PlayerProfile(bank = 5_000, shieldLevel = 1), seed = 12)
        engine.startRound()
        repeat(150) { engine.advance(1.0 / 60) }
        val frame = engine.snapshot()

        assertEquals(Phase.Crashed, frame.phase)
        assertEquals(Impact.Floor, frame.result?.impact)
        assertEquals(5_000L, frame.profile.bank)
        assertEquals(0L, frame.roundAmount)
        assertTrue(frame.hasShield)
        assertFalse(engine.cashOut())
        assertTrue(engine.startRound())
        assertTrue(engine.snapshot().hasShield)
    }

    @Test
    fun aRedSpikeConsumesOneShieldAndUnprotectedContactCrashes() {
        for (shield in listOf(0, 1)) {
            val engine = GameEngine(PlayerProfile(shieldLevel = shield), seed = 12)
            engine.resize(380, 430)
            engine.startRound()
            val firstSpike = engine.snapshot().obstacles.first()
            var firstHit: GameState? = null
            repeat(245) {
                if (firstHit == null) {
                    engine.setThrust(engine.snapshot().player.y > 0.85f)
                    engine.advance(1.0 / 120)
                    val frame = engine.snapshot()
                    if (frame.phase == Phase.Crashed || (shield == 1 && !frame.hasShield)) firstHit = frame
                }
            }
            val frame = checkNotNull(firstHit)
            if (shield == 0) {
                assertEquals(Phase.Crashed, frame.phase)
                assertEquals(Impact.Volatility, frame.result?.impact)
            } else {
                assertEquals(Phase.Playing, frame.phase)
                assertFalse(frame.hasShield)
                assertFalse(firstSpike in frame.obstacles)
                assertTrue(engine.cashOut())
                assertTrue(engine.startRound())
                assertTrue(engine.snapshot().hasShield)
            }
        }
    }

    @Test
    fun upgradesChargeTheListedCostAndApplyToFollowingRounds() {
        val engine = GameEngine(PlayerProfile(bank = 10_000), seed = 12)
        assertTrue(engine.buy(Upgrade.Capital))
        assertTrue(engine.buy(Upgrade.Leverage))
        assertTrue(engine.buy(Upgrade.Shield))
        assertFalse(engine.buy(Upgrade.Shield))
        val upgraded = engine.snapshot().profile

        assertEquals(3_000L, upgraded.bank)
        assertEquals(500L, upgraded.startingCapital)
        assertTrue(upgraded.growthPerSecond > PlayerProfile().growthPerSecond)
        assertTrue(engine.startRound())
        assertEquals(500L, engine.snapshot().roundAmount)
        assertTrue(engine.snapshot().hasShield)
        assertFalse(engine.buy(Upgrade.Capital))
    }

    @Test
    fun progressionUsesEveryExactBankThreshold() {
        for ((index, tier) in wealthTiers.withIndex()) {
            val progress = PlayerProfile(bank = tier.threshold).tierProgress
            assertEquals(tier, progress.current)
            assertEquals(wealthTiers.getOrNull(index + 1), progress.next)
            assertEquals(if (progress.next == null) 1f else 0f, progress.fraction, 0f)
        }
        assertEquals(0.5f, PlayerProfile(bank = 2_500L).tierProgress.fraction, 0f)
    }
}

