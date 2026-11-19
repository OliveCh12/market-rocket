package com.marketrocket.data

import com.marketrocket.game.CareerStats
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.earningsBeforeTracking

internal data class GameSettings(
    val hapticsEnabled: Boolean = true,
    val reduceEffects: Boolean = false,
    val soundEnabled: Boolean = true,
)

internal data class SavedGame(val profile: PlayerProfile, val settings: GameSettings)

internal object SaveKeys {
    const val BANK = "bank_euros"
    const val CAPITAL = "capital_level"
    const val LEVERAGE = "leverage_level"
    const val SHIELD = "shield_level"
    const val EARNINGS = "lifetime_earnings"
    const val ROUNDS = "completed_rounds"
    const val CASH_OUTS = "cash_outs"
    const val RINGS = "rings_collected"
    const val COMBO = "best_combo"
    const val RECORD = "best_cash_out"
    const val FLIGHT = "longest_flight_ms"
    const val HAPTICS = "haptics_enabled"
    const val REDUCE_EFFECTS = "reduce_effects"
    const val SOUND = "sound_enabled"
}

internal fun decodeSave(values: Map<String, *>): SavedGame {
    fun long(key: String) = values[key] as? Long ?: 0L
    fun int(key: String) = values[key] as? Int ?: 0

    val profile = PlayerProfile(
        bank = long(SaveKeys.BANK),
        capitalLevel = int(SaveKeys.CAPITAL),
        leverageLevel = int(SaveKeys.LEVERAGE),
        shieldLevel = int(SaveKeys.SHIELD),
        stats = CareerStats(
            completedRounds = long(SaveKeys.ROUNDS),
            cashOuts = long(SaveKeys.CASH_OUTS),
            rings = long(SaveKeys.RINGS),
            bestCombo = int(SaveKeys.COMBO),
            bestCashOut = long(SaveKeys.RECORD),
            longestFlightMillis = long(SaveKeys.FLIGHT),
        ),
    ).normalized()
    val earnings = values[SaveKeys.EARNINGS] as? Long ?: profile.earningsBeforeTracking()
    return SavedGame(
        profile = profile.copy(lifetimeEarnings = earnings).normalized(),
        settings = GameSettings(
            hapticsEnabled = values[SaveKeys.HAPTICS] as? Boolean ?: true,
            reduceEffects = values[SaveKeys.REDUCE_EFFECTS] as? Boolean ?: false,
            soundEnabled = values[SaveKeys.SOUND] as? Boolean ?: true,
        ),
    )
}
