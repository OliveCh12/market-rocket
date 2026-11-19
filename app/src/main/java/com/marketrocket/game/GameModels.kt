package com.marketrocket.game

import kotlin.math.exp
import kotlin.math.max

internal const val MAX_BANK = 9_000_000_000_000_000L
internal const val PLAYER_RADIUS = 0.018f
internal const val RING_RADIUS = 0.046f
internal const val SHAKE_DURATION = 0.32f
internal const val REWARD_DURATION = 0.86f

internal data class WealthTier(val name: String, val threshold: Long)

internal val wealthTiers = listOf(
    WealthTier("Stagiaire", 0L),
    WealthTier("Freelance", 5_000L),
    WealthTier("Fondateur Startup", 50_000L),
    WealthTier("CEO", 500_000L),
    WealthTier("Titan Hedge Fund", 10_000_000L),
    WealthTier("Milliardaire", 1_000_000_000L),
)

internal data class TierProgress(
    val current: WealthTier,
    val next: WealthTier?,
    val fraction: Float,
)

internal data class PlayerProfile(
    val bank: Long = 0L,
    val capitalLevel: Int = 0,
    val leverageLevel: Int = 0,
    val shieldLevel: Int = 0,
    val lifetimeEarnings: Long = bank,
    val stats: CareerStats = CareerStats(),
) {
    val startingCapital: Long get() = 250L shl capitalLevel
    val growthRate: Double get() = 0.29 + 0.105 * leverageLevel
    val growthPerSecond: Double get() = exp(growthRate) - 1.0

    val tierProgress: TierProgress
        get() {
            val index = wealthTiers.indexOfLast { lifetimeEarnings >= it.threshold }.coerceAtLeast(0)
            val current = wealthTiers[index]
            val next = wealthTiers.getOrNull(index + 1)
            val fraction = if (next == null) {
                1f
            } else {
                (lifetimeEarnings - current.threshold).toFloat() / (next.threshold - current.threshold)
            }
            return TierProgress(current, next, fraction.coerceIn(0f, 1f))
        }

    fun normalized(): PlayerProfile {
        val available = bank.coerceIn(0L, MAX_BANK)
        return copy(
            bank = available,
            capitalLevel = capitalLevel.coerceIn(0, Upgrade.Capital.maxLevel),
            leverageLevel = leverageLevel.coerceIn(0, Upgrade.Leverage.maxLevel),
            shieldLevel = shieldLevel.coerceIn(0, Upgrade.Shield.maxLevel),
            lifetimeEarnings = lifetimeEarnings.coerceIn(available, MAX_BANK),
            stats = stats.normalized(),
        )
    }
}

internal data class CareerStats(
    val completedRounds: Long = 0L,
    val cashOuts: Long = 0L,
    val rings: Long = 0L,
    val bestCombo: Int = 0,
    val bestCashOut: Long = 0L,
    val longestFlightMillis: Long = 0L,
) {
    fun record(rings: Int, combo: Int, flightMillis: Long, payment: Long? = null) = copy(
        completedRounds = (completedRounds + 1).coerceAtMost(MAX_BANK),
        cashOuts = (cashOuts + if (payment == null) 0 else 1).coerceAtMost(MAX_BANK),
        rings = (this.rings + rings).coerceAtMost(MAX_BANK),
        bestCombo = maxOf(bestCombo, combo),
        bestCashOut = maxOf(bestCashOut, payment ?: 0L),
        longestFlightMillis = maxOf(longestFlightMillis, flightMillis),
    )

    fun normalized(): CareerStats {
        val complete = completedRounds.coerceIn(0, MAX_BANK)
        return copy(
            completedRounds = complete,
            cashOuts = cashOuts.coerceIn(0, complete),
            rings = rings.coerceIn(0, MAX_BANK),
            bestCombo = bestCombo.coerceAtLeast(0),
            bestCashOut = bestCashOut.coerceIn(0, MAX_BANK),
            longestFlightMillis = longestFlightMillis.coerceAtLeast(0L),
        )
    }
}

