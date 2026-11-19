package com.marketrocket

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.marketrocket.data.GameSettings
import com.marketrocket.data.StorageManager
import com.marketrocket.game.GameEngine
import com.marketrocket.game.GameState
import com.marketrocket.game.Phase
import com.marketrocket.game.Upgrade

internal class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = StorageManager(application)
    private val engine = GameEngine(storage.load())
    private val currentState = mutableStateOf(engine.snapshot())
    val state: State<GameState> = currentState
    private val displayedPot = mutableLongStateOf(currentState.value.roundAmount)
    val displayedRoundAmount: LongState = displayedPot
    private var hudElapsed = 0.0
    var settings by mutableStateOf(storage.loadSettings())
        private set
    var paused by mutableStateOf(false)
        private set
    val needsFrames: Boolean get() = !paused && engine.needsFrames

    fun updateSettings(value: GameSettings) {
        settings = value
        storage.saveSettings(value)
    }

    fun togglePause() {
        if (currentState.value.phase != Phase.Playing) return
        releaseFlight()
        paused = !paused
    }

    fun pauseForInterruption() {
        if (currentState.value.phase == Phase.Playing && !paused) togglePause()
    }

    fun leaveGame() {
        paused = false
        engine.returnToReady()
        publish()
    }

    fun resize(width: Int, height: Int) = engine.resize(width, height)

    fun startRound() {
        if (engine.startRound()) publish()
    }

    fun pressFlight(): Boolean {
        if (paused) return false
        return when (currentState.value.phase) {
            Phase.Ready -> {
                engine.startRound(held = true)
                publish()
                true
            }
            Phase.Playing -> {
                engine.setThrust(true)
                true
            }
            Phase.CashedOut, Phase.Crashed -> false
        }
    }

    fun releaseFlight() = engine.setThrust(false)

    fun advance(deltaTime: Double): Boolean {
        if (paused || !deltaTime.isFinite() || deltaTime <= 0.0) return false
        val collectedBonus = engine.advance(deltaTime)
        hudElapsed += deltaTime.coerceAtMost(0.08)
        publish(refreshPot = collectedBonus || hudElapsed >= 1.0 / 15.0)
        return collectedBonus
    }

    fun cashOut(): Boolean {
        if (paused || !engine.cashOut()) return false
        publish(save = true)
        return true
    }

    fun buy(upgrade: Upgrade) {
        if (engine.buy(upgrade)) publish(save = true)
    }

    private fun publish(save: Boolean = false, refreshPot: Boolean = true) {
        val snapshot = engine.snapshot(interpolate = true)
        if (save) storage.save(snapshot.profile)
        if (refreshPot || snapshot.phase != currentState.value.phase) {
            displayedPot.longValue = snapshot.roundAmount
            hudElapsed = 0.0
        }
        currentState.value = snapshot
    }
}

