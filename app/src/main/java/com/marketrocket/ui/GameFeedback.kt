package com.marketrocket.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.marketrocket.R
import com.marketrocket.data.GameSettings
import com.marketrocket.game.GameEvent

@Composable
internal fun rememberGameFeedback(settings: GameSettings): (GameEvent) -> Unit {
    val context = LocalContext.current.applicationContext
    val haptics = LocalHapticFeedback.current
    var sounds by remember { mutableStateOf<FlightSounds?>(null) }
    LifecycleResumeEffect(settings.soundEnabled) {
        val player = if (settings.soundEnabled) FlightSounds(context) else null
        sounds = player
        onPauseOrDispose {
            sounds = null
            player?.close()
        }
    }
    return remember(sounds, haptics, settings.hapticsEnabled) {
        { event ->
            sounds?.play(event)
            if (settings.hapticsEnabled) haptics.performHapticFeedback(feedbackType(event))
        }
    }
}

private fun feedbackType(event: GameEvent): HapticFeedbackType = when (event) {
    GameEvent.Ring, GameEvent.Shield -> when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackType.SegmentTick
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackType.TextHandleMove
        else -> HapticFeedbackType.LongPress
    }
    GameEvent.Crash -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackType.Reject
    } else {
        HapticFeedbackType.LongPress
    }
    GameEvent.CashOut -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackType.Confirm
    } else {
        HapticFeedbackType.LongPress
    }
}

private class FlightSounds(context: Context) {
    private val ready = mutableSetOf<Int>()
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .apply { setOnLoadCompleteListener { _, sample, status -> if (status == 0) ready += sample } }
    private val ring = pool.load(context, R.raw.flight_ring, 1)
    private val crash = pool.load(context, R.raw.flight_crash, 1)
    private val cash = pool.load(context, R.raw.flight_cash, 1)

    fun play(event: GameEvent) {
        val sample = when (event) {
            GameEvent.Ring, GameEvent.Shield -> ring
            GameEvent.Crash -> crash
            GameEvent.CashOut -> cash
        }
        if (sample in ready) {
            val pitch = if (event == GameEvent.Shield) 0.7f else 1f
            pool.play(sample, 0.22f, 0.22f, 1, 0, pitch)
        }
    }

    fun close() = pool.release()
}
