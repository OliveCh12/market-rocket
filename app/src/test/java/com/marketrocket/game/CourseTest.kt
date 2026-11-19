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
                val obstacle = gate.obstacle
                val ringY = gate.bonus.center.y
                if (obstacle.edge == Edge.Floor) {
                    assertTrue(obstacle.tipY > 0.5f + PLAYER_RADIUS)
                    assertTrue(ringY + RING_RADIUS + PLAYER_RADIUS < obstacle.tipY)
                } else {
                    assertTrue(obstacle.tipY < 0.5f - PLAYER_RADIUS)
                    assertTrue(ringY - RING_RADIUS - PLAYER_RADIUS > obstacle.tipY)
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
    fun aPilotCanChainRingsAcrossSeedsAndScreenShapes() {
        for (seed in 0..11) {
            for ((width, height) in listOf(320 to 620, 640 to 640, 1_000 to 360)) {
                val engine = GameEngine(PlayerProfile(), seed = 48)
                engine.resize(width, height)
                engine.startRound(seed = seed)
                val pilot = RingPilot(engine)
                repeat(10) { index ->
                    val frame = pilot.untilRing()
                    assertEquals("seed $seed, ${width}×$height", index + 1, frame.combo)
                }
                val amount = engine.snapshot().roundAmount
                assertTrue(engine.cashOut())
                val paid = engine.snapshot()
                assertTrue(paid.result?.newRecord == true)
                assertEquals(10, paid.profile.stats.bestCombo)
                assertEquals(10L, paid.profile.stats.rings)
                assertEquals(1L, paid.profile.stats.cashOuts)
                assertEquals(amount, paid.profile.stats.bestCashOut)
                assertTrue(paid.profile.stats.longestFlightMillis > 1_000L)
            }
        }
    }

    @Test
    fun missingARingResetsComboButKeepsCashOutUnlocked() {
        val engine = GameEngine(PlayerProfile(), seed = 12)
        engine.resize(360, 640)
        engine.startRound()
        val pilot = RingPilot(engine)
        pilot.untilRing()
        assertEquals(2, pilot.untilRing().combo)

        repeat(480) {
            val frame = engine.snapshot()
            if (frame.combo == 0) {
                assertEquals(Phase.Playing, frame.phase)
                assertTrue(frame.canCashOut)
                assertTrue(engine.cashOut())
                assertEquals(2, engine.snapshot().profile.stats.bestCombo)
                return
            }
            pilot.tick { gate -> if (gate.obstacle.edge == Edge.Ceiling) 0.74f else 0.26f }
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
    fun comboIncreasesOnlyTheRingBonusAndCapsTheBoost() {
        assertEquals(1_250.0, BonusKind.Cash.collect(1_000.0, 1), 0.001)
        assertEquals(1_275.0, BonusKind.Cash.collect(1_000.0, 2), 0.001)
        assertEquals(1_750.0, BonusKind.Leverage.collect(1_000.0, 60), 0.001)
        assertEquals(2_500.0, BonusKind.Double.collect(1_000.0, 6), 0.001)
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
                active = CourseGate(obstacle, next)
                target = aim(checkNotNull(active))
            }
            val verticalSpeed = (frame.player.y - previousY) * 120f
            previousY = frame.player.y
            engine.setThrust(frame.player.y + verticalSpeed * 0.12f > target)
            return engine.advance(1.0 / 120)
        }
    }
}
