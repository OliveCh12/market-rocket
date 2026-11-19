package com.marketrocket.data

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.Upgrade
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMigrationTest {
    @Test
    fun legacyBankUpgradesAndSettingsAreCopiedWithoutDeletingTheSource() = runBlocking {
        val legacy = mutableMapOf<String, Any>(
            SaveKeys.BANK to 4_000L,
            SaveKeys.CAPITAL to 1,
            SaveKeys.LEVERAGE to 1,
            SaveKeys.SHIELD to 1,
            SaveKeys.HAPTICS to false,
            SaveKeys.REDUCE_EFFECTS to true,
        )
        val original = legacy.toMap()
        val migration = LegacySaveMigration { legacy }
        val empty = mutablePreferencesOf()
        assertTrue(migration.shouldMigrate(empty))

        val migrated = migration.migrate(empty)
        migration.cleanUp()
        val loaded = decodeSave(migrated.asMap().mapKeys { it.key.name })
        assertEquals(4_000L, loaded.profile.bank)
        assertEquals(11_000L, loaded.profile.lifetimeEarnings)
        assertEquals(205L, loaded.profile.startingCapital)
        assertEquals(1, loaded.profile.leverageLevel)
        assertEquals(1, loaded.profile.shieldLevel)
        assertEquals("Freelance", loaded.profile.tierProgress.current.name)
        assertEquals(GameSettings(hapticsEnabled = false, reduceEffects = true), loaded.settings)
        assertEquals(original, legacy)
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun anExistingDataStoreValueHasPriorityOverTheLegacyFile() = runBlocking {
        val existing = mutablePreferencesOf(longPreferencesKey(SaveKeys.BANK) to 1_250L)
        val migration = LegacySaveMigration { error("An existing DataStore must not read the legacy file") }
        val migrated = migration.migrate(existing)
        assertEquals(1_250L, migrated[longPreferencesKey(SaveKeys.BANK)])
        assertFalse(migration.shouldMigrate(migrated))
    }

    @Test
    fun decoderSanitizesLegacyValuesAndKeepsRecordedTotals() {
        val defaults = decodeSave(mapOf("unrelated" to "value"))
        assertEquals(PlayerProfile(), defaults.profile)
        val corrupt = decodeSave(mapOf(SaveKeys.BANK to -100L, SaveKeys.CAPITAL to Int.MAX_VALUE))
        assertEquals(0L, corrupt.profile.bank)
        assertEquals(Upgrade.Capital.maxLevel, corrupt.profile.capitalLevel)
        val tracked = decodeSave(mapOf(SaveKeys.BANK to 4_000L, SaveKeys.CAPITAL to 1, SaveKeys.EARNINGS to 6_000L))
        assertEquals(6_000L, tracked.profile.lifetimeEarnings)
    }

    @Test
    fun missingLifetimeEarningsUseThePricesActuallyPaidByLegacyPlayers() {
        val loaded = decodeSave(mapOf<String, Any>(
            SaveKeys.BANK to 1_000L,
            SaveKeys.CAPITAL to 2,
            SaveKeys.LEVERAGE to 2,
        ))
        assertEquals(1_000L, loaded.profile.bank)
        assertEquals(9_600L, loaded.profile.lifetimeEarnings)
        assertEquals(260L, loaded.profile.startingCapital)
        assertEquals(20L, loaded.profile.incomePerSecond)
    }
}
