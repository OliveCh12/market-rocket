package com.marketrocket.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val STORE_NAME = "market_rocket"
private val schema = intPreferencesKey("save_schema")

private val Context.rocketDataStore by preferencesDataStore(
    name = STORE_NAME,
    produceMigrations = { context ->
        listOf(LegacySaveMigration { context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE).all })
    },
)

internal class StorageManager(context: Context) {
    private val store = context.applicationContext.rocketDataStore

    suspend fun load(): SavedGame = disk.withLock {
        decodeSave(store.data.first().asMap().mapKeys { it.key.name })
    }

    // Queue in call order and let an accepted write finish across Activity recreation.
    fun save(value: SavedGame): Deferred<Unit> = persistenceScope.async(start = CoroutineStart.UNDISPATCHED) {
        disk.withLock { store.edit { it.writeSave(value) } }
    }

    private companion object {
        val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = Mutex()
    }
}

internal class LegacySaveMigration(private val legacy: () -> Map<String, *>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[schema] == null

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        if (currentData.asMap().isEmpty()) writeSave(decodeSave(legacy()))
        this[schema] = 1
    }

    override suspend fun cleanUp() = Unit
}

private fun MutablePreferences.writeSave(value: SavedGame) {
    val profile = value.profile
    val stats = profile.stats
    val settings = value.settings
    this[longPreferencesKey(SaveKeys.BANK)] = profile.bank
    this[intPreferencesKey(SaveKeys.CAPITAL)] = profile.capitalLevel
    this[intPreferencesKey(SaveKeys.LEVERAGE)] = profile.leverageLevel
    this[intPreferencesKey(SaveKeys.SHIELD)] = profile.shieldLevel
    this[longPreferencesKey(SaveKeys.EARNINGS)] = profile.lifetimeEarnings
    this[longPreferencesKey(SaveKeys.ROUNDS)] = stats.completedRounds
    this[longPreferencesKey(SaveKeys.CASH_OUTS)] = stats.cashOuts
    this[longPreferencesKey(SaveKeys.RINGS)] = stats.rings
    this[intPreferencesKey(SaveKeys.COMBO)] = stats.bestCombo
    this[longPreferencesKey(SaveKeys.RECORD)] = stats.bestCashOut
    this[longPreferencesKey(SaveKeys.FLIGHT)] = stats.longestFlightMillis
    this[booleanPreferencesKey(SaveKeys.HAPTICS)] = settings.hapticsEnabled
    this[booleanPreferencesKey(SaveKeys.REDUCE_EFFECTS)] = settings.reduceEffects
    this[booleanPreferencesKey(SaveKeys.SOUND)] = settings.soundEnabled
}
