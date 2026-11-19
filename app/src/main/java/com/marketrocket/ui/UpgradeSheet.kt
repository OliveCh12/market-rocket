package com.marketrocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.Upgrade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeSheet(
    profile: PlayerProfile,
    onBuy: (Upgrade) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14191B),
        contentColor = WhiteInk,
        scrimColor = Color(0xBA050709),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                start = 20.dp, end = 20.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Améliorations", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "BANQUE  ${formatMoney(profile.bank)}",
                        style = MarketLabel.copy(fontSize = 12.sp),
                        color = Mint,
                    )
                }
                IconButton(onClick = onDismiss) {
                    MarketIcon(Symbol.Close, MutedInk, Modifier.size(21.dp), "Fermer les améliorations")
                }
            }
            for (upgrade in Upgrade.entries) {
                UpgradeCard(upgrade, profile, onBuy = { onBuy(upgrade) })
            }
        }
    }
}

@Composable
private fun UpgradeCard(upgrade: Upgrade, profile: PlayerProfile, onBuy: () -> Unit) {
    val price = upgrade.price(profile)
    val level = upgrade.level(profile)
    val nextProfile = if (price == null) profile else upgrade.nextLevel(profile)
    val (symbol, tint, effect) = when (upgrade) {
        Upgrade.Capital -> Triple(
            Symbol.Trend,
            Gold,
            if (price == null) "${formatMoney(profile.startingCapital)} au départ" else
                "${formatMoney(profile.startingCapital)} → ${formatMoney(nextProfile.startingCapital)} au départ",
        )
        Upgrade.Leverage -> Triple(
            Symbol.Bolt,
            Mint,
            if (price == null) "+${growthPercent(profile.growthPerSecond)} / s" else
                "+${growthPercent(profile.growthPerSecond)} → +${growthPercent(nextProfile.growthPerSecond)} / s",
        )
        Upgrade.Shield -> Triple(Symbol.Shield, Color(0xFFBEC6FF), "Absorbe un pic rouge par manche")
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color(0xFF1B2423))
            .border(1.dp, Line, shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(tint.copy(alpha = 0.11f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MarketIcon(symbol, tint, Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(upgrade.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("NIV. $level/${upgrade.maxLevel}", color = tint, style = MarketLabel)
            Text(
                effect,
                color = MutedInk,
                style = MarketLabel.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            )
        }
        if (price == null) {
            Text("MAX", color = tint, style = MarketLabel, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            Button(
                onClick = onBuy,
                enabled = profile.bank >= price,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.semantics {
                    contentDescription = "Acheter ${upgrade.title} pour ${formatMoney(price)}"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mint,
                    contentColor = ButtonInk,
                    disabledContainerColor = Color(0xFF283330),
                    disabledContentColor = MutedInk,
                ),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(formatMoney(price), style = MarketLabel.copy(fontSize = 11.sp, letterSpacing = 0.sp))
            }
        }
    }
}

