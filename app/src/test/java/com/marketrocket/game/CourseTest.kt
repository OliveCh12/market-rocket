package com.marketrocket.game

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test
    fun seedsRepeatAndGatesLeaveClearanceAndTimeToSteerAtTopSpeed() {
        fun course(seed: Int): List<CourseGate> {
            val generator = CourseGenerator(seed)
            return List(160) { generator.next() }
        }

        assertEquals(course(42), course(42))
        assertFalse(course(42) == course(43))
        for (seed in 0..63) {
            val gates = course(seed)
            for (gate in gates) {
                val ringY = gate.bonus.center.y
                val clearance = gate.bonus.kind.radius + PLAYER_RADIUS
                for (obstacle in listOfNotNull(gate.obstacle, gate.opposite)) {
                    assertTrue(obstacle.tipY in 0f..1f)
                    if (obstacle.edge == Edge.Floor) {
                        assertTrue(ringY + clearance < obstacle.tipY)
                    } else {
                        assertTrue(ringY - clearance > obstacle.tipY)
                    }
                }
                if (gate.bonus.kind == BonusKind.Premium) {
                    val other = checkNotNull(gate.opposite)
                    assertEquals(gate.obstacle.x, other.x, 0f)
                    val safeLane = (gate.obstacle.tipY + other.tipY) * 0.5f
                    assertTrue(abs(ringY - safeLane) > clearance)
                }
            }
            for ((before, after) in gates.zipWithNext()) {
                val travelGap = after.obstacle.x - before.obstacle.x -
                    before.obstacle.halfWidth - after.obstacle.halfWidth - 2 * PLAYER_RADIUS
                val requiredTime = abs(after.bonus.center.y - before.bonus.center.y) /
                    ROUTE_VERTICAL_SPEED + ROUTE_SETTLE_SECONDS
                assertTrue(travelGap / MAX_FLIGHT_SPEED + 0.0001 > requiredTime)
            }
        }
    }

    @Test
    fun laterPhrasesNarrowTheGapAndRequireSwitchingFromOneSideToTheOther() {
        val generator = CourseGenerator(42)
        val gates = List(32) { generator.next() }
        fun gap(gate: CourseGate) = abs(gate.obstacle.tipY - checkNotNull(gate.opposite).tipY)
        val firstPremium = gates[3]
        val latePremium = gates[31]
        assertTrue(gap(latePremium) < gap(firstPremium) - 0.08f)
        for (gate in gates.subList(29, 31)) {
            val other = checkNotNull(gate.opposite)
            assertFalse(0.5f in minOf(gate.obstacle.tipY, other.tipY)..maxOf(gate.obstacle.tipY, other.tipY))
        }
        assertTrue((gates[29].bonus.center.y - 0.5f) * (gates[30].bonus.center.y - 0.5f) < 0f)
    }

    @Test
    fun flyingExactlyThroughTheMiddleEventuallyHitsAClosingGate() {
        val engine = GameEngine(PlayerProfile(), seed = 48)
        engine.resize(320, 620)
        engine.startRound(seed = 0)
        val pilot = RingPilot(engine)

        repeat(40 * 120) {
            if (pilot.tick { 0.5f } == GameEvent.Crash) {
                assertEquals(Impact.Volatility, engine.snapshot().result?.impact)
                return
            }
        }
        error("The straight route should eventually meet a spike")
    }

    @Test
    fun aPilotCanChainRingsThroughTopSpeedAcrossSeedsAndScreenShapes() {
        val ringsPerFlight = 40
        for (seed in 0..11) {
            for ((width, height) in listOf(320 to 620, 640 to 640, 1_000 to 360)) {
                val engine = GameEngine(PlayerProfile(), seed = 48)
                engine.resize(width, height)
                engine.startRound(seed = seed)
                val pilot = RingPilot(engine)
                repeat(ringsPerFlight) { index ->
                    val frame = pilot.untilRing()
                    assertEquals("seed $seed, ${width}×$height", index + 1, frame.combo)
                }
                val amount = engine.snapshot().roundAmount
                assertTrue(engine.cashOut())
                val paid = engine.snapshot()
                assertTrue(paid.result?.newRecord == true)
                assertEquals(ringsPerFlight, paid.result?.rings)
                assertEquals(ringsPerFlight, paid.result?.bestCombo)
                assertEquals(ringsPerFlight, paid.profile.stats.bestCombo)
                assertEquals(ringsPerFlight.toLong(), paid.profile.stats.rings)
                assertEquals(1L, paid.profile.stats.cashOuts)
                assertEquals(amount, paid.profile.stats.bestCashOut)
                assertTrue(paid.profile.stats.longestFlightMillis > 1_000L)
            }
        }
    }

    @Test
    fun aCrashClosesTheComboAndRecordsTheRunWithoutCreditingThePot() {
        val engine = GameEngine(PlayerProfile(bank = 800L), seed = 12)
        engine.resize(360, 640)
        engine.startRound()
        assertEquals(1, RingPilot(engine).untilRing().combo)

        engine.setThrust(true)
        repeat(180) { engine.advance(1.0 / 120) }
        val crashed = engine.snapshot()
        assertEquals(Phase.Crashed, crashed.phase)
        assertEquals(0, crashed.combo)
        assertFalse(crashed.canCashOut)
        assertEquals(800L, crashed.profile.bank)
        assertEquals(1L, crashed.profile.stats.completedRounds)
        assertEquals(1L, crashed.profile.stats.rings)
        assertEquals(1, crashed.profile.stats.bestCombo)
        assertEquals(0L, crashed.profile.stats.cashOuts)
    }

    @Test
    fun theSafeLaneCanBypassAPremiumRingWithoutLockingCashOut() {
        val engine = GameEngine(PlayerProfile(), seed = 12)
        engine.resize(360, 640)
        engine.startRound()
        val pilot = RingPilot(engine)
        repeat(2) { pilot.untilRing() }
        assertEquals(3, pilot.untilRing().combo)

        repeat(480) {
            val frame = engine.snapshot()
            if (frame.combo == 0) {
                assertEquals(Phase.Playing, frame.phase)
                assertTrue(frame.canCashOut)
                assertTrue(engine.cashOut())
                assertEquals(3, engine.snapshot().profile.stats.bestCombo)
                assertEquals(3L, engine.snapshot().profile.stats.rings)
                return
            }
            pilot.tick { gate -> (gate.obstacle.tipY + checkNotNull(gate.opposite).tipY) * 0.5f }
        }
        error("The skipped ring was not resolved")
    }

    @Test
    fun tutorialRespondsToClimbDescentAndPickup() {
        val engine = GameEngine(PlayerProfile(), seed = 12)
        engine.resize(360, 640)
        engine.startRound(held = true)
        assertEquals(FlightLesson.Hold, engine.snapshot().lesson)
        repeat(76) { engine.advance(1.0 / 120) }
        assertEquals(FlightLesson.Release, engine.snapshot().lesson)

        engine.setThrust(false)
        repeat(60) { engine.advance(1.0 / 120) }
        assertEquals(FlightLesson.Collect, engine.snapshot().lesson)
        repeat(36) {
            if (!engine.snapshot().canCashOut) engine.advance(1.0 / 120)
        }
        assertEquals(FlightLesson.CashOut, engine.snapshot().lesson)
        assertTrue(engine.snapshot().canCashOut)
    }

    @Test
    fun comboIncreasesOnlyTheRingValueAndCapsTheBoost() {
        val bonus = Bonus(WorldPoint(0f, 0.5f), BonusKind.Premium, 130)
        assertEquals(130L, bonus.payout(1))
        assertEquals(140L, bonus.payout(2))
        assertEquals(192L, bonus.payout(7))
        assertEquals(192L, bonus.payout(60))
    }

    @Test
    fun aPerfectFlightPaysOutWithinTheExpectedBudgetAtAnyUpgradeLevel() {
        for (level in 0..8) {
            val profile = PlayerProfile(capitalLevel = level, leverageLevel = level)
            val engine = GameEngine(profile, seed = 48)
            engine.resize(320, 620)
            engine.startRound(seed = 0)
            val pilot = RingPilot(engine)
            var bonusesWon = 0L
            repeat(40) { index ->
                val frame = pilot.untilRing()
                bonusesWon += frame.rewards.last().amount
                val rings = index + 1
                val ceiling = when (rings) {
                    1 -> 750
                    4 -> 1_500
                    8 -> 2_500
                    24 -> 8_000
                    40 -> 15_000
                    else -> null
                }
                if (ceiling != null) assertTrue("level=$level, rings=$rings, amount=${frame.roundAmount}", frame.roundAmount < ceiling)
                if (level in listOf(0, 8) && rings in listOf(1, 4, 8, 16, 24, 40)) {
                    println("level=$level rings=$rings seconds=${frame.effectsTime} amount=${frame.roundAmount}")
                }
            }
            assertTrue(engine.cashOut())
            val stats = engine.snapshot().profile.stats
            val income = stats.longestFlightMillis * profile.incomePerSecond / 1_000
            assertEquals(profile.startingCapital + bonusesWon + income, stats.bestCashOut)
        }
    }

    private class RingPilot(private val engine: GameEngine) {
        private var previousY = engine.snapshot().player.y
        private var active: CourseGate? = null
        private var target = previousY

        fun untilRing(): GameState {
            repeat(540) {
                when (tick { it.bonus.center.y }) {
                    GameEvent.Ring -> return engine.snapshot()
                    GameEvent.Crash -> error("Pilot crashed at ${engine.snapshot().player}")
                    else -> Unit
                }
            }
            error("No ring was reached")
        }

        fun tick(aim: (CourseGate) -> Float): GameEvent? {
            val frame = engine.snapshot()
            val gate = active
            val clearance = if (gate == null) 0f else gate.obstacle.x + gate.obstacle.halfWidth + PLAYER_RADIUS
            val next = frame.bonuses.firstOrNull()
            if (next != null && (gate == null || (frame.player.x > clearance && next != gate.bonus))) {
                val obstacle = frame.obstacles.first { abs(it.x - next.center.x - RING_LEAD) < 0.0001f }
                val opposite = frame.obstacles.firstOrNull { it.x == obstacle.x && it.edge != obstacle.edge }
                active = CourseGate(obstacle, next, opposite)
                target = aim(checkNotNull(active))
            }
            val verticalSpeed = (frame.player.y - previousY) * 120f
            previousY = frame.player.y
            engine.setThrust(frame.player.y + verticalSpeed * 0.12f > target)
            return engine.advance(1.0 / 120)
        }
    }
}
