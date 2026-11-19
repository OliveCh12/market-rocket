package com.marketrocket.game

import kotlin.math.abs
import kotlin.random.Random

internal const val MAX_FLIGHT_SPEED = 0.59
internal const val RING_LEAD = 0.035f
internal const val ROUTE_VERTICAL_SPEED = 0.35f
internal const val ROUTE_SETTLE_SECONDS = 0.22f

internal data class CourseGate(val obstacle: Obstacle, val bonus: Bonus)

/** The same seed and generator version produce the same gates, regardless of frame rate. */
internal class CourseGenerator(seed: Int) {
    private val random = Random(seed)
    private var last: CourseGate? = null
    private var index = 0

    fun next(): CourseGate {
        val previous = last
        val gate = if (previous == null) {
            CourseGate(
                Obstacle(0.52f, 0.105f, 0.65f, Edge.Floor),
                Bonus(WorldPoint(0.52f - RING_LEAD, 0.44f), BonusKind.Cash),
            )
        } else {
            nextGate(previous)
        }
        last = gate
        index++
        return gate
    }

    private fun nextGate(previous: CourseGate): CourseGate {
        val difficulty = (previous.obstacle.x / 16f).coerceIn(0f, 1f)
        val edge = if (random.nextBoolean()) Edge.Ceiling else Edge.Floor
        val depth = 0.25f + random.nextFloat() * 0.08f + difficulty * 0.10f
        val halfWidth = 0.075f + random.nextFloat() * 0.04f + difficulty * 0.012f
        val offset = 0.03f + random.nextFloat() * 0.10f
        val y = 0.5f + if (edge == Edge.Floor) -offset else offset
        val tipY = if (edge == Edge.Floor) 1f - depth else depth
        val timeToRing = abs(y - previous.bonus.center.y) / ROUTE_VERTICAL_SPEED + ROUTE_SETTLE_SECONDS
        // Keep space to clear one obstacle and settle near the next ring even at top speed.
        val safeSpacing = previous.obstacle.halfWidth + halfWidth +
            2f * PLAYER_RADIUS + (MAX_FLIGHT_SPEED * timeToRing).toFloat()
        val spacing = maxOf(0.49f + random.nextFloat() * 0.12f - difficulty * 0.035f, safeSpacing)
        val x = previous.obstacle.x + spacing
        val reward = when {
            index % 4 == 3 -> BonusKind.Double
            index % 2 == 1 -> BonusKind.Leverage
            else -> BonusKind.Cash
        }
        return CourseGate(
            Obstacle(x, halfWidth, tipY, edge),
            Bonus(WorldPoint(x - RING_LEAD, y), reward),
        )
    }
}
