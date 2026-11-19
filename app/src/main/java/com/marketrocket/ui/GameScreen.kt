package com.marketrocket.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.marketrocket.GameViewModel
import com.marketrocket.game.GameState
import com.marketrocket.game.Impact
import com.marketrocket.game.Phase
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.RoundResult
import kotlinx.coroutines.isActive

private val bankShape = RoundedCornerShape(18.dp)
private val chartShape = RoundedCornerShape(20.dp)
private val bonusHaptic = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HapticFeedbackType.SegmentTick
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> HapticFeedbackType.TextHandleMove
    else -> HapticFeedbackType.LongPress
}
private val cashOutHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    HapticFeedbackType.Confirm
} else {
    HapticFeedbackType.LongPress
}

@Composable
internal fun GameScreen(viewModel: GameViewModel) {
    val state = viewModel.state
    val phase by remember(state) { derivedStateOf { state.value.phase } }
    val profile by remember(state) { derivedStateOf { state.value.profile } }
    val runNumber by remember(state) { derivedStateOf { state.value.runNumber } }
    val result by remember(state) { derivedStateOf { state.value.result } }
    val haptics = LocalHapticFeedback.current
    var shopOpen by rememberSaveable { mutableStateOf(false) }

    RunFrameClock(viewModel)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Obsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val compact = maxHeight < 720.dp
        val short = maxHeight < 610.dp
        val gutter = if (maxWidth < 365.dp) 16.dp else 22.dp
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(3f)
                    .fillMaxWidth()
                    .pointerInput(viewModel, shopOpen) {
                        if (shopOpen) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (viewModel.pressFlight()) {
                                down.consume()
                                try {
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                        change.consume()
                                    }
                                } finally {
                                    viewModel.releaseFlight()
                                }
                            }
                        }
                    }
                    .padding(start = gutter, end = gutter, top = if (compact) 6.dp else 12.dp),
            ) {
                Header(phase, compact)
                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                BankHud(profile, compact)
                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                ChartPanel(
                    state = state,
                    phase = phase,
                    runNumber = runNumber,
                    result = result,
                    onResize = viewModel::resize,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            ControlDeck(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter),
                state = state,
                phase = phase,
                result = result,
                short = short,
                compact = compact,
                onAction = {
                    if (phase == Phase.Playing) {
                        if (viewModel.cashOut()) haptics.performHapticFeedback(cashOutHaptic)
                    } else {
                        viewModel.startRound()
                    }
                },
                onShop = { shopOpen = true },
            )
        }
    }
    if (shopOpen && phase != Phase.Playing) {
        UpgradeSheet(profile, onBuy = viewModel::buy, onDismiss = { shopOpen = false })
    }
}

@Composable
private fun RunFrameClock(viewModel: GameViewModel) {
    val state = viewModel.state
    val ticking by remember(state) { derivedStateOf { state.value.needsFrames } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel, lifecycle, haptics, ticking) {
        if (!ticking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            try {
                while (isActive && viewModel.needsFrames) {
                    val now = withFrameNanos { it }
                    if (viewModel.advance((now - previous) / 1_000_000_000.0)) {
                        haptics.performHapticFeedback(bonusHaptic)
                    }
                    previous = now
                }
            } finally {
                viewModel.releaseFlight()
            }
        }
    }
}

@Composable
private fun Header(phase: Phase, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                Modifier.size(if (compact) 30.dp else 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Emerald),
                contentAlignment = Alignment.Center,
            ) {
                MarketIcon(Symbol.Trend, ButtonInk, Modifier.size(if (compact) 18.dp else 22.dp))
            }
            Text(
                "MARKET ROCKET",
                color = WhiteInk,
                fontSize = if (compact) 17.sp else 19.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.65.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val (label, tint) = when (phase) {
            Phase.Ready -> "PRÊT" to MutedInk
            Phase.Playing -> "EN VOL" to Emerald
            Phase.CashedOut -> "SOLDÉE" to Mint
            Phase.Crashed -> "KRACH" to CoralText
        }
        Row(
            Modifier.clip(CircleShape)
                .background(tint.copy(alpha = 0.085f))
                .border(1.dp, tint.copy(alpha = 0.22f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(5.dp).background(tint, CircleShape))
            Text(label, color = tint, style = MarketLabel, maxLines = 1)
        }
    }
}

