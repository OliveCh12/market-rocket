package com.marketrocket.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Owns the simulation and publishes read-only snapshots. Call on one thread; the Android
 * host advances it from the display clock and sends touch and shop actions on the same thread.
 */
internal class GameEngine(initialProfile: PlayerProfile, seed: Int = Random.nextInt()) {
    private val courseRandom = Random(seed)
    private val effectsRandom = Random(seed xor 0x5F17_8AC4)
    private val obstacles = mutableListOf<Obstacle>()
    private val bonuses = mutableListOf<Bonus>()
    private val trail = ArrayDeque<WorldPoint>()
    private val particles = mutableListOf<Particle>()
    private val rewards = mutableListOf<FloatingReward>()

    private var profile = initialProfile.normalized()
    private var playfield = Playfield()
    private var phase = Phase.Ready
    private var runNumber = 0
    private var player = START
    private var velocityY = 0.0
    private var elapsed = 0.0
    private var pot = profile.startingCapital.toDouble()
    private var result: RoundResult? = null
    private var thrust = false
    private var hasShield = profile.shieldLevel > 0
    private var shakeRemaining = 0f
    private var nextGateX = FIRST_GATE_X
    private var gateIndex = 0

    init {
        resetCourse()
    }

    val needsFrames: Boolean
        get() = phase == Phase.Playing ||
            shakeRemaining > 0f || particles.isNotEmpty() || rewards.isNotEmpty()

    fun snapshot() = GameState(
        phase = phase,
        profile = profile,
        runNumber = runNumber,
        player = player,
        trail = trail.toList(),
        obstacles = obstacles.toList(),
        bonuses = bonuses.toList(),
        particles = particles.toList(),
        rewards = rewards.toList(),
        roundAmount = pot.toLong(),
        result = result,
        hasShield = hasShield,
        shakeRemaining = shakeRemaining,
    )

    fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) playfield = Playfield(width.toFloat() / height)
    }

    fun startRound(held: Boolean = false): Boolean {
        if (phase == Phase.Playing) return false
        if (phase != Phase.Ready) resetCourse()
        phase = Phase.Playing
        runNumber++
        pot = profile.startingCapital.toDouble()
        result = null
        thrust = held
        hasShield = profile.shieldLevel > 0
        return true
    }

    fun setThrust(enabled: Boolean) {
        thrust = enabled && phase == Phase.Playing
    }

    fun cashOut(): Boolean {
        if (phase != Phase.Playing) return false
        val payment = min(pot.toLong(), MAX_BANK - profile.bank)
        profile = profile.copy(bank = profile.bank + payment)
        result = RoundResult(payment)
        pot = 0.0
        thrust = false
        phase = Phase.CashedOut
        burst(player, ParticleColor.Emerald, 22)
        return true
    }

    fun buy(upgrade: Upgrade): Boolean {
        if (phase == Phase.Playing) return false
        val price = upgrade.price(profile) ?: return false
        if (profile.bank < price) return false
        profile = upgrade.nextLevel(profile).copy(bank = profile.bank - price)
        if (phase == Phase.Ready) {
            pot = profile.startingCapital.toDouble()
            hasShield = profile.shieldLevel > 0
        }
        return true
    }

    /** Advances by seconds and reports whether this frame collected a gold ring. */
    fun advance(deltaTime: Double): Boolean {
        if (!needsFrames || !deltaTime.isFinite() || deltaTime <= 0.0) return false

        // Bound catch-up after a stall and substep collisions even on a 60 Hz display.
        val seconds = deltaTime.coerceAtMost(MAX_FRAME_SECONDS)
        updateEffects(seconds.toFloat())
        val steps = ceil(seconds / MAX_STEP_SECONDS).toInt()
        val stepSeconds = seconds / steps
        var collected = false
        repeat(steps) {
            if (phase != Phase.Playing) return collected
            if (step(stepSeconds)) collected = true
        }
        return collected
    }

    private fun step(seconds: Double): Boolean {
        val targetY = if (thrust) ASCENT_SPEED else FALL_SPEED
        val decay = exp(-FLIGHT_RESPONSE * seconds)
        val previous = player
        val nextY = previous.y + targetY * seconds +
            (velocityY - targetY) * (1.0 - decay) / FLIGHT_RESPONSE
        velocityY = targetY + (velocityY - targetY) * decay

        val speed = min(MAX_SPEED, START_SPEED + (elapsed + seconds * 0.5) * ACCELERATION)
        elapsed += seconds
        val radiusY = PLAYER_RADIUS / playfield.yScale
        player = WorldPoint(
            x = (previous.x + speed * seconds).toFloat(),
            y = nextY.toFloat().coerceIn(radiusY, 1f - radiusY),
        )
        extendTrail(previous)
        pot = (pot * exp(profile.growthRate * seconds)).coerceAtMost(MAX_BANK.toDouble())

        val boundary = when {
            nextY <= radiusY -> Impact.Ceiling
            nextY >= 1f - radiusY -> Impact.Floor
            else -> null
        }
        if (boundary != null) {
            crash(boundary)
            return false
        }

        val obstacleIndex = obstacles.indexOfFirst { touches(it) }
        if (obstacleIndex >= 0) {
            if (!hasShield) {
                crash(Impact.Volatility)
                return false
            }
            hasShield = false
            obstacles.removeAt(obstacleIndex)
            burst(player, ParticleColor.Emerald, 24)
        }

        val bonusIndex = bonuses.indexOfFirst { touches(it) }
        if (bonusIndex >= 0) {
            val bonus = bonuses.removeAt(bonusIndex)
            pot = ((pot + bonus.kind.addition) * bonus.kind.multiplier)
                .coerceAtMost(MAX_BANK.toDouble())
            burst(bonus.center, ParticleColor.Gold, 18)
            rewards += FloatingReward(bonus.center, bonus.kind)
        }

        obstacles.removeAll { it.x + it.halfWidth < player.x - 0.51f }
        bonuses.removeAll { it.center.x + RING_RADIUS < player.x - 0.51f }
        fillCourse()
        return bonusIndex >= 0
    }

    private fun resetCourse() {
        player = START
        velocityY = 0.0
        elapsed = 0.0
        shakeRemaining = 0f
        gateIndex = 1
        nextGateX = FIRST_GATE_X + 0.52f
        obstacles.clear()
        bonuses.clear()
        particles.clear()
        rewards.clear()
        trail.clear()
        trail.addAll(STARTING_TRAIL)
        obstacles += Obstacle(FIRST_GATE_X, 0.105f, 0.65f, Edge.Floor)
        bonuses += Bonus(WorldPoint(FIRST_GATE_X - 0.035f, 0.44f), BonusKind.Cash)
        fillCourse()
    }

    private fun fillCourse() {
        while (nextGateX < player.x + 1.1f) {
            val difficulty = (nextGateX / 17f).coerceAtMost(1f)
            val edge = if (courseRandom.nextBoolean()) Edge.Ceiling else Edge.Floor
            val depth = 0.28f + courseRandom.nextFloat() * 0.09f + difficulty * 0.17f
            val halfWidth = 0.074f + courseRandom.nextFloat() * 0.045f + difficulty * 0.017f
            val tip = if (edge == Edge.Floor) 1f - depth else depth
            val direction = if (edge == Edge.Floor) -1f else 1f
            val safeY = (tip + direction * (0.15f + courseRandom.nextFloat() * 0.06f))
                .coerceIn(0.27f, 0.73f)

            obstacles += Obstacle(nextGateX, halfWidth, tip, edge)
            val reward = when {
                gateIndex % 4 == 3 -> BonusKind.Double
                gateIndex % 2 == 1 -> BonusKind.Leverage
                else -> BonusKind.Cash
            }
            bonuses += Bonus(WorldPoint(nextGateX - 0.035f, safeY), reward)
            gateIndex++
            nextGateX += 0.46f + courseRandom.nextFloat() * 0.125f - difficulty * 0.055f
        }
    }

    private fun extendTrail(previous: WorldPoint) {
        var x = trail.last().x + TRAIL_SPACING
        while (x <= player.x) {
            val fraction = (x - previous.x) / (player.x - previous.x)
            trail.addLast(WorldPoint(x, previous.y + (player.y - previous.y) * fraction))
            x += TRAIL_SPACING
        }
        while (trail.first().x < player.x - 0.55f) trail.removeFirst()
    }

    private fun touches(bonus: Bonus): Boolean {
        val dx = (player.x - bonus.center.x) * playfield.xScale
        val dy = (player.y - bonus.center.y) * playfield.yScale
        val radius = RING_RADIUS + PLAYER_RADIUS
        return dx * dx + dy * dy <= radius * radius
    }

    private fun touches(obstacle: Obstacle): Boolean {
        val x = (player.x - obstacle.x) * playfield.xScale
        val y = (player.y - obstacle.tipY) * playfield.yScale
        val halfWidth = obstacle.halfWidth * playfield.xScale
        val height = (obstacle.baseY - obstacle.tipY) * playfield.yScale
        val depth = y / height
        if (abs(x) > halfWidth + PLAYER_RADIUS || y * height < -abs(height) * PLAYER_RADIUS) {
            return false
        }
        if (depth in 0f..1f && abs(x) <= halfWidth * depth) return true

        // By symmetry, the edge on the player's side is the nearest sloped edge.
        val t = ((abs(x) * halfWidth + y * height) / (halfWidth * halfWidth + height * height))
            .coerceIn(0f, 1f)
        val dx = abs(x) - halfWidth * t
        val dy = y - height * t
        return dx * dx + dy * dy <= PLAYER_RADIUS * PLAYER_RADIUS
    }

    private fun crash(impact: Impact) {
        result = RoundResult(pot.toLong(), impact)
        pot = 0.0
        thrust = false
        phase = Phase.Crashed
        shakeRemaining = SHAKE_DURATION
        burst(player, ParticleColor.Coral, 32)
    }

    private fun burst(origin: WorldPoint, color: ParticleColor, count: Int) {
        repeat(count) {
            val angle = effectsRandom.nextDouble(2 * PI)
            val speed = 0.09f + effectsRandom.nextFloat() * 0.35f
            particles += Particle(
                position = origin,
                velocity = WorldPoint(
                    x = cos(angle).toFloat() * speed / playfield.xScale,
                    y = sin(angle).toFloat() * speed / playfield.yScale,
                ),
                color = color,
                radius = 0.003f + effectsRandom.nextFloat() * 0.008f,
                lifetime = 0.32f + effectsRandom.nextFloat() * 0.45f,
            )
        }
    }

    private fun updateEffects(seconds: Float) {
        shakeRemaining = (shakeRemaining - seconds).coerceAtLeast(0f)
        particles.removeAll { it.age + seconds >= it.lifetime }
        for (index in particles.indices) {
            val particle = particles[index]
            particles[index] = particle.copy(
                position = WorldPoint(
                    x = particle.position.x + particle.velocity.x * seconds,
                    y = particle.position.y + particle.velocity.y * seconds,
                ),
                age = particle.age + seconds,
            )
        }
        rewards.removeAll { it.age + seconds >= REWARD_DURATION }
        for (index in rewards.indices) {
            val reward = rewards[index]
            rewards[index] = reward.copy(age = reward.age + seconds)
        }
    }

    private companion object {
        const val MAX_FRAME_SECONDS = 0.08
        const val MAX_STEP_SECONDS = 1.0 / 120.0
        const val ASCENT_SPEED = -0.51
        const val FALL_SPEED = 0.42
        const val FLIGHT_RESPONSE = 7.5
        const val START_SPEED = 0.31
        const val MAX_SPEED = 0.59
        const val ACCELERATION = 0.009
        const val FIRST_GATE_X = 0.52f
        const val TRAIL_SPACING = 0.006f
        val START = WorldPoint(0f, 0.52f)
        val STARTING_TRAIL = listOf(
            WorldPoint(-0.42f, 0.79f),
            WorldPoint(-0.32f, 0.73f),
            WorldPoint(-0.266f, 0.75f),
            WorldPoint(-0.194f, 0.64f),
            WorldPoint(-0.138f, 0.66f),
            WorldPoint(-0.061f, 0.54f),
            WorldPoint(-0.027f, 0.57f),
            START,
        )
    }
}
