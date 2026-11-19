package com.marketrocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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

private val chartSky = Color(0xFF141C1C)
private val chartGround = Color(0xFF0B1114)
private val fineGrid = Color(0xFF192523)
private val majorGrid = Color(0xFF263733)
private val ringTextStyle = MarketLabel.copy(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.sp,
)

@Composable
internal fun MarketRocketCanvas(state: State<GameState>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 8)
    val curve = remember { Path() }
    val area = remember { Path() }
    val triangle = remember { Path() }
    val sky = remember { Brush.verticalGradient(listOf(chartSky, chartGround)) }
    val underCurve = remember {
        Brush.verticalGradient(
            0.25f to Emerald.copy(alpha = 0.24f),
            0.84f to Emerald.copy(alpha = 0.045f),
            1f to Emerald.copy(alpha = 0.012f),
        )
    }

    // Reading State inside the draw block invalidates only drawing at display cadence.
    Canvas(modifier) {
        val frame = state.value
        val camera = frame.player.x - (0.34f + frame.player.x.coerceAtMost(0.05f))
        val shake = frame.shakeRemaining / SHAKE_DURATION
        val offsetX = sin(shake * 36f) * shake * 6.dp.toPx()
        val offsetY = cos(shake * 42f) * shake * 3.dp.toPx()
        drawRect(sky)
        clipRect {
            translate(offsetX, offsetY) {
                drawGrid(frame.player.x)
                drawGuide(frame.player, camera)
                drawCurve(frame, camera, curve, area, underCurve)
                frame.obstacles.forEach { drawSpike(it, camera, triangle) }
                frame.bonuses.forEach { drawRing(it, camera, textMeasurer) }
                drawHead(frame, camera)
                frame.particles.forEach { drawParticle(it, camera) }
                frame.rewards.forEach { drawReward(it, camera, textMeasurer) }
            }
            drawBounds(frame)
        }
    }
}

private fun DrawScope.point(position: WorldPoint, camera: Float) =
    Offset((position.x - camera) * size.width, position.y * size.height)

private fun DrawScope.drawGrid(distance: Float) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val fineStep = width / 16f
    val fineOffset = -(distance * width * 0.22f) % fineStep
    for (index in 0..17) {
        val x = fineOffset + index * fineStep
        drawLine(fineGrid, Offset(x, -8.dp.toPx()), Offset(x, height + 8.dp.toPx()))
    }
    val majorStep = fineStep * 4f
    val majorOffset = -(distance * width * 0.46f) % majorStep
    for (index in 0..5) {
        val x = majorOffset + index * majorStep
        drawLine(majorGrid, Offset(x, -8.dp.toPx()), Offset(x, height + 8.dp.toPx()))
    }
    for (index in 1..11) {
        val y = height * index / 12f
        val tint = if (index % 3 == 0) majorGrid else fineGrid
        drawLine(tint, Offset(-8.dp.toPx(), y), Offset(width + 8.dp.toPx(), y))
    }
}

private fun DrawScope.drawBounds(frame: GameState) {
    val band = 12.dp.toPx().coerceAtMost(size.height * 0.25f)
    if (band <= 0f) return
    val ceilingAlpha = 0.07f + 0.19f * (1f - frame.player.y / 0.17f).coerceIn(0f, 1f)
    val floorAlpha = 0.07f + 0.19f * (1f - (1f - frame.player.y) / 0.17f).coerceIn(0f, 1f)
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
    drawLine(Coral.copy(alpha = ceilingAlpha + 0.12f), Offset.Zero, Offset(size.width, 0f), 2.dp.toPx())
    val bottomLeft = Offset(0f, size.height)
    drawLine(Coral.copy(alpha = floorAlpha + 0.12f), bottomLeft, Offset(size.width, size.height), 2.dp.toPx())
}

