package com.marketrocket.game

import kotlin.math.PI
import kotlin.math.abs
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
    private val roundSeeds = Random(seed xor 0x7D0D_8AF3)
    private val effectsRandom = Random(seed xor 0x5F17_8AC4)
    private val obstacles = mutableListOf<Obstacle>()
    private val bonuses = mutableListOf<Bonus>()
    private val trail = ArrayDeque<WorldPoint>()
    private val particles = mutableListOf<Particle>()
    private val rewards = mutableListOf<FloatingReward>()
    private var obstacleSnapshot: List<Obstacle>? = null
    private var bonusSnapshot: List<Bonus>? = null
    private var trailSnapshot: List<WorldPoint>? = null
    private var particleSnapshot: List<Particle>? = null
    private var rewardSnapshot: List<FloatingReward>? = null

    private var profile = initialProfile.normalized()
    private var playfield = Playfield()
    private var phase = Phase.Ready
    private var runNumber = 0
    private var player = START
    private var previousPlayer = START
    private var velocityY = 0.0
    private var flightSteps = 0L
    private var accumulator = 0.0
    private var effectsTime = 0f
    private var bonusIncome = 0L
    private var result: RoundResult? = null
    private var thrust = false
    private var hasShield = profile.shieldLevel > 0
    private var shakeRemaining = 0f
    private var course = CourseGenerator(seed)
    private var nextGate = course.next()
    private var collectedRings = 0
    private var combo = 0
    private var bestCombo = 0
    private var lesson = FlightLesson.Hold
    private var lessonAnchorY = START.y

    init {
        trail.addAll(STARTING_TRAIL)
        fillCourse()
    }

    val needsFrames: Boolean
        get() = phase == Phase.Playing ||
            shakeRemaining > 0f || particles.isNotEmpty() || rewards.isNotEmpty()

    private val pot: Long
        get() = if (phase == Phase.Ready || phase == Phase.Playing) {
            val passiveIncome = flightSteps * profile.incomePerSecond / STEPS_PER_SECOND
            (profile.startingCapital + passiveIncome + bonusIncome).coerceAtMost(MAX_BANK)
        } else {
            0L
        }

    fun snapshot(interpolate: Boolean = false) = GameState(
        phase = phase,
        profile = profile,
        runNumber = runNumber,
        player = if (interpolate && phase == Phase.Playing) interpolatedPlayer() else player,
        trail = trailSnapshot ?: trail.toList().also { trailSnapshot = it },
        obstacles = obstacleSnapshot ?: obstacles.toList().also { obstacleSnapshot = it },
        bonuses = bonusSnapshot ?: bonuses.toList().also { bonusSnapshot = it },
        particles = particleSnapshot ?: particles.toList().also { particleSnapshot = it },
        rewards = rewardSnapshot ?: rewards.toList().also { rewardSnapshot = it },
        roundAmount = pot,
        result = result,
        hasShield = hasShield,
        shakeRemaining = shakeRemaining,
        canCashOut = phase == Phase.Playing && collectedRings > 0,
        effectsTime = effectsTime,
        combo = combo,
        lesson = lesson,
    )

    fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) playfield = Playfield(width.toFloat() / height)
    }

    fun startRound(held: Boolean = false, seed: Int? = null): Boolean {
        if (phase == Phase.Playing) return false
        if (seed != null || phase != Phase.Ready) resetCourse(seed ?: roundSeeds.nextInt())
        phase = Phase.Playing
        runNumber++
        result = null
        thrust = held
        hasShield = profile.shieldLevel > 0
        return true
    }

    fun returnToReady() {
        if (phase == Phase.Ready) return
        resetCourse(roundSeeds.nextInt())
        phase = Phase.Ready
        result = null
        thrust = false
        hasShield = profile.shieldLevel > 0
    }

    fun setThrust(enabled: Boolean) {
        thrust = enabled && phase == Phase.Playing
    }

    fun cashOut(): Boolean {
        if (phase != Phase.Playing || collectedRings == 0) return false
        val payment = min(pot, MAX_BANK - profile.bank)
        val newRecord = payment > profile.stats.bestCashOut
        profile = profile.copy(
            bank = profile.bank + payment,
            lifetimeEarnings = (profile.lifetimeEarnings + payment).coerceAtMost(MAX_BANK),
            stats = profile.stats.record(collectedRings, bestCombo, flightMillis(), payment),
        )
        result = RoundResult(payment, collectedRings, bestCombo, newRecord = newRecord)
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
        if (phase == Phase.Ready) hasShield = profile.shieldLevel > 0
        return true
    }

    /** Advances by seconds and returns the latest feedback event in this display frame. */
    fun advance(deltaTime: Double): GameEvent? {
        if (!needsFrames || !deltaTime.isFinite() || deltaTime <= 0.0) return null

        // Only a bounded portion of a stalled frame enters the simulation.
        val seconds = deltaTime.coerceAtMost(MAX_FRAME_SECONDS)
        updateEffects(seconds.toFloat())
        if (phase != Phase.Playing) return null
        accumulator += seconds
        var feedback: GameEvent? = null
        while (phase == Phase.Playing && accumulator + STEP_EPSILON >= STEP_SECONDS) {
            accumulator = (accumulator - STEP_SECONDS).coerceAtLeast(0.0)
            previousPlayer = player
            feedback = step() ?: feedback
        }
        if (phase != Phase.Playing) accumulator = 0.0
        return feedback
    }

    private fun interpolatedPlayer(): WorldPoint {
        val fraction = (accumulator / STEP_SECONDS).toFloat().coerceIn(0f, 1f)
        return WorldPoint(
            previousPlayer.x + (player.x - previousPlayer.x) * fraction,
            previousPlayer.y + (player.y - previousPlayer.y) * fraction,
        )
    }

    private fun step(): GameEvent? {
        val seconds = STEP_SECONDS
        val targetY = if (thrust) ASCENT_SPEED else FALL_SPEED
        val decay = FLIGHT_DECAY
        val previous = player
        val nextY = previous.y + targetY * seconds +
            (velocityY - targetY) * (1.0 - decay) / FLIGHT_RESPONSE
        velocityY = targetY + (velocityY - targetY) * decay

        val elapsed = (flightSteps + 0.5) * seconds
        val accelerationTime = (elapsed - 2.0).coerceAtLeast(0.0)
        val speed = min(MAX_FLIGHT_SPEED, START_SPEED + elapsed * ACCELERATION + accelerationTime * EXTRA_ACCELERATION)
        flightSteps++
        val radiusY = PLAYER_RADIUS / playfield.yScale
        player = WorldPoint(
            x = (previous.x + speed * seconds).toFloat(),
            y = nextY.toFloat().coerceIn(radiusY, 1f - radiusY),
        )
        extendTrail(previous)

        val boundary = when {
            nextY <= radiusY -> Impact.Ceiling
            nextY >= 1f - radiusY -> Impact.Floor
            else -> null
        }
        if (boundary != null) return crash(boundary)

        var feedback: GameEvent? = null
        val obstacleIndex = obstacles.indexOfFirst { touches(it) }
        if (obstacleIndex >= 0) {
            if (!hasShield) return crash(Impact.Volatility)
            hasShield = false
            combo = 0
            obstacles.removeAt(obstacleIndex)
            obstacleSnapshot = null
            burst(player, ParticleColor.Emerald, 24)
            feedback = GameEvent.Shield
        }

        val bonus = bonuses.firstOrNull()
        if (bonus != null) {
            val ringRadiusX = (bonus.kind.radius + PLAYER_RADIUS) / playfield.xScale
            if (touches(bonus, previous)) {
                bonuses.removeAt(0)
                bonusSnapshot = null
                collectedRings++
                combo++
                bestCombo = maxOf(bestCombo, combo)
                val amount = bonus.payout(combo).coerceAtMost(MAX_BANK - pot)
                bonusIncome += amount
                burst(bonus.center, ParticleColor.Gold, if (bonus.kind == BonusKind.Premium) 25 else 14)
                rewards += FloatingReward(bonus.center, bonus.kind, amount, effectsTime)
                rewardSnapshot = null
                feedback = GameEvent.Ring
            } else if (bonus.center.x < player.x - ringRadiusX) {
                bonuses.removeAt(0)
                bonusSnapshot = null
                combo = 0
            }
        }

        updateLesson()
        if (obstacles.removeAll { it.x + it.halfWidth < player.x - 0.51f }) obstacleSnapshot = null
        fillCourse()
        return feedback
    }

    private fun resetCourse(seed: Int) {
        player = START
        previousPlayer = START
        velocityY = 0.0
        flightSteps = 0L
        accumulator = 0.0
        effectsTime = 0f
        bonusIncome = 0L
        collectedRings = 0
        combo = 0
        bestCombo = 0
        lesson = FlightLesson.Hold
        lessonAnchorY = START.y
        shakeRemaining = 0f
        course = CourseGenerator(seed)
        nextGate = course.next()
        obstacles.clear()
        bonuses.clear()
        particles.clear()
        rewards.clear()
        trail.clear()
        obstacleSnapshot = null
        bonusSnapshot = null
        trailSnapshot = null
        particleSnapshot = null
        rewardSnapshot = null
        trail.addAll(STARTING_TRAIL)
        fillCourse()
    }

    private fun fillCourse() {
        while (nextGate.obstacle.x < player.x + 1.1f) {
            obstacleSnapshot = null
            bonusSnapshot = null
            obstacles += nextGate.obstacle
            nextGate.opposite?.let { obstacles += it }
            bonuses += nextGate.bonus
            nextGate = course.next()
        }
    }

    private fun updateLesson() {
        if (collectedRings > 0) {
            lesson = FlightLesson.CashOut
            return
        }
        when (lesson) {
            FlightLesson.Hold -> {
                lessonAnchorY = maxOf(lessonAnchorY, player.y)
                if (lessonAnchorY - player.y >= 0.035f) {
                    lesson = FlightLesson.Release
                    lessonAnchorY = player.y
                }
            }
            FlightLesson.Release -> {
                lessonAnchorY = minOf(lessonAnchorY, player.y)
                if (player.y - lessonAnchorY >= 0.035f) lesson = FlightLesson.Collect
            }
            FlightLesson.Collect, FlightLesson.CashOut -> Unit
        }
    }

    private fun extendTrail(previous: WorldPoint) {
        var x = trail.last().x + TRAIL_SPACING
        while (x <= player.x) {
            val fraction = (x - previous.x) / (player.x - previous.x)
            trail.addLast(WorldPoint(x, previous.y + (player.y - previous.y) * fraction))
            trailSnapshot = null
            x += TRAIL_SPACING
        }
        while (trail.first().x < player.x - 0.55f) {
            trail.removeFirst()
            trailSnapshot = null
        }
    }

    private fun touches(bonus: Bonus, previous: WorldPoint): Boolean {
        val startX = (previous.x - bonus.center.x) * playfield.xScale
        val startY = (previous.y - bonus.center.y) * playfield.yScale
        val stepX = (player.x - previous.x) * playfield.xScale
        val stepY = (player.y - previous.y) * playfield.yScale
        val lengthSquared = stepX * stepX + stepY * stepY
        val nearest = if (lengthSquared == 0f) 0f else {
            (-(startX * stepX + startY * stepY) / lengthSquared).coerceIn(0f, 1f)
        }
        val dx = startX + nearest * stepX
        val dy = startY + nearest * stepY
        val radius = bonus.kind.radius + PLAYER_RADIUS
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

    private fun flightMillis(): Long = flightSteps * 1_000 / STEPS_PER_SECOND

    private fun crash(impact: Impact): GameEvent {
        result = RoundResult(pot, collectedRings, bestCombo, impact)
        profile = profile.copy(stats = profile.stats.record(collectedRings, bestCombo, flightMillis()))
        combo = 0
        thrust = false
        phase = Phase.Crashed
        shakeRemaining = SHAKE_DURATION
        burst(player, ParticleColor.Coral, 32)
        return GameEvent.Crash
    }

    private fun burst(origin: WorldPoint, color: ParticleColor, count: Int) {
        particleSnapshot = null
        repeat(count) {
            val angle = effectsRandom.nextDouble(2 * PI)
            val speed = 0.09f + effectsRandom.nextFloat() * 0.35f
            particles += Particle(
                origin = origin,
                velocity = WorldPoint(
                    x = cos(angle).toFloat() * speed / playfield.xScale,
                    y = sin(angle).toFloat() * speed / playfield.yScale,
                ),
                color = color,
                radius = 0.003f + effectsRandom.nextFloat() * 0.008f,
                lifetime = 0.32f + effectsRandom.nextFloat() * 0.45f,
                bornAt = effectsTime,
            )
        }
    }

    private fun updateEffects(seconds: Float) {
        effectsTime += seconds
        shakeRemaining = (shakeRemaining - seconds).coerceAtLeast(0f)
        if (particles.removeAll { effectsTime - it.bornAt >= it.lifetime }) particleSnapshot = null
        if (rewards.removeAll { effectsTime - it.bornAt >= REWARD_DURATION }) rewardSnapshot = null
    }

    private companion object {
        const val MAX_FRAME_SECONDS = 0.08
        const val STEPS_PER_SECOND = 120
        const val STEP_SECONDS = 1.0 / STEPS_PER_SECOND
        const val STEP_EPSILON = 1e-10
        const val ASCENT_SPEED = -0.51
        const val FALL_SPEED = 0.42
        const val FLIGHT_RESPONSE = 7.5
        const val START_SPEED = 0.31
        const val ACCELERATION = 0.009
        const val EXTRA_ACCELERATION = 0.012
        const val TRAIL_SPACING = 0.006f
        val FLIGHT_DECAY = exp(-FLIGHT_RESPONSE * STEP_SECONDS)
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
