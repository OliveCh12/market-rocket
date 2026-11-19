package com.marketrocket.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Obsidian = Color(0xFF0A0B0E)
internal val Emerald = Color(0xFF00E676)
internal val Gold = Color(0xFFFFD700)
internal val Coral = Color(0xFFFF3B30)
internal val CoralText = Color(0xFFFF776E)
internal val Mint = Color(0xFF8CFBC3)
internal val WhiteInk = Color(0xFFF0F5F3)
internal val MutedInk = Color(0xFFA5B4B0)
internal val Panel = Color(0xFF13181B)
internal val Line = Color(0xFF283532)
internal val ButtonInk = Color(0xFF052018)

internal val MarketLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.6.sp,
)

internal val MarketMoney = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    fontSize = 30.sp,
    lineHeight = 34.sp,
    letterSpacing = (-1.3).sp,
)

private val marketColors = darkColorScheme(
    primary = Emerald,
    onPrimary = ButtonInk,
    secondary = Gold,
    error = CoralText,
    background = Obsidian,
    onBackground = WhiteInk,
    surface = Panel,
    onSurface = WhiteInk,
    onSurfaceVariant = MutedInk,
    outline = Line,
)

@Composable
internal fun MarketRocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = marketColors, content = content)
}

