package com.marketrocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketrocket.game.Bonus
import com.marketrocket.game.Edge
import com.marketrocket.game.FloatingReward
import com.marketrocket.game.GameState
import com.marketrocket.game.Obstacle
import com.marketrocket.game.PLAYER_RADIUS
import com.marketrocket.game.Particle
import com.marketrocket.game.ParticleColor
import com.marketrocket.game.Phase
import com.marketrocket.game.REWARD_DURATION
import com.marketrocket.game.RING_RADIUS
import com.marketrocket.game.SHAKE_DURATION
import com.marketrocket.game.WorldPoint
import kotlin.math.cos
import kotlin.math.sin

private val ringTextStyle = MarketLabel.copy(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.sp,
    shadow = Shadow(Obsidian, Offset(0f, 2f), 6f),
)

@Composable
internal fun MarketRocketCanvas(state: State<GameState>, reduceEffects: Boolean, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 8)
    val curve = remember { Path() }
    val area = remember { Path() }
    val triangle = remember { Path() }
    val underCurve = remember {
        Brush.verticalGradient(
            0.15f to Emerald.copy(alpha = 0.20f),
            0.72f to Emerald.copy(alpha = 0.065f),
            1f to Color.Transparent,
        )
    }

    // Reading State inside the draw block invalidates only drawing at display cadence.
    Canvas(modifier) {
        val frame = state.value
        val camera = frame.player.x - (0.34f + frame.player.x.coerceAtMost(0.05f))
        val shake = if (reduceEffects) 0f else frame.shakeRemaining / SHAKE_DURATION
        val offsetX = sin(shake * 36f) * shake * 6.dp.toPx()
        val offsetY = cos(shake * 42f) * shake * 3.dp.toPx()
        clipRect {
            translate(offsetX, offsetY) {
                drawGuide(frame.player, camera)
                drawCurve(frame, camera, curve, area, underCurve)
                frame.obstacles.forEach { drawSpike(it, camera, triangle) }
                val glint = if (reduceEffects) 215f else 215f + frame.player.x * 250f
                frame.bonuses.forEach { drawRing(it, camera, glint, textMeasurer) }
                drawHead(frame, camera)
                if (!reduceEffects) frame.particles.forEach { drawParticle(it, camera) }
                frame.rewards.forEach { drawReward(it, camera, reduceEffects, textMeasurer) }
            }
            drawBounds(frame)
        }
    }
}

private fun DrawScope.point(position: WorldPoint, camera: Float) =
    Offset((position.x - camera) * size.width, position.y * size.height)

private fun DrawScope.drawBounds(frame: GameState) {
    val band = 9.dp.toPx().coerceAtMost(size.height * 0.25f)
    if (band <= 0f) return
    val ceilingAlpha = 0.30f * (1f - frame.player.y / 0.18f).coerceIn(0f, 1f)
    val floorAlpha = 0.30f * (1f - (1f - frame.player.y) / 0.18f).coerceIn(0f, 1f)
    drawRect(
        Brush.verticalGradient(listOf(Coral.copy(alpha = ceilingAlpha), Color.Transparent), endY = band),
        size = Size(size.width, band),
    )
    val startY = size.height - band
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Coral.copy(alpha = floorAlpha)),
            startY = startY,
            endY = size.height,
        ),
        topLeft = Offset(0f, startY),
        size = Size(size.width, band),
    )
    val rail = Color(0xFF86AFB1).copy(alpha = 0.22f)
    drawLine(rail, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
    drawLine(CoralText.copy(alpha = ceilingAlpha), Offset.Zero, Offset(size.width, 0f), 2.dp.toPx())
    val bottomLeft = Offset(0f, size.height)
    val bottomRight = Offset(size.width, size.height)
    drawLine(rail, bottomLeft, bottomRight, 1.dp.toPx())
    drawLine(CoralText.copy(alpha = floorAlpha), bottomLeft, bottomRight, 2.dp.toPx())
}

private fun DrawScope.drawGuide(player: WorldPoint, camera: Float) {
    val head = point(player, camera)
    val dashes = floatArrayOf(3.dp.toPx(), 5.dp.toPx())
    drawLine(
        Mint.copy(alpha = 0.18f),
        head,
        Offset(size.width, head.y),
        pathEffect = PathEffect.dashPathEffect(dashes),
    )
}

private fun DrawScope.drawCurve(
    frame: GameState,
    camera: Float,
    curve: Path,
    area: Path,
    fill: Brush,
) {
    val start = point(frame.trail.first(), camera)
    val head = point(frame.player, camera)
    curve.rewind()
    curve.moveTo(start.x, start.y)
    for (sample in frame.trail) {
        val current = point(sample, camera)
        curve.lineTo(current.x, current.y)
    }
    curve.lineTo(head.x, head.y)
    area.rewind()
    area.addPath(curve)
    area.lineTo(head.x, size.height)
    area.lineTo(start.x, size.height)
    area.close()
    drawPath(area, fill)
    translate(top = 5.dp.toPx()) {
        drawPath(curve, Color(0xFF398B9A).copy(alpha = 0.27f), style = lineStroke(3f))
    }
    drawPath(curve, Emerald.copy(alpha = 0.09f), style = lineStroke(16f))
    drawPath(curve, Emerald.copy(alpha = 0.21f), style = lineStroke(7f))
    drawPath(curve, Emerald, style = lineStroke(3.1f))
    drawPath(curve, Mint.copy(alpha = 0.95f), style = lineStroke(1.2f))
}

