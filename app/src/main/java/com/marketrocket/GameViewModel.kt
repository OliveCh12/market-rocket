package com.marketrocket

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
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
    val needsFrames: Boolean get() = engine.needsFrames

    fun resize(width: Int, height: Int) = engine.resize(width, height)

    fun startRound() {
        if (engine.startRound()) publish()
    }

    fun pressFlight(): Boolean = when (currentState.value.phase) {
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

    fun releaseFlight() = engine.setThrust(false)

    fun advance(deltaTime: Double): Boolean {
        val collectedBonus = engine.advance(deltaTime)
        publish()
        return collectedBonus
    }

    fun cashOut(): Boolean {
        if (!engine.cashOut()) return false
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

