package com.marketrocket.game

import kotlin.math.abs
import kotlin.random.Random

internal const val MAX_FLIGHT_SPEED = 0.73
internal const val RING_LEAD = 0.035f
internal const val ROUTE_VERTICAL_SPEED = 0.35f
internal const val ROUTE_SETTLE_SECONDS = 0.22f

internal data class CourseGate(val obstacle: Obstacle, val bonus: Bonus, val opposite: Obstacle? = null)

/** The same seed and generator version produce the same gates, regardless of frame rate. */
internal class CourseGenerator(seed: Int) {
    private val random = Random(seed)
    private var last: CourseGate? = null
    private var index = 0
    private var patternSide = 1

    fun next(): CourseGate {
        val previous = last
        val gate = if (previous == null) {
            CourseGate(
                Obstacle(0.52f, 0.105f, 0.65f, Edge.Floor),
                Bonus(WorldPoint(0.52f - RING_LEAD, 0.44f), BonusKind.Standard, 55),
            )
        } else {
            nextGate(previous)
        }
        last = gate
        index++
        return gate
    }

    private fun nextGate(previous: CourseGate): CourseGate {
        val slot = index % 4
        val difficulty = (index / 24f).coerceAtMost(1f)
        if (slot == 1) patternSide = if (random.nextBoolean()) 1 else -1
        // Recover near the middle, steer to one side, cross, then choose a precision pickup.
        val direction = when (slot) {
            0 -> 0
            1 -> patternSide
            else -> -patternSide
        }
        val premium = slot == 3
        val laneOffset = 0.075f + 0.15f * difficulty + 0.026f * random.nextFloat()
        val lane = 0.5f + direction * laneOffset + (random.nextFloat() - 0.5f) * 0.034f
        val outer = if (lane < 0.5f) Edge.Ceiling else Edge.Floor
        val edge = if (premium) outer else outer.opposite()
        val premiumOffset = if (outer == Edge.Ceiling) -0.074f else 0.074f
        val ringY = if (premium) lane + premiumOffset else lane
        val gapHalf = when (slot) {
            0 -> 0.28f
            3 -> 0.19f - 0.052f * difficulty
            else -> 0.23f - 0.10f * difficulty
        }
        val halfWidth = 0.09f + random.nextFloat() * 0.021f + difficulty * 0.027f

        // Leave room to clear the previous gate before steering to either passage at top speed.
        val travel = abs(ringY - previous.bonus.center.y)
        val timeToRing = travel / ROUTE_VERTICAL_SPEED + ROUTE_SETTLE_SECONDS
        val safeSpacing = previous.obstacle.halfWidth + halfWidth +
            2f * PLAYER_RADIUS + (MAX_FLIGHT_SPEED * timeToRing).toFloat()
        val spacing = maxOf(0.54f + random.nextFloat() * 0.085f - difficulty * 0.045f, safeSpacing)
        val x = previous.obstacle.x + spacing
        val ceiling = Obstacle(x, halfWidth, lane - gapHalf, Edge.Ceiling)
        val floor = Obstacle(x, halfWidth, lane + gapHalf, Edge.Floor)
        val main = if (edge == Edge.Ceiling) ceiling else floor
        val paired = index >= 3 && slot != 0
        val other = if (edge == Edge.Ceiling) floor else ceiling
        val depth = (index / 4).coerceAtMost(10)
        val premiumReward = if (premium) 75L + 6L * depth else 0L
        val reward = 55L + 12L * depth + premiumReward
        val kind = if (premium) BonusKind.Premium else BonusKind.Standard
        return CourseGate(
            main,
            Bonus(WorldPoint(x - RING_LEAD, ringY), kind, reward),
            other.takeIf { paired },
        )
    }

    private fun Edge.opposite() = if (this == Edge.Ceiling) Edge.Floor else Edge.Ceiling
}
