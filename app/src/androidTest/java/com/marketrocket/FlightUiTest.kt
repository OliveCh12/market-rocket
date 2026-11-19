package com.marketrocket

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marketrocket.game.Phase
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlightUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun holdAndReleaseCollectsAnRingBeforeCashOut() {
        play()
        clockManually {
            val field = compose.onNodeWithTag("flight-field")
            val activity = compose.activity
            val model = compose.runOnIdle { ViewModelProvider(activity)[GameViewModel::class.java] }
            fun frame() = compose.runOnIdle { model.state.value }
            field.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(580)
            // Release at a flight position, allowing the frame loop to start on a different frame.
            repeat(12) {
                if (frame().player.y > 0.255f) compose.mainClock.advanceTimeByFrame()
            }
            val climb = frame()
            assertTrue("Expected a climb before release: ${climb.phase} ${climb.player}", climb.player.y <= 0.255f)
            compose.onNodeWithText("Un anneau requis").assertIsNotEnabled()

            // The clock above already covered the hold. Advancing event time here would advance it again.
            field.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(670)
            repeat(18) {
                val snapshot = frame()
                if (!snapshot.canCashOut && snapshot.phase == Phase.Playing) {
                    compose.mainClock.advanceTimeByFrame()
                }
            }
            val pickup = frame()
            assertTrue("Expected actual ring contact: ${pickup.phase} ${pickup.player}", pickup.canCashOut)
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText("Encaisser").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(320)
            compose.onNodeWithText("ENCAISSÉ").assertIsDisplayed()
            compose.onNodeWithText("Relancer").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeBy(34)
            compose.onNodeWithText("Un anneau requis").assertIsNotEnabled()
        }
    }

    @Test
    fun backgroundingAndResumingRequiresAnExplicitResume() {
        play()
        clockManually {
            compose.onNodeWithText("Lancer").performClick()
            compose.mainClock.advanceTimeBy(160)
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            compose.mainClock.advanceTimeBy(2_500)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.mainClock.advanceTimeBy(320)

            compose.onNodeWithText("En pause").assertIsDisplayed()
            compose.onNodeWithText("Reprendre").performClick()
            compose.mainClock.advanceTimeBy(60)
            compose.onNodeWithText("Un anneau requis").assertIsNotEnabled()
        }
    }

    @Test
    fun soundSettingAndStatisticsAreReachableAndSurviveRecreation() {
        awaitMenu()
        compose.onNodeWithText("Paramètres").performClick()
        val sound = compose.onNodeWithText("Sons du jeu")
        val previous = sound.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        sound.performClick()
        compose.waitForIdle()
        val updated = sound.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        assertNotEquals(previous, updated)

        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Sons du jeu").fetchSemanticsNodes().isNotEmpty()
        }
        val after = sound.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        org.junit.Assert.assertEquals(updated, after)
        if (updated != ToggleableState.Indeterminate) sound.performClick()
        compose.onNodeWithText("Statistiques et objectifs").performClick()
        compose.onNodeWithText("Gains cumulés").assertIsDisplayed()
    }

    @Test
    fun theFlightFieldIsUsableAfterRotatingTheActivity() {
        play()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(20_000) {
            val node = compose.onNodeWithTag("flight-field").fetchSemanticsNode()
            node.boundsInRoot.width > node.boundsInRoot.height
        }
        compose.onNodeWithText("Lancer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menu principal").assertIsDisplayed()
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun awaitMenu() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Jouer").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun play() {
        awaitMenu()
        compose.onNodeWithText("Jouer").performClick()
        compose.onNodeWithText("Lancer").assertIsEnabled()
    }

    private fun clockManually(test: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            test()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
