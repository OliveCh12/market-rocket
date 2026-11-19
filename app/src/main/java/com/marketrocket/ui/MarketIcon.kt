package com.marketrocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class Symbol {
    Trend, Play, Deposit, Reload, Up, Down, Sliders, Chevron, Shield, Close, Bolt, Back, Pause, Lock,
}

@Composable
internal fun MarketIcon(
    symbol: Symbol,
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val accessible = if (description == null) modifier else {
        modifier.semantics { contentDescription = description }
    }
    Canvas(accessible) {
        val unit = size.minDimension / 24f
        val left = (size.width - 24f * unit) / 2f
        val top = (size.height - 24f * unit) / 2f
        fun dot(x: Float, y: Float) = Offset(left + x * unit, top + y * unit)

        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun trace(vararg coordinates: Float, fill: Boolean = false) {
            val path = Path()
            val first = dot(coordinates[0], coordinates[1])
            path.moveTo(first.x, first.y)
            for (i in 2 until coordinates.size step 2) {
                val point = dot(coordinates[i], coordinates[i + 1])
                path.lineTo(point.x, point.y)
            }
            if (fill) {
                path.close()
                drawPath(path, color)
            } else {
                drawPath(path, color, style = stroke)
            }
        }

        when (symbol) {
            Symbol.Trend -> {
                trace(3f, 18f, 9f, 12f, 13f, 15f, 21f, 6f)
                trace(14f, 6f, 21f, 6f, 21f, 13f)
            }
            Symbol.Play -> trace(8f, 5f, 19f, 12f, 8f, 19f, fill = true)
            Symbol.Deposit -> {
                trace(12f, 4f, 12f, 15f)
                trace(8f, 11f, 12f, 15f, 16f, 11f)
                trace(4f, 15f, 4f, 20f, 20f, 20f, 20f, 15f)
            }
            Symbol.Reload -> {
                drawArc(color, -36f, 298f, useCenter = false, dot(5f, 5f), Size(14f * unit, 14f * unit), style = stroke)
                trace(12f, 2f, 16f, 5f, 12f, 8f)
            }
            Symbol.Up -> {
                trace(12f, 20f, 12f, 4f)
                trace(7f, 9f, 12f, 4f, 17f, 9f)
            }
            Symbol.Down -> {
                trace(12f, 4f, 12f, 20f)
                trace(7f, 15f, 12f, 20f, 17f, 15f)
            }
            Symbol.Sliders -> {
                trace(4f, 7f, 9f, 7f)
                trace(13f, 7f, 20f, 7f)
                trace(4f, 17f, 13f, 17f)
                trace(17f, 17f, 20f, 17f)
                drawCircle(color, 2.1f * unit, dot(11f, 7f), style = stroke)
                drawCircle(color, 2.1f * unit, dot(15f, 17f), style = stroke)
            }
            Symbol.Chevron -> trace(9f, 6f, 15f, 12f, 9f, 18f)
            Symbol.Back -> {
                trace(20f, 12f, 4f, 12f)
                trace(11f, 5f, 4f, 12f, 11f, 19f)
            }
            Symbol.Pause -> {
                trace(9f, 5f, 9f, 19f)
                trace(15f, 5f, 15f, 19f)
            }
            Symbol.Lock -> {
                trace(6f, 10f, 18f, 10f, 18f, 20f, 6f, 20f, 6f, 10f)
                drawArc(color, 180f, 180f, useCenter = false, dot(8f, 4f), Size(8f * unit, 9f * unit), style = stroke)
                drawCircle(color, unit, dot(12f, 15f))
            }
            Symbol.Shield -> {
                trace(12f, 3f, 20f, 6f, 19f, 14f, 16f, 18f, 12f, 21f, 8f, 18f, 5f, 14f, 4f, 6f, 12f, 3f)
                trace(8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f)
            }
            Symbol.Close -> {
                trace(6f, 6f, 18f, 18f)
                trace(18f, 6f, 6f, 18f)
            }
            Symbol.Bolt -> trace(13f, 3f, 5f, 14f, 11f, 14f, 10f, 21f, 19f, 10f, 13f, 10f, 13f, 3f)
        }
    }
}
