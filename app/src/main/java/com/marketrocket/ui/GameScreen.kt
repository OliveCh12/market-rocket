package com.marketrocket.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LongState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.marketrocket.GameViewModel
import com.marketrocket.game.GameState
import com.marketrocket.game.Impact
import com.marketrocket.game.Phase
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.RoundResult
import kotlinx.coroutines.isActive

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
internal fun GameScreen(viewModel: GameViewModel, onMenu: () -> Unit) {
    val state = viewModel.state
    val phase by remember(state) { derivedStateOf { state.value.phase } }
    val profile by remember(state) { derivedStateOf { state.value.profile } }
    val result by remember(state) { derivedStateOf { state.value.result } }
    val shield by remember(state) { derivedStateOf { state.value.hasShield } }
    val reduceEffects = viewModel.settings.reduceEffects
    val paused = viewModel.paused
    val haptics = LocalHapticFeedback.current
    var shopOpen by rememberSaveable { mutableStateOf(false) }
    val onBack: () -> Unit = {
        if (phase == Phase.Playing) viewModel.togglePause() else onMenu()
    }

    BackHandler(enabled = !shopOpen, onBack = onBack)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pauseForInterruption() }
    RunFrameClock(viewModel)

    BoxWithConstraints(Modifier.fillMaxSize().background(Obsidian)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val fontExtra = (LocalDensity.current.fontScale - 1f).coerceIn(0f, 1.5f)
        val ceiling = minOf(insets.calculateTopPadding() + 60.dp + 32.dp * fontExtra, maxHeight * 0.30f)
        val floor = minOf(insets.calculateBottomPadding() + 74.dp + 12.dp * fontExtra, maxHeight * 0.30f)
        val gutter = if (maxWidth < 365.dp) 16.dp else 22.dp

        MarketBackdrop(Modifier.matchParentSize(), state, reduceEffects)
        MarketRocketCanvas(
            state = state,
            reduceEffects = reduceEffects,
            modifier = Modifier.matchParentSize()
                .padding(top = ceiling, bottom = floor)
                .onSizeChanged { viewModel.resize(it.width, it.height) }
                .flightControl(viewModel, paused || shopOpen),
        )

        if (!paused) {
            GameHud(
                profile = profile,
                shield = shield && phase == Phase.Playing,
                playing = phase == Phase.Playing,
                reduceEffects = reduceEffects,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = gutter, vertical = 4.dp),
            )
        }

        if (phase == Phase.Ready && maxHeight >= 420.dp) {
            FlightHint(
                Modifier.align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = gutter, bottom = floor + 24.dp),
            )
        }

        if (result != null) {
            RoundOutcome(
                result = checkNotNull(result),
                reduceEffects = reduceEffects,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = gutter),
            )
        }

        if (!paused) {
            RoundControls(
                state = state,
                displayedAmount = viewModel.displayedRoundAmount,
                phase = phase,
                compact = maxWidth < 350.dp,
                onAction = {
                    if (phase == Phase.Playing) {
                        if (viewModel.cashOut() && viewModel.settings.hapticsEnabled) {
                            haptics.performHapticFeedback(cashOutHaptic)
                        }
                    } else {
                        viewModel.startRound()
                    }
                },
                onShop = { shopOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(start = gutter, end = gutter, bottom = 12.dp)
                    .widthIn(max = 472.dp)
                    .fillMaxWidth(),
            )
        }

        AnimatedVisibility(
            visible = paused,
            enter = if (reduceEffects) EnterTransition.None else fadeIn(),
            exit = if (reduceEffects) ExitTransition.None else fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            PauseOverlay(onResume = viewModel::togglePause, onMenu = onMenu)
        }
    }

    if (shopOpen && phase != Phase.Playing) {
        UpgradeSheet(profile, onBuy = viewModel::buy, onDismiss = { shopOpen = false })
    }
}

private fun Modifier.flightControl(viewModel: GameViewModel, blocked: Boolean): Modifier =
    pointerInput(viewModel, blocked) {
        if (blocked) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!viewModel.pressFlight()) return@awaitEachGesture
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