private fun DrawScope.drawGuide(player: WorldPoint, camera: Float) {
    val head = point(player, camera)
    val dashes = floatArrayOf(3.dp.toPx(), 5.dp.toPx())
    drawLine(
        Emerald.copy(alpha = 0.25f),
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
    drawPath(curve, Emerald.copy(alpha = 0.09f), style = lineStroke(12f))
    drawPath(curve, Emerald.copy(alpha = 0.19f), style = lineStroke(6f))
    drawPath(curve, Emerald, style = lineStroke(2.25f))
    drawPath(curve, Mint.copy(alpha = 0.8f), style = lineStroke(0.85f))
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
    val baseColor = Color(0xFF441E23)
    val colors = if (floorSpike) listOf(peakColor, baseColor) else listOf(baseColor, peakColor)
    drawPath(path, Coral.copy(alpha = 0.12f), style = lineStroke(9f))
    drawPath(
        path,
        Brush.verticalGradient(colors, startY = minOf(baseY, tipY), endY = maxOf(baseY, tipY)),
    )
    drawPath(path, Coral.copy(alpha = 0.87f), style = lineStroke(1.35f))
    drawLine(CoralText.copy(alpha = 0.6f), tip, Offset(x - half * 0.28f, baseY))
    drawLine(CoralText.copy(alpha = 0.24f), tip, Offset(x + half * 0.42f, baseY))
    val ridgeY = tipY + (baseY - tipY) * 0.73f
    drawLine(CoralText.copy(alpha = 0.27f), Offset(x - half * 0.73f, ridgeY), Offset(x + half * 0.73f, ridgeY))
}

private fun DrawScope.drawRing(bonus: Bonus, camera: Float, measurer: TextMeasurer) {
    val center = point(bonus.center, camera)
    val radius = RING_RADIUS * size.minDimension
    if (center.x + radius < 0f || center.x - radius > size.width || radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(Gold.copy(alpha = 0.14f), Color.Transparent), center, radius * 2.1f),
        radius = radius * 2.1f,
        center = center,
    )
    drawCircle(Gold.copy(alpha = 0.21f), radius * 1.22f, center, style = Stroke(1.dp.toPx()))
    drawCircle(Gold.copy(alpha = 0.24f), radius, center, style = Stroke(5.dp.toPx()))
    drawCircle(Gold, radius, center, style = Stroke(2.dp.toPx()))
    drawCircle(Color(0xFFFFF3AE), radius * 0.20f, center)

    val label = measurer.measure(bonus.kind.label(), ringTextStyle)
    val padX = 6.dp.toPx()
    val padY = 3.dp.toPx()
    val width = label.size.width + padX * 2f
    val height = label.size.height + padY * 2f
    val x = center.x - width * 0.5f
    val y = center.y - radius - height - 8.dp.toPx()
    val chipBounds = Size(width, height)
    val chipRadius = CornerRadius(5.dp.toPx())
    drawRoundRect(Color(0xFF242312), Offset(x, y), chipBounds, chipRadius)
    drawRoundRect(Gold.copy(alpha = 0.42f), Offset(x, y), chipBounds, chipRadius, style = Stroke(1.dp.toPx()))
    drawText(label, Gold, topLeft = Offset(x + padX, y + padY))
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
    drawCircle(color, radius + 1.3.dp.toPx(), center)
    drawCircle(Color(0xFFF3FFF8), radius * 0.63f, center)
}

private fun DrawScope.drawParticle(particle: Particle, camera: Float) {
    val color = when (particle.color) {
        ParticleColor.Gold -> Gold
        ParticleColor.Emerald -> Mint
        ParticleColor.Coral -> CoralText
    }
    val remaining = 1f - particle.age / particle.lifetime
    val radius = particle.radius * size.minDimension * (0.45f + remaining * 0.55f)
    drawCircle(color.copy(alpha = remaining), radius, point(particle.position, camera))
}

private fun DrawScope.drawReward(reward: FloatingReward, camera: Float, measurer: TextMeasurer) {
    val progress = reward.age / REWARD_DURATION
    val center = point(reward.position, camera)
    val layout = measurer.measure(
        reward.kind.label(),
        MarketMoney.copy(fontSize = 19.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    )
    val x = (center.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val y = (center.y - 22.dp.toPx() - progress * 42.dp.toPx()).coerceAtLeast(0f)
    val alpha = (1f - progress * progress).coerceIn(0f, 1f)
    drawText(layout, Gold.copy(alpha = alpha), topLeft = Offset(x, y))
}
