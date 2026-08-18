package com.narcic.ng.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

private enum class WebCorner(val startAngleDeg: Float) {
    TOP_LEFT(0f),
    TOP_RIGHT(90f),
    BOTTOM_RIGHT(180f),
    BOTTOM_LEFT(270f),
}

/**
 * Faint, slowly-breathing spiderweb motif drawn into all four corners of the
 * screen, echoing the app's launcher icon (web + spider). Purely decorative:
 * it sits behind the real UI, stays low-alpha so it never competes with
 * content, and ignores touch entirely.
 *
 * Three animation layers, each looping independently so the whole thing
 * never feels mechanical:
 *  - `breathe`  — the web threads themselves fade in and out very slowly.
 *  - `ripple`   — a soft glow ring expands outward from each corner on a
 *                 loop, staggered per corner so they pulse out of sync.
 *  - `spiderT`  — a tiny spider patrols one strand of the top-left web.
 */
@Composable
fun SpiderWebCorners(
    modifier: Modifier = Modifier,
    threadColor: Color = AuroraCyan,
    glowColor: Color = AuroraIndigo,
) {
    val infinite = rememberInfiniteTransition(label = "spiderWeb")

    val breathe by infinite.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val rippleRaw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )

    val spiderT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spider",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val webRadius = size.minDimension * 0.30f

        WebCorner.entries.forEachIndexed { index, corner ->
            val origin = when (corner) {
                WebCorner.TOP_LEFT -> Offset(0f, 0f)
                WebCorner.TOP_RIGHT -> Offset(size.width, 0f)
                WebCorner.BOTTOM_RIGHT -> Offset(size.width, size.height)
                WebCorner.BOTTOM_LEFT -> Offset(0f, size.height)
            }
            // Stagger each corner's ripple by a quarter cycle so they never pulse in unison.
            val phase = (rippleRaw + index * 0.25f) % 1f

            drawWebQuadrant(
                origin = origin,
                startAngleDeg = corner.startAngleDeg,
                radius = webRadius,
                threadAlpha = breathe,
                threadColor = threadColor,
                glowColor = glowColor,
                rippleProgress = phase,
            )
        }

        // The spider only patrols the top-left web, matching the launcher icon.
        drawSpider(
            origin = Offset(0f, 0f),
            startAngleDeg = 0f,
            radius = webRadius,
            t = spiderT,
            color = threadColor,
        )
    }
}

private fun DrawScope.drawWebQuadrant(
    origin: Offset,
    startAngleDeg: Float,
    radius: Float,
    threadAlpha: Float,
    threadColor: Color,
    glowColor: Color,
    rippleProgress: Float,
) {
    val spokeCount = 6
    val ringCount = 4
    val sweep = 90f

    // Spokes fan out from the corner across the quadrant.
    for (i in 0..spokeCount) {
        val t = i / spokeCount.toFloat()
        val angle = Math.toRadians((startAngleDeg + sweep * t).toDouble())
        val end = Offset(
            origin.x + (radius * cos(angle)).toFloat(),
            origin.y + (radius * sin(angle)).toFloat(),
        )
        drawLine(
            color = threadColor.copy(alpha = threadAlpha * 0.8f),
            start = origin,
            end = end,
            strokeWidth = 1.1f,
            cap = StrokeCap.Round,
        )
    }

    // Concentric rings connecting the spokes, fading out toward the edge.
    for (r in 1..ringCount) {
        val ringRadius = radius * (r / ringCount.toFloat()) * 0.95f
        val ringAlpha = (threadAlpha * (0.55f - r * 0.06f)).coerceAtLeast(0.05f)
        drawArc(
            color = threadColor.copy(alpha = ringAlpha),
            startAngle = startAngleDeg,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(origin.x - ringRadius, origin.y - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(width = 1f, cap = StrokeCap.Round),
        )
    }

    // Outward-travelling glow ring — the "pulse" that gives the web life.
    val glowRadius = radius * rippleProgress
    val glowAlpha = (1f - rippleProgress) * 0.35f
    if (glowAlpha > 0.01f) {
        drawArc(
            color = glowColor.copy(alpha = glowAlpha),
            startAngle = startAngleDeg,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(origin.x - glowRadius, origin.y - glowRadius),
            size = Size(glowRadius * 2, glowRadius * 2),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawSpider(
    origin: Offset,
    startAngleDeg: Float,
    radius: Float,
    t: Float,
    color: Color,
) {
    // Travels along a fixed spoke (mid-sweep), back and forth between 35%
    // and 90% of the web's radius from the corner.
    val angle = Math.toRadians((startAngleDeg + 40f).toDouble())
    val travel = radius * (0.35f + 0.55f * t)
    val pos = Offset(
        origin.x + (travel * cos(angle)).toFloat(),
        origin.y + (travel * sin(angle)).toFloat(),
    )
    val bodyColor = color.copy(alpha = 0.9f)

    // Four short strokes crossing through the body stand in for legs.
    val legLen = 4f
    for (i in 0..3) {
        val legAngle = Math.toRadians((i * 45.0) + 20.0)
        val dx = (legLen * cos(legAngle)).toFloat()
        val dy = (legLen * sin(legAngle)).toFloat()
        drawLine(
            color = bodyColor.copy(alpha = 0.55f),
            start = Offset(pos.x - dx, pos.y - dy),
            end = Offset(pos.x + dx, pos.y + dy),
            strokeWidth = 1f,
        )
    }
    drawCircle(color = bodyColor, radius = 2.6f, center = pos)
}
