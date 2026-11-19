package com.marketrocket.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationTest {
    @Test
    fun theSameInputScheduleProducesIdenticalSimulationAtAllCadences() {
        fun fly(hz: Int, width: Int, height: Int): GameState {
            val engine = GameEngine(PlayerProfile(), seed = 12)
            engine.resize(width, height)
            engine.startRound()
            repeat(6) {
                engine.setThrust(true)
                repeat(hz / 5) { engine.advance(1.0 / hz) }
                engine.setThrust(false)
                repeat(hz / 5) { engine.advance(1.0 / hz) }
            }
            return engine.snapshot()
        }

        for ((width, height) in listOf(320 to 500, 480 to 900, 900 to 360, 1_200 to 1_200)) {
            val reference = fly(120, width, height)
            assertEquals(Phase.Playing, reference.phase)
            for (hz in listOf(30, 60, 90)) {
                val actual = fly(hz, width, height)
                assertEquals("$hz Hz", reference.player, actual.player)
                assertEquals(reference.roundAmount, actual.roundAmount)
                assertEquals(reference.phase, actual.phase)
            }
        }
    }

    @Test
    fun rejectsInvalidTimeAndLimitsCatchUpAfterAStall() {
        fun rocket() = GameEngine(PlayerProfile(), seed = 12).also { it.startRound() }
        val stalled = rocket()
        val before = stalled.snapshot()
        for (delta in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1.0, 0.0)) {
            stalled.advance(delta)
            assertEquals(before, stalled.snapshot())
        }

        stalled.resize(0, -1)
        stalled.advance(Double.MAX_VALUE)
        val bounded = rocket()
        bounded.advance(0.08)
        assertEquals(bounded.snapshot(), stalled.snapshot())
    }

    @Test
    fun fractionalStepsAndRotationKeepTheAuthoritativeTrajectory() {
        val flight = GameEngine(PlayerProfile(), seed = 12)
        flight.resize(360, 720)
        flight.startRound(held = true)

        val start = flight.snapshot().player
        flight.advance(1.0 / 240.0)
        assertEquals(start, flight.snapshot().player)
        flight.advance(1.0 / 120.0)

        val beforeRotation = flight.snapshot()
        val interpolated = flight.snapshot(interpolate = true).player
        assertTrue(interpolated.x > start.x && interpolated.x < beforeRotation.player.x)
        flight.resize(720, 360)
        assertEquals(beforeRotation, flight.snapshot())

        flight.setThrust(false)
        repeat(120) { flight.advance(1.0 / 120.0) }
        assertEquals(Phase.Playing, flight.snapshot().phase)
        assertTrue(flight.snapshot().player.y > start.y)
    }

    @Test
    fun unappliedInputAndStallsCannotTunnelThroughTheFloor() {
        var reference: RoundResult? = null
        for (hz in listOf(30, 60, 90, 120)) {
            val engine = GameEngine(PlayerProfile(), seed = 12)
            engine.resize(360, 720)
            engine.startRound()
            repeat(hz * 2) { engine.advance(1.0 / hz) }
            val frame = engine.snapshot()
            assertEquals(Impact.Floor, frame.result?.impact)
            val expected = reference ?: frame.result
            assertEquals(expected, frame.result)
            reference = expected
        }
    }

    @Test
    fun stableGeometryIsSharedAcrossFramesWithoutChangingOlderSnapshots() {
        val engine = GameEngine(PlayerProfile(), seed = 12)
        engine.startRound()
        val start = engine.snapshot()
        engine.advance(1.0 / 120.0)
        val first = engine.snapshot()
        assertSame(start.obstacles, first.obstacles)
        assertSame(start.bonuses, first.bonuses)
        assertSame(start.trail, first.trail)
        val startingTrail = start.trail.toList()
        repeat(30) { engine.advance(1.0 / 120.0) }
        assertEquals(startingTrail, start.trail)
        assertTrue(engine.snapshot().trail.last().x > start.trail.last().x)
    }
}
