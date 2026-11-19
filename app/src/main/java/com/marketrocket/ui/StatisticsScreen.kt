package com.marketrocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.wealthTiers

@Composable
internal fun StatisticsScreen(profile: PlayerProfile, onBack: () -> Unit) {
    val stats = profile.stats
    val duration = stats.longestFlightMillis / 1_000
    val nextMilestone = wealthTiers[1]
    MenuPage("Statistiques", onBack) {
        StatLine("Gains cumulés", formatMoney(profile.lifetimeEarnings))
        StatLine("Meilleur encaissement", formatMoney(stats.bestCashOut))
        StatLine("Plus longue série", stats.bestCombo.toString())
        StatLine("Plus long vol", "${duration / 60} min ${duration % 60} s")
        StatLine("Manches terminées", stats.completedRounds.toString())
        StatLine("Anneaux collectés", stats.rings.toString())
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Line)
        Text("Objectifs", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = WhiteInk)
        Objective("Premier encaissement", "${stats.cashOuts.coerceAtMost(1)} / 1", stats.cashOuts > 0L)
        Objective("Trois anneaux d’affilée", "${stats.bestCombo.coerceAtMost(3)} / 3", stats.bestCombo >= 3)
        Objective("Huit anneaux d’affilée", "${stats.bestCombo.coerceAtMost(8)} / 8", stats.bestCombo >= 8)
        Objective("Voler une minute", "${duration.coerceAtMost(60)} / 60 s", duration >= 60)
        Objective(
            "Atteindre le palier ${nextMilestone.name}",
            "${formatMoney(profile.lifetimeEarnings.coerceAtMost(nextMilestone.threshold))} / ${formatMoney(nextMilestone.threshold)}",
            profile.lifetimeEarnings >= nextMilestone.threshold,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MutedInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            value, color = WhiteInk, style = MarketLabel.copy(fontSize = 13.sp, lineHeight = 18.sp),
            textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 152.dp),
        )
    }
}

@Composable
private fun Objective(label: String, progress: String, complete: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .semantics { stateDescription = if (complete) "Atteint" else "En cours" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WhiteInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            if (complete) "Atteint" else progress,
            color = if (complete) Mint else MutedInk,
            style = MarketLabel,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 144.dp),
        )
    }
}
