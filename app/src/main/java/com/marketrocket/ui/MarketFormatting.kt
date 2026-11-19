package com.marketrocket.ui

import com.marketrocket.game.BonusKind
import kotlin.math.roundToInt

internal fun formatMoney(euros: Long): String {
    if (euros < 1_000L) return "$euros €"
    val unit: Long
    val suffix: String
    when {
        euros >= 999_950_000L -> {
            unit = 1_000_000_000L
            suffix = "Md€"
        }
        euros >= 999_950L -> {
            unit = 1_000_000L
            suffix = "M€"
        }
        else -> {
            unit = 1_000L
            suffix = "k€"
        }
    }
    val tenths = (euros + unit / 20) / (unit / 10)
    val whole = tenths / 10
    val decimal = tenths % 10
    return if (decimal == 0L) "$whole $suffix" else "$whole,$decimal $suffix"
}

internal fun growthPercent(growth: Double): String = "${(growth * 100).roundToInt()} %"

internal fun BonusKind.label(): String = when (this) {
    BonusKind.Cash -> "+${formatMoney(addition)}"
    BonusKind.Leverage -> "×1,5"
    BonusKind.Double -> "×2"
}
