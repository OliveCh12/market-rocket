package com.marketrocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketrocket.GameViewModel
import com.marketrocket.LoadState
import com.marketrocket.data.GameSettings

private enum class Destination { Menu, Game, Settings, Statistics }

@Composable
internal fun MarketRocketApp(viewModel: GameViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.Menu) }
    val loadState = viewModel.loadState
    if (loadState != LoadState.Ready) {
        ProfileLoading(loadState, viewModel::loadGame)
        return
    }
    val settings = viewModel.settings
    when (destination) {
        Destination.Menu -> HomeScreen(
            reduceEffects = settings.reduceEffects,
            onPlay = { destination = Destination.Game },
            onSettings = { destination = Destination.Settings },
        )
        Destination.Game -> GameScreen(viewModel) {
            viewModel.leaveGame()
            destination = Destination.Menu
        }
        Destination.Settings -> SettingsScreen(
            settings,
            viewModel::updateSettings,
            onStatistics = { destination = Destination.Statistics },
            onBack = { destination = Destination.Menu },
        )
        Destination.Statistics -> StatisticsScreen(viewModel.state.value.profile) {
            destination = Destination.Settings
        }
    }
    if (viewModel.saveFailed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text("Sauvegarde indisponible") },
            text = { Text("La progression est encore en mémoire. Réessayez pour l’enregistrer.") },
            confirmButton = { TextButton(onClick = viewModel::retrySave) { Text("Réessayer") } },
            dismissButton = { TextButton(onClick = viewModel::dismissSaveError) { Text("Plus tard") } },
        )
    }
}

@Composable
private fun ProfileLoading(state: LoadState, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Obsidian).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state == LoadState.Failed) {
                Text("Le profil n’a pas pu être chargé.", color = WhiteInk)
                Button(onClick = onRetry) { Text("Réessayer") }
            } else {
                Text("Chargement du profil…", color = MutedInk)
            }
        }
    }
}