@Composable
private fun RunFrameClock(viewModel: GameViewModel) {
    val ticking by remember(viewModel) {
        derivedStateOf { !viewModel.paused && viewModel.state.value.needsFrames }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel, lifecycle, haptics, ticking) {
        if (!ticking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            try {
                while (isActive && viewModel.needsFrames) {
                    val now = withFrameNanos { it }
                    val collected = viewModel.advance((now - previous) / 1_000_000_000.0)
                    if (collected && viewModel.settings.hapticsEnabled) {
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
private fun GameHud(
    profile: PlayerProfile,
    shield: Boolean,
    playing: Boolean,
    reduceEffects: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(profile) { profile.tierProgress }
    val fill = if (reduceEffects) progress.fraction else {
        animateFloatAsState(progress.fraction, label = "Progression").value
    }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("BANQUE", color = MutedInk, style = MarketLabel, modifier = Modifier.alignByBaseline())
                Text(
                    formatMoney(profile.bank),
                    color = WhiteInk,
                    style = MarketMoney.copy(fontSize = 21.sp, lineHeight = 25.sp, letterSpacing = (-0.6).sp),
                    modifier = Modifier.alignByBaseline(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    progress.current.name,
                    color = Mint,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
                progress.next?.let { next ->
                    Text(
                        "→ ${formatMoney(next.threshold)}",
                        color = MutedInk,
                        style = MarketLabel.copy(letterSpacing = 0.sp),
                        modifier = Modifier.alignByBaseline(),
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.widthIn(max = 154.dp).fillMaxWidth().height(2.dp)
                    .background(WhiteInk.copy(alpha = 0.14f), CircleShape)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f) },
            ) {
                Box(Modifier.fillMaxWidth(fill).height(2.dp).background(Mint, CircleShape))
            }
        }
        if (shield) MarketIcon(Symbol.Shield, Mint, Modifier.size(20.dp), "Bouclier actif")
        IconButton(onClick = onBack) {
            MarketIcon(
                if (playing) Symbol.Pause else Symbol.Back,
                WhiteInk,
                Modifier.size(22.dp),
                if (playing) "Mettre en pause" else "Menu principal",
            )
        }
    }
}

@Composable
private fun FlightHint(modifier: Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MarketIcon(Symbol.Up, Mint, Modifier.size(17.dp))
        Text(
            "Maintenez pour monter\nRelâchez pour descendre",
            color = MutedInk,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun RoundControls(
    state: State<GameState>,
    displayedAmount: LongState,
    phase: Phase,
    compact: Boolean,
    onAction: () -> Unit,
    onShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount by remember(displayedAmount) { derivedStateOf { formatMoney(displayedAmount.longValue) } }
    val canCashOut by remember(state) { derivedStateOf { state.value.canCashOut } }
    val inFlight = phase == Phase.Playing
    val locked = inFlight && !canCashOut
    val label = when (phase) {
        Phase.Ready -> "Lancer"
        Phase.Playing -> if (locked) "Un anneau requis" else "Encaisser"
        Phase.CashedOut, Phase.Crashed -> "Relancer"
    }
    val symbol = when (phase) {
        Phase.Ready -> Symbol.Play
        Phase.Playing -> if (locked) Symbol.Lock else Symbol.Deposit
        Phase.CashedOut, Phase.Crashed -> Symbol.Reload
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        key(phase) {
            Button(
                onClick = onAction,
                enabled = !locked,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Emerald,
                    contentColor = ButtonInk,
                    disabledContainerColor = Panel,
                    disabledContentColor = MutedInk,
                ),
                contentPadding = PaddingValues(horizontal = 15.dp),
                modifier = Modifier.weight(1f).height(54.dp),
            ) {
                MarketIcon(symbol, if (locked) MutedInk else ButtonInk, Modifier.size(17.dp))
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 9.dp),
                )
                if (inFlight || phase == Phase.Ready) {
                    Text(
                        if (canCashOut) "+$amount" else amount,
                        color = if (locked) MutedInk else ButtonInk,
                        style = MarketMoney.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.5).sp),
                        maxLines = 1,
                    )
                }
            }
        }
        if (!inFlight) {
            if (compact) {
                IconButton(onClick = onShop) {
                    MarketIcon(Symbol.Bolt, Mint, Modifier.size(20.dp), "Améliorations")
                }
            } else {
                TextButton(onClick = onShop, contentPadding = PaddingValues(horizontal = 7.dp)) {
                    MarketIcon(Symbol.Bolt, Mint, Modifier.size(17.dp))
                    Text("Améliorations", color = WhiteInk, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundOutcome(result: RoundResult, reduceEffects: Boolean, modifier: Modifier) {
    val color = if (result.impact == null) Mint else CoralText
    val label = if (result.impact == null) "ENCAISSÉ" else "KRACH"
    val detail = when (result.impact) {
        null -> "Versé en banque"
        Impact.Ceiling -> "Plafond touché"
        Impact.Floor -> "Sol touché"
        Impact.Volatility -> "Pic de volatilité"
    }
    key(result) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = if (reduceEffects) EnterTransition.None else fadeIn() + scaleIn(initialScale = 0.92f),
            exit = ExitTransition.None,
            modifier = modifier,
        ) {
            Column(
                Modifier.background(
                    Brush.radialGradient(listOf(Obsidian.copy(alpha = 0.96f), Color.Transparent)),
                ).padding(horizontal = 32.dp, vertical = 28.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, color = color, style = MarketLabel.copy(fontSize = 12.sp, letterSpacing = 1.8.sp))
                Text(
                    (if (result.impact == null) "+" else "−") + formatMoney(result.amount),
                    style = MarketMoney.copy(fontSize = 34.sp, lineHeight = 42.sp),
                    color = WhiteInk,
                )
                Text(detail, color = MutedInk, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PauseOverlay(onResume: () -> Unit, onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Obsidian.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("En pause", color = WhiteInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onResume) { Text("Reprendre") }
            TextButton(onClick = onMenu) { Text("Quitter la manche", color = MutedInk) }
        }
    }
}
