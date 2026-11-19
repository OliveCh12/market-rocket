package com.marketrocket

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marketrocket.ui.GameScreen
import com.marketrocket.ui.MarketRocketTheme
import com.marketrocket.ui.Obsidian

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Obsidian.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Obsidian.toArgb()),
        )
        preferHighRefreshRate()
        setContent {
            MarketRocketTheme {
                GameScreen(viewModel<GameViewModel>())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun preferHighRefreshRate() {
        val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        } ?: return
        val current = screen.mode
        val preferred = screen.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight && it.refreshRate <= 121f
            }
            .maxByOrNull { it.refreshRate }
        if (preferred != null && preferred.refreshRate > current.refreshRate) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = preferred.modeId }
        }
    }
}