@Composable
private fun HomeScreen(reduceEffects: Boolean, onPlay: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        MarketBackdrop(Modifier.matchParentSize(), reduceEffects = reduceEffects)
        HomeChart(Modifier.matchParentSize(), reduceEffects)
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding(),
        ) {
            val compact = maxHeight < 500.dp
            val side = if (maxWidth < 360.dp) 20.dp else 28.dp
            val maximumHeadline = (maxWidth.value - 2f * side.value) / (4.0f * LocalDensity.current.fontScale)
            val headline = minOf(if (compact) 42f else 60f, maximumHeadline.coerceAtLeast(16f)).sp
            val headlineHeight = (headline.value * 0.9f).sp
            Column(
                Modifier.align(Alignment.TopStart).padding(
                    start = side,
                    top = if (compact) 16.dp else maxHeight * 0.12f,
                ),
            ) {
                Text(
                    "MARKET",
                    color = WhiteInk,
                    fontWeight = FontWeight.Black,
                    fontSize = headline,
                    lineHeight = headlineHeight,
                    letterSpacing = (-2.7).sp,
                    maxLines = 1,
                )
                Text(
                    "ROCKET",
                    color = Mint,
                    fontWeight = FontWeight.Black,
                    fontSize = headline,
                    lineHeight = headlineHeight,
                    letterSpacing = (-2.7).sp,
                    maxLines = 1,
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = side, end = side, bottom = if (compact) 10.dp else 25.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val shape = RoundedCornerShape(15.dp)
                Button(
                    onClick = onPlay,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = ButtonInk),
                    contentPadding = PaddingValues(horizontal = 19.dp),
                ) {
                    Text("Jouer", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    MarketIcon(Symbol.Play, ButtonInk, Modifier.size(22.dp))
                }
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = shape,
                    border = BorderStroke(1.dp, Mint.copy(alpha = 0.24f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Obsidian.copy(alpha = 0.72f),
                        contentColor = WhiteInk,
                    ),
                    contentPadding = PaddingValues(horizontal = 19.dp),
                ) {
                    Text("Paramètres", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    MarketIcon(Symbol.Sliders, Mint, Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeChart(modifier: Modifier, reduceEffects: Boolean) {
    val sweep = if (reduceEffects) null else rememberInfiniteTransition(label = "Orbite").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6_000, easing = LinearEasing)),
        label = "Reflet",
    )
    val path = remember { Path() }
    Canvas(modifier) {
        val core = Offset(size.width * 0.79f, size.height * 0.44f)
        val radius = size.minDimension * 0.058f
        if (radius <= 0f) return@Canvas
        path.rewind()
        path.moveTo(-size.width * 0.08f, size.height * 0.77f)
        path.lineTo(size.width * 0.14f, size.height * 0.64f)
        path.lineTo(size.width * 0.30f, size.height * 0.69f)
        path.lineTo(size.width * 0.48f, size.height * 0.55f)
        path.lineTo(size.width * 0.59f, size.height * 0.58f)
        path.lineTo(core.x, core.y)

        drawCircle(
            Brush.radialGradient(listOf(Emerald.copy(alpha = 0.4f), Color.Transparent), core, radius * 4.6f),
            radius * 4.6f,
            core,
        )
        val line = Brush.horizontalGradient(listOf(Emerald.copy(alpha = 0.25f), Mint), endX = core.x)
        for (width in listOf(15.dp, 7.dp)) {
            drawPath(path, Emerald.copy(alpha = 0.13f), style = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val orbit = Size(radius * 5.5f, radius * 2.1f)
        val topLeft = Offset(core.x - orbit.width / 2f, core.y - orbit.height / 2f)
        rotate(-32f, pivot = core) {
            drawOval(Mint.copy(alpha = 0.30f), topLeft, orbit, style = Stroke(1.dp.toPx()))
            drawArc(
                Mint, sweep?.value ?: 240f, 68f, useCenter = false,
                topLeft = topLeft, size = orbit, style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawCircle(
            Brush.radialGradient(
                listOf(WhiteInk, Mint, Emerald, Color(0xFF087354)),
                center = core - Offset(radius * 0.39f, radius * 0.40f),
                radius = radius * 1.62f,
            ),
            radius,
            core,
        )
        drawCircle(Mint, radius, core, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun SettingsScreen(
    settings: GameSettings,
    onChange: (GameSettings) -> Unit,
    onStatistics: () -> Unit,
    onBack: () -> Unit,
) {
    MenuPage("Paramètres", onBack) {
        SettingSwitch(
            title = "Sons du jeu",
            detail = "Anneaux, encaissement et krach",
            checked = settings.soundEnabled,
            onCheckedChange = { onChange(settings.copy(soundEnabled = it)) },
        )
        HorizontalDivider(color = Line)
        SettingSwitch(
            title = "Vibrations",
            detail = "Bonus, encaissements et collisions",
            checked = settings.hapticsEnabled,
            onCheckedChange = { onChange(settings.copy(hapticsEnabled = it)) },
        )
        HorizontalDivider(color = Line)
        SettingSwitch(
            title = "Réduire les effets",
            detail = "Atténue les secousses et les animations du décor",
            checked = settings.reduceEffects,
            onCheckedChange = { onChange(settings.copy(reduceEffects = it)) },
        )
        HorizontalDivider(color = Line)
        TextButton(onClick = onStatistics, modifier = Modifier.fillMaxWidth()) {
            Text("Statistiques et objectifs", modifier = Modifier.weight(1f), color = WhiteInk)
            MarketIcon(Symbol.Chevron, Mint, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun MenuPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        MarketBackdrop(Modifier.matchParentSize(), reduceEffects = true)
        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding()
                .widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    MarketIcon(Symbol.Back, WhiteInk, Modifier.size(22.dp), "Retour au menu")
                }
                Text(
                    title,
                    color = WhiteInk,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Column(Modifier.padding(top = 20.dp), content = content)
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = WhiteInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(detail, color = MutedInk, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
