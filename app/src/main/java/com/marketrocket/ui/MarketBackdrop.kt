package com.marketrocket.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.marketrocket.game.GameState
import kotlin.math.pow

@Composable
internal fun MarketBackdrop(
    modifier: Modifier,
    state: State<GameState>? = null,
    reduceEffects: Boolean = false,
) {
    Spacer(
        modifier.drawWithCache {
            val sky = Brush.verticalGradient(
                0f to Obsidian,
                0.47f to Color(0xFF102129),
                0.72f to Color(0xFF0B1B20),
                1f to Obsidian,
            )
            val light = Brush.radialGradient(
                colors = listOf(Color(0x403D918B), Color.Transparent),
                center = Offset(size.width * 0.71f, size.height * 0.54f),
                radius = (size.maxDimension * 0.62f).coerceAtLeast(1f),
            )
            val vignette = Brush.verticalGradient(
                0f to Obsidian.copy(alpha = 0.92f),
                0.2f to Color.Transparent,
                0.74f to Color.Transparent,
                1f to Obsidian.copy(alpha = 0.88f),
            )
            onDrawBehind {
                val distance = if (reduceEffects) 0f else state?.value?.player?.x ?: 0f
                drawRect(sky)
                drawRect(light)
                drawDistantGrid(distance)
                drawPerspectiveFloor(distance)
                drawRect(vignette)
            }
        },
    )
}

private fun DrawScope.drawDistantGrid(distance: Float) {
    val step = size.width / 12f
    if (step <= 0f) return
    val phase = -(distance * size.width * 0.16f) % step
    val grid = Color(0xFF54868B)
    for (index in 0..13) {
        val x = index * step + phase
        drawLine(grid.copy(alpha = 0.13f), Offset(x, 0f), Offset(x, size.height))
    }
    for (index in 1..16) {
        val y = size.height * index / 17f
        val alpha = if (index % 4 == 0) 0.15f else 0.075f
        drawLine(grid.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y))
    }
}

private fun DrawScope.drawPerspectiveFloor(distance: Float) {
    val horizon = size.height * 0.54f
    val vanishingX = size.width * 0.67f
    val step = size.width / 9f
    if (step <= 0f) return
    val phase = -(distance * size.width * 0.52f) % step
    val cyan = Color(0xFF71C4C6)

    for (index in -9..18) {
        val x = index * step + phase
        drawLine(
            cyan.copy(alpha = 0.13f),
            Offset(vanishingX + (x - vanishingX) * 0.13f, horizon),
            Offset(x, size.height),
        )
    }
    for (index in 1..11) {
        val depth = (index / 11f).pow(2.5f)
        val y = horizon + (size.height - horizon) * depth
        drawLine(cyan.copy(alpha = 0.09f + depth * 0.14f), Offset(0f, y), Offset(size.width, y))
    }
    drawLine(cyan.copy(alpha = 0.18f), Offset(0f, horizon), Offset(size.width, horizon))
}