internal enum class Upgrade(val title: String, private val prices: List<Long>) {
    Capital(
        "Capital de départ",
        listOf(750L, 2_400L, 8_000L, 28_000L, 100_000L, 360_000L, 1_300_000L, 4_800_000L),
    ),
    Leverage(
        "Effet de levier",
        listOf(1_250L, 4_200L, 14_000L, 46_000L, 150_000L, 500_000L, 1_650_000L, 5_500_000L),
    ),
    Shield("Bouclier Anti-Krach", listOf(5_000L));

    val maxLevel: Int get() = prices.size

    fun level(profile: PlayerProfile): Int = when (this) {
        Capital -> profile.capitalLevel
        Leverage -> profile.leverageLevel
        Shield -> profile.shieldLevel
    }

    fun price(profile: PlayerProfile): Long? = prices.getOrNull(level(profile))

    fun totalSpent(profile: PlayerProfile): Long = prices.take(level(profile)).sum()

    fun nextLevel(profile: PlayerProfile): PlayerProfile = when (this) {
        Capital -> profile.copy(capitalLevel = profile.capitalLevel + 1)
        Leverage -> profile.copy(leverageLevel = profile.leverageLevel + 1)
        Shield -> profile.copy(shieldLevel = 1)
    }
}

internal fun PlayerProfile.earningsBeforeTracking(): Long =
    (bank + Upgrade.entries.sumOf { it.totalSpent(this) }).coerceAtMost(MAX_BANK)

internal enum class Phase { Ready, Playing, CashedOut, Crashed }

internal enum class GameEvent { Ring, Shield, Crash, CashOut }

internal enum class FlightLesson { Hold, Release, Collect, CashOut }

internal enum class Impact { Ceiling, Floor, Volatility }

internal enum class Edge { Ceiling, Floor }

internal enum class BonusKind(val addition: Long, val multiplier: Double) {
    Cash(250L, 1.0),
    Leverage(0L, 1.5),
    Double(0L, 2.0);

    fun collect(pot: Double, combo: Int): Double {
        val bonus = addition + pot * (multiplier - 1.0)
        val streakBoost = 1.0 + (combo - 1).coerceIn(0, 5) * 0.1
        return (pot + bonus * streakBoost).coerceAtMost(MAX_BANK.toDouble())
    }
}

internal enum class ParticleColor { Gold, Emerald, Coral }

// X is measured in viewport widths and increases without scrolling the physics world.
// Y runs from the ceiling (0) to the floor (1).
internal data class WorldPoint(val x: Float, val y: Float)

internal data class Playfield(val aspectRatio: Float = 1f) {
    // Convert normalized X/Y differences to units of the viewport's shorter edge.
    val xScale = max(1f, aspectRatio)
    val yScale = max(1f, 1f / aspectRatio)
}

internal data class Obstacle(
    val x: Float,
    val halfWidth: Float,
    val tipY: Float,
    val edge: Edge,
) {
    val baseY: Float get() = if (edge == Edge.Floor) 1f else 0f
}

internal data class Bonus(
    val center: WorldPoint,
    val kind: BonusKind,
)

internal data class Particle(
    val origin: WorldPoint,
    val velocity: WorldPoint,
    val color: ParticleColor,
    val radius: Float,
    val lifetime: Float,
    val bornAt: Float,
)

internal data class FloatingReward(
    val position: WorldPoint,
    val kind: BonusKind,
    val bornAt: Float,
)

internal data class RoundResult(
    val amount: Long,
    val impact: Impact? = null,
    val newRecord: Boolean = false,
)

internal data class GameState(
    val phase: Phase,
    val profile: PlayerProfile,
    val runNumber: Int,
    val player: WorldPoint,
    val trail: List<WorldPoint>,
    val obstacles: List<Obstacle>,
    val bonuses: List<Bonus>,
    val particles: List<Particle>,
    val rewards: List<FloatingReward>,
    val roundAmount: Long,
    val result: RoundResult?,
    val hasShield: Boolean,
    val shakeRemaining: Float,
    val canCashOut: Boolean,
    val effectsTime: Float,
    val combo: Int,
    val lesson: FlightLesson,
) {
    val needsFrames: Boolean
        get() = phase == Phase.Playing ||
            shakeRemaining > 0f || particles.isNotEmpty() || rewards.isNotEmpty()
}

