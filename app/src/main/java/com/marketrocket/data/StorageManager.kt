package com.marketrocket.data

import android.content.Context
import androidx.core.content.edit
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.earningsBeforeTracking

internal data class GameSettings(
    val hapticsEnabled: Boolean = true,
    val reduceEffects: Boolean = false,
)

internal class StorageManager(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("market_rocket", Context.MODE_PRIVATE)

    fun load(): PlayerProfile {
        val values = preferences.all
        val profile = PlayerProfile(
            bank = (values[BANK] as? Long) ?: 0L,
            capitalLevel = (values[CAPITAL] as? Int) ?: 0,
            leverageLevel = (values[LEVERAGE] as? Int) ?: 0,
            shieldLevel = (values[SHIELD] as? Int) ?: 0,
        ).normalized()
        return profile.copy(
            lifetimeEarnings = (values[EARNINGS] as? Long) ?: profile.earningsBeforeTracking(),
        ).normalized()
    }

    fun save(profile: PlayerProfile) {
        preferences.edit {
            putLong(BANK, profile.bank)
            putInt(CAPITAL, profile.capitalLevel)
            putInt(LEVERAGE, profile.leverageLevel)
            putInt(SHIELD, profile.shieldLevel)
            putLong(EARNINGS, profile.lifetimeEarnings)
        }
    }

    fun loadSettings(): GameSettings {
        val values = preferences.all
        return GameSettings(
            hapticsEnabled = values[HAPTICS] as? Boolean ?: true,
            reduceEffects = values[REDUCE_EFFECTS] as? Boolean ?: false,
        )
    }

    fun saveSettings(settings: GameSettings) {
        preferences.edit {
            putBoolean(HAPTICS, settings.hapticsEnabled)
            putBoolean(REDUCE_EFFECTS, settings.reduceEffects)
        }
    }

    private companion object {
        const val BANK = "bank_euros"
        const val CAPITAL = "capital_level"
        const val LEVERAGE = "leverage_level"
        const val SHIELD = "shield_level"
        const val EARNINGS = "lifetime_earnings"
        const val HAPTICS = "haptics_enabled"
        const val REDUCE_EFFECTS = "reduce_effects"
    }
}

