package com.marketrocket

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
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
        if (paused) return false
        val collectedBonus = engine.advance(deltaTime)
        publish()
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

    private fun publish(save: Boolean = false) {
        val snapshot = engine.snapshot()
        if (save) storage.save(snapshot.profile)
        currentState.value = snapshot
    }
}