private fun DrawScope.lineStroke(width: Float) =
    Stroke(width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawSpike(obstacle: Obstacle, camera: Float, path: Path) {
    val x = (obstacle.x - camera) * size.width
    val half = obstacle.halfWidth * size.width
    if (x + half < 0f || x - half > size.width) return
    val tipY = obstacle.tipY * size.height
    val baseY = obstacle.baseY * size.height
    val floorSpike = obstacle.edge == Edge.Floor
    val tip = Offset(x, tipY)
    path.rewind()
    path.moveTo(x - half, baseY)
    path.lineTo(x, tipY)
    path.lineTo(x + half, baseY)
    path.close()
    val peakColor = Coral.copy(alpha = 0.74f)
    val baseColor = Color(0xFF361A2C)
    val colors = if (floorSpike) listOf(peakColor, baseColor) else listOf(baseColor, peakColor)
    drawPath(path, Coral.copy(alpha = 0.12f), style = lineStroke(9f))
    drawPath(
        path,
        Brush.verticalGradient(colors, startY = minOf(baseY, tipY), endY = maxOf(baseY, tipY)),
    )
    drawPath(path, CoralText, style = lineStroke(1.2f))
    val ridgeX = x - half * 0.24f
    path.rewind()
    path.moveTo(x, tipY)
    path.lineTo(ridgeX, baseY)
    path.lineTo(x + half, baseY)
    path.close()
    drawPath(path, Color(0xFF150F22).copy(alpha = 0.56f))
    drawLine(CoralText.copy(alpha = 0.67f), tip, Offset(ridgeX, baseY), 1.dp.toPx())
    drawLine(Coral.copy(alpha = 0.65f), tip, Offset(x + half, baseY), 1.dp.toPx())
}

private fun DrawScope.drawRing(bonus: Bonus, camera: Float, glint: Float, measurer: TextMeasurer) {
    val center = point(bonus.center, camera)
    val radius = RING_RADIUS * size.minDimension
    if (center.x + radius < 0f || center.x - radius > size.width || radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(Gold.copy(alpha = 0.14f), Color.Transparent), center, radius * 2.1f),
        radius = radius * 2.1f,
        center = center,
    )
    drawCircle(Color(0xFF774822), radius, center + Offset(2.dp.toPx(), 2.dp.toPx()), style = Stroke(3.dp.toPx()))
    drawCircle(Gold.copy(alpha = 0.20f), radius, center, style = Stroke(6.dp.toPx()))
    drawCircle(Gold, radius, center, style = Stroke(2.4.dp.toPx()))
    drawArc(
        Color(0xFFFFF1AF), glint % 360f, 74f, useCenter = false,
        topLeft = center - Offset(radius, radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round),
    )

    val label = measurer.measure(bonus.kind.label(), ringTextStyle)
    val x = (center.x - label.size.width * 0.5f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f))
    val y = (center.y - radius - label.size.height - 8.dp.toPx()).coerceAtLeast(0f)
    drawText(label, Gold, topLeft = Offset(x, y))
}

private fun DrawScope.drawHead(frame: GameState, camera: Float) {
    val center = point(frame.player, camera)
    val radius = PLAYER_RADIUS * size.minDimension
    if (radius <= 0f) return
    val color = if (frame.phase == Phase.Crashed) Coral else Emerald
    val halo = radius * 3.8f
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center, halo),
        radius = halo,
        center = center,
    )
    if (frame.hasShield && frame.phase != Phase.Crashed) {
        drawCircle(Mint.copy(alpha = 0.64f), radius * 1.8f, center, style = Stroke(1.dp.toPx()))
    }
    drawCircle(
        Brush.radialGradient(
            listOf(WhiteInk, if (frame.phase == Phase.Crashed) CoralText else Mint, color),
            center - Offset(radius * 0.43f, radius * 0.43f),
            radius * 1.6f,
        ),
        radius,
        center,
    )
    drawCircle(color, radius, center, style = Stroke(0.8.dp.toPx()))
}

private fun DrawScope.drawParticle(particle: Particle, camera: Float) {
    val color = when (particle.color) {
        ParticleColor.Gold -> Gold
        ParticleColor.Emerald -> Mint
        ParticleColor.Coral -> CoralText
    }
    val remaining = 1f - particle.age / particle.lifetime
    val radius = particle.radius * size.minDimension * (0.45f + remaining * 0.55f)
    val head = point(particle.position, camera)
    val tail = head - Offset(particle.velocity.x * size.width, particle.velocity.y * size.height) * 0.027f
    drawLine(color.copy(alpha = remaining), tail, head, radius * 1.4f, StrokeCap.Round)
}

private fun DrawScope.drawReward(
    reward: FloatingReward,
    camera: Float,
    reduceEffects: Boolean,
    measurer: TextMeasurer,
) {
    val progress = reward.age / REWARD_DURATION
    val center = point(reward.position, camera)
    val layout = measurer.measure(
        reward.kind.label(),
        MarketMoney.copy(fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    )
    val x = (center.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val lift = if (reduceEffects) 0f else (1f - (1f - progress) * (1f - progress)) * 38.dp.toPx()
    val y = (center.y - 24.dp.toPx() - lift).coerceAtLeast(0f)
    val alpha = (1f - progress * progress).coerceIn(0f, 1f)
    if (!reduceEffects) {
        val wave = (progress * 2.4f).coerceIn(0f, 1f)
        drawCircle(
            Gold.copy(alpha = (1f - wave) * 0.65f),
            RING_RADIUS * size.minDimension * (1f + wave * 1.6f),
            center,
            style = Stroke(1.6.dp.toPx()),
        )
    }
    drawText(layout, Gold.copy(alpha = alpha), topLeft = Offset(x, y))
}