@Composable
private fun BankHud(profile: PlayerProfile, compact: Boolean) {
    val progression = remember(profile) { profile.tierProgress }
    Column(
        Modifier.fillMaxWidth()
            .clip(bankShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF18211F), Panel)))
            .border(1.dp, Line, bankShape)
            .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(0.9f)) {
                Text("SOLDE EN BANQUE", style = MarketLabel, color = MutedInk)
                Text(
                    formatMoney(profile.bank),
                    color = WhiteInk,
                    style = if (compact) MarketMoney.copy(fontSize = 24.sp, lineHeight = 29.sp) else MarketMoney,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("STATUT", style = MarketLabel, color = MutedInk)
                Text(
                    progression.current.name,
                    color = Mint,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0xFF2D3A37))
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progression.fraction, 0f..1f) },
        ) {
            Box(
                Modifier.fillMaxWidth(progression.fraction).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Emerald, Mint)), CircleShape),
            )
        }
        Spacer(Modifier.height(if (compact) 4.dp else 7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val next = progression.next
            Text(
                if (next == null) "Dernier palier atteint" else "Vers ${next.name}",
                color = MutedInk,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (next != null) {
                Text(formatMoney(next.threshold), color = Mint, style = MarketLabel)
            }
        }
    }
}

@Composable
private fun ChartPanel(
    state: State<GameState>,
    phase: Phase,
    runNumber: Int,
    result: RoundResult?,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.clip(chartShape)
            .border(1.dp, Line, chartShape)
            .onSizeChanged { onResize(it.width, it.height) },
    ) {
        MarketRocketCanvas(state, Modifier.fillMaxSize())
        Text(
            "MANCHE ${runNumber.coerceAtLeast(1).toString().padStart(2, '0')}",
            style = MarketLabel,
            color = Mint,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(Color(0xFF101916), RoundedCornerShape(6.dp))
                .border(1.dp, Line, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (maxHeight >= 104.dp) {
            Text(
                "PLAFOND",
                style = MarketLabel,
                color = MutedInk,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 13.dp, end = 12.dp),
            )
            Text(
                "SOL",
                style = MarketLabel,
                color = MutedInk,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp, end = 12.dp),
            )
        }
        if (phase == Phase.Ready) {
            if (maxHeight >= 170.dp) {
                FlightHint(Modifier.align(Alignment.BottomStart).padding(12.dp))
            } else {
                Text(
                    "Maintenez ↑ · Relâchez ↓",
                    color = WhiteInk,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                )
            }
        }
        if (result != null) {
            RoundOverlay(
                result,
                compact = maxHeight < 180.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun FlightHint(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier.clip(shape).background(Color(0xEC111B1A)).border(1.dp, Line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MarketIcon(
            Symbol.Up,
            Emerald,
            Modifier.size(28.dp).background(Emerald.copy(alpha = 0.12f), RoundedCornerShape(7.dp)).padding(5.dp),
        )
        Column {
            Text("Maintenez pour monter", color = WhiteInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Relâchez pour descendre", color = MutedInk, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RoundOverlay(result: RoundResult, compact: Boolean, modifier: Modifier = Modifier) {
    val impact = result.impact
    val tint = if (impact == null) Mint else CoralText
    val detail = when (impact) {
        null -> "Gains transférés"
        Impact.Ceiling -> "Plafond touché"
        Impact.Floor -> "Sol touché"
        Impact.Volatility -> "Pic de volatilité"
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(Color(0xF20C1012)).border(1.dp, tint.copy(alpha = 0.48f), shape)
            .padding(horizontal = 22.dp, vertical = if (compact) 6.dp else 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (impact == null) "ENCAISSÉ" else "KRACH",
            color = tint,
            fontSize = if (compact) 20.sp else 30.sp,
            lineHeight = if (compact) 24.sp else 35.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.3.sp,
        )
        Text(detail, color = WhiteInk, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ControlDeck(
    state: State<GameState>,
    phase: Phase,
    result: RoundResult?,
    short: Boolean,
    compact: Boolean,
    onAction: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inPlay = phase == Phase.Playing
    val price by remember(state) { derivedStateOf { formatMoney(state.value.roundAmount) } }
    val shield by remember(state) {
        derivedStateOf {
            val frame = state.value
            if (frame.profile.shieldLevel > 0) frame.hasShield else null
        }
    }
    val (label, value, color) = when (phase) {
        Phase.Ready -> Triple("CAPITAL DE DÉPART", price, WhiteInk)
        Phase.Playing -> Triple("CAGNOTTE EN JEU", price, Mint)
        Phase.CashedOut -> Triple("VERSÉ EN BANQUE", "+${formatMoney(result?.amount ?: 0L)}", Mint)
        Phase.Crashed -> Triple("CAGNOTTE PERDUE", "−${formatMoney(result?.amount ?: 0L)}", CoralText)
    }
    if (short) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inPlay) {
                RoundAction(phase, price, compact = true, onAction, Modifier.fillMaxWidth())
            } else {
                RoundAmount(label, value, color, compact = true, Modifier.weight(0.6f))
                RoundAction(phase, price, compact = true, onAction, Modifier.weight(1f))
                IconButton(onClick = onShop) {
                    MarketIcon(Symbol.Sliders, Mint, Modifier.size(22.dp), "Améliorations")
                }
            }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.Center) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAmount(label, value, color, compact)
                if (shield != null && (phase == Phase.Ready || inPlay)) {
                    val tint = if (shield == true) Mint else MutedInk
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        MarketIcon(Symbol.Shield, tint, Modifier.size(16.dp))
                        Text(
                            if (shield == true) "BOUCLIER ACTIF" else "BOUCLIER UTILISÉ",
                            color = tint,
                            style = MarketLabel,
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
            RoundAction(phase, price, compact, onAction, Modifier.fillMaxWidth())
            if (inPlay) {
                Row(
                    Modifier.fillMaxWidth().height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Maintenez pour monter · Relâchez pour descendre", color = MutedInk, fontSize = 11.sp)
                }
            } else {
                TextButton(
                    onClick = onShop,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    MarketIcon(Symbol.Sliders, Mint, Modifier.size(17.dp))
                    Text(
                        "Améliorations",
                        color = WhiteInk,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    MarketIcon(Symbol.Chevron, MutedInk, Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundAmount(
    label: String,
    value: String,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MarketLabel, color = MutedInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = color,
            style = if (compact) MarketMoney.copy(fontSize = 24.sp, lineHeight = 28.sp) else MarketMoney,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundAction(
    phase: Phase,
    price: String,
    compact: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) = key(phase) {
    val source = remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.983f else 1f, tween(95), label = "Action")
    val (label, icon) = when (phase) {
        Phase.Ready -> "DÉMARRER" to Symbol.Play
        Phase.Playing -> "ENCAISSER" to Symbol.Deposit
        Phase.CashedOut, Phase.Crashed -> "RELANCER" to Symbol.Reload
    }
    Button(
        onClick = onAction,
        modifier = modifier.height(if (compact) 52.dp else 58.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = source,
        colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = ButtonInk),
        shape = RoundedCornerShape(15.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (phase == Phase.Playing) {
                Text(
                    "+$price",
                    style = MarketMoney.copy(fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.55).sp),
                    modifier = Modifier.background(Color(0x1E052018), RoundedCornerShape(7.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    maxLines = 1,
                )
            }
            MarketIcon(icon, ButtonInk, Modifier.size(20.dp))
        }
    }
}
