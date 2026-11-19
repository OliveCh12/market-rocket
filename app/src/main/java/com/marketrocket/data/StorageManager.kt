package com.marketrocket.data

import android.content.Context
import androidx.core.content.edit
import com.marketrocket.game.PlayerProfile

internal class StorageManager(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences("market_rocket", Context.MODE_PRIVATE)

    fun load(): PlayerProfile {
        val values = preferences.all
        return PlayerProfile(
            bank = (values[BANK] as? Long) ?: 0L,
            capitalLevel = (values[CAPITAL] as? Int) ?: 0,
            leverageLevel = (values[LEVERAGE] as? Int) ?: 0,
            shieldLevel = (values[SHIELD] as? Int) ?: 0,
        ).normalized()
    }

    fun save(profile: PlayerProfile) {
        preferences.edit {
            putLong(BANK, profile.bank)
            putInt(CAPITAL, profile.capitalLevel)
            putInt(LEVERAGE, profile.leverageLevel)
            putInt(SHIELD, profile.shieldLevel)
        }
    }

    private companion object {
        const val BANK = "bank_euros"
        const val CAPITAL = "capital_level"
        const val LEVERAGE = "leverage_level"
        const val SHIELD = "shield_level"
    }
}

