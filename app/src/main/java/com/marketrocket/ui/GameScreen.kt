package com.marketrocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
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
import com.marketrocket.game.GameEvent
import com.marketrocket.game.FlightLesson
import com.marketrocket.game.GameState
import com.marketrocket.game.Impact
import com.marketrocket.game.Phase
import com.marketrocket.game.PlayerProfile
import com.marketrocket.game.RoundResult
import kotlinx.coroutines.isActive

@Composable
internal fun GameScreen(viewModel: GameViewModel, onMenu: () -> Unit) {
    val state = viewModel.state
    val phase by remember(state) { derivedStateOf { state.value.phase } }
    val profile by remember(state) { derivedStateOf { state.value.profile } }
    val result by remember(state) { derivedStateOf { state.value.result } }
    val shield by remember(state) { derivedStateOf { state.value.hasShield } }
    val combo by remember(state) { derivedStateOf { state.value.combo } }
    val lesson by remember(state) { derivedStateOf { state.value.lesson } }
    val reduceEffects = viewModel.settings.reduceEffects
    val paused = viewModel.paused
    val feedback = rememberGameFeedback(viewModel.settings)
    var shopOpen by rememberSaveable { mutableStateOf(false) }
    var hudHeight by remember { mutableIntStateOf(0) }
    var controlsHeight by remember { mutableIntStateOf(0) }
    val onBack: () -> Unit = {
        if (phase == Phase.Playing) viewModel.togglePause() else onMenu()
    }

    BackHandler(enabled = !shopOpen, onBack = onBack)
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.pauseForInterruption() }
    RunFrameClock(viewModel, feedback)

    BoxWithConstraints(Modifier.fillMaxSize().background(Obsidian)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val density = LocalDensity.current
        val ceiling = minOf(
            maxOf(insets.calculateTopPadding() + 60.dp, with(density) { hudHeight.toDp() } + 4.dp),
            maxHeight * 0.30f,
        )
        val floor = minOf(
            maxOf(insets.calculateBottomPadding() + 74.dp, with(density) { controlsHeight.toDp() } + 6.dp),
            maxHeight * 0.32f,
        )
        val gutter = if (maxWidth < 365.dp) 16.dp else 22.dp

        MarketBackdrop(Modifier.matchParentSize(), state, reduceEffects)
        MarketRocketCanvas(
            state = state,
            reduceEffects = reduceEffects,
            modifier = Modifier.matchParentSize()
                .padding(top = ceiling, bottom = floor)
                .onSizeChanged { viewModel.resize(it.width, it.height) }
                .testTag("flight-field")
                .semantics { contentDescription = "Zone de vol. Maintenez pour monter, relâchez pour descendre." }
                .flightControl(viewModel, paused || shopOpen),
        )

        if (!paused) {
            GameHud(
                profile = profile,
                shield = shield && phase == Phase.Playing,
                combo = if (phase == Phase.Playing) combo else 0,
                playing = phase == Phase.Playing,
                reduceEffects = reduceEffects,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { hudHeight = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = gutter, vertical = 4.dp),
            )
        }

        val showLesson = phase == Phase.Ready || (phase == Phase.Playing && profile.stats.cashOuts == 0L)
        if (showLesson && !paused && maxHeight >= 420.dp) {
            FlightHint(
                lesson = lesson,
                modifier = Modifier.align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = gutter, end = gutter, bottom = floor + 21.dp),
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
                compact = maxWidth < 350.dp || density.fontScale >= 1.3f,
                stacked = density.fontScale >= 1.45f,
                onAction = {
                    if (phase == Phase.Playing) {
                        if (viewModel.cashOut()) feedback(GameEvent.CashOut)
                    } else {
                        viewModel.startRound()
                    }
                },
                onShop = { shopOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { controlsHeight = it.height }
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
private fun RunFrameClock(viewModel: GameViewModel, feedback: (GameEvent) -> Unit) {
    val ticking by remember(viewModel) {
        derivedStateOf { !viewModel.paused && viewModel.state.value.needsFrames }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentFeedback by rememberUpdatedState(feedback)
    LaunchedEffect(viewModel, lifecycle, ticking) {
        if (!ticking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            try {
                while (isActive && viewModel.needsFrames) {
                    val now = withFrameNanos { it }
                    val event = viewModel.advance((now - previous) / 1_000_000_000.0)
                    if (event != null) currentFeedback(event)
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
    combo: Int,
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
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
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
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
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
                    .semantics {
                        contentDescription = "Progression du palier selon les gains cumulés"
                        progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f)
                    },
            ) {
                Box(Modifier.fillMaxWidth(fill).height(2.dp).background(Mint, CircleShape))
            }
        }
        if (combo > 1) {
            Text(
                "×$combo",
                color = Gold,
                style = MarketLabel.copy(fontSize = 12.sp),
                modifier = Modifier.semantics { contentDescription = "Combo de $combo anneaux" },
            )
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
private fun FlightHint(lesson: FlightLesson, modifier: Modifier) {
    val (icon, text) = when (lesson) {
        FlightLesson.Hold -> Symbol.Up to "Maintenez pour monter"
        FlightLesson.Release -> Symbol.Down to "Relâchez pour redescendre"
        FlightLesson.Collect -> Symbol.Trend to "Traversez un anneau doré"
        FlightLesson.CashOut -> Symbol.Deposit to "Encaissez ou visez le prochain anneau"
    }
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MarketIcon(icon, Mint, Modifier.size(17.dp))
        Text(
            text,
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
    stacked: Boolean,
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
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 54.dp),
            ) {
                MarketIcon(symbol, if (locked) MutedInk else ButtonInk, Modifier.size(17.dp))
                val showAmount = inFlight || phase == Phase.Ready
                val display = if (canCashOut) "+$amount" else amount
                val tint = if (locked) MutedInk else ButtonInk
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2)
                    if (stacked && showAmount) CashButtonAmount(display, tint)
                }
                if (!stacked && showAmount) CashButtonAmount(display, tint)
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
private fun CashButtonAmount(amount: String, color: Color) {
    Text(
        amount,
        color = color,
        style = MarketMoney.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.5).sp),
        maxLines = 1,
    )
}

@Composable
private fun RoundOutcome(result: RoundResult, reduceEffects: Boolean, modifier: Modifier) {
    val cashedOut = result.impact == null
    val color = if (cashedOut) Mint else CoralText
    val label = if (cashedOut) "ENCAISSÉ" else "KRACH"
    val detail = when (result.impact) {
        null -> if (result.newRecord) "Nouveau record d’encaissement" else "Versé en banque"
        Impact.Ceiling -> "Plafond touché"
        Impact.Floor -> "Sol touché"
        Impact.Volatility -> "Pic de volatilité"
    }
    val enter = when {
        reduceEffects -> EnterTransition.None
        cashedOut -> fadeIn(tween(150)) + scaleIn(
            animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow),
            initialScale = 0.85f,
        )
        else -> fadeIn(tween(130)) + scaleIn(tween(170), initialScale = 0.95f)
    }
    key(result) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = enter,
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
                    (if (cashedOut) "+" else "−") + formatMoney(result.amount),
                    style = MarketMoney.copy(fontSize = 34.sp, lineHeight = 42.sp),
                    color = WhiteInk,
                )
                Text(detail, color = MutedInk, fontSize = 12.sp)
                if (result.rings > 0) {
                    val rings = if (result.rings == 1) "1 anneau" else "${result.rings} anneaux"
                    val streak = if (result.bestCombo > 1) " · Série ×${result.bestCombo}" else ""
                    Text(rings + streak, color = color, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                }
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
