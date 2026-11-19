package com.marketrocket.ui

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
