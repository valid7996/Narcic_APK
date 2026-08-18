package com.narcic.ng.ui.compose

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Subtle animated cobweb decoration for dark backgrounds, echoing the
 * spider-and-web launcher icon ("Narcic NG"). Two corner webs (a big one
 * top-start, a smaller one bottom-end) breathe gently and send a faint
 * "glint" traveling outward along a strand every few seconds; a tiny
 * spider idles on a thread near the top-start web and occasionally bobs
 * down and climbs back up.
 *
 * Purely decorative — sits behind all real content, ignores touch, and is
 * intentionally very low-alpha so it never competes with the UI.
 */
@Composable
fun AnimatedCobwebBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cobweb")

    // Slow overall breathing (opacity) shared by both webs.
    val breathe by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Glint that travels from the corner outward along the strands, looping.
    val glint by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glint",
    )

    // Spider bob: hangs still, drops a little, climbs back — with a pause at top.
    val spiderBob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spiderBob",
    )

    // Gentle sway so the spider/web doesn't feel static.
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCornerWeb(
                anchor = Alignment.TopStart,
                radius = min(size.minDimension * 0.55f, 260.dp.toPx()),
                spokeCount = 7,
                ringCount = 4,
                breathe = breathe,
                glint = glint,
                sway = sway,
                baseAlpha = 0.16f,
            )
            drawCornerWeb(
                anchor = Alignment.BottomEnd,
                radius = min(size.minDimension * 0.4f, 180.dp.toPx()),
                spokeCount = 6,
                ringCount = 3,
                breathe = breathe,
                glint = (glint + 0.5f) % 1f,
                sway = -sway,
                baseAlpha = 0.10f,
            )
            drawHangingSpider(
                webRadius = min(size.minDimension * 0.55f, 260.dp.toPx()),
                bob = spiderBob,
                sway = sway,
                baseAlpha = 0.30f * breathe,
            )
        }
    }
}

private fun DrawScope.drawCornerWeb(
    anchor: Alignment,
    radius: Float,
    spokeCount: Int,
    ringCount: Int,
    breathe: Float,
    glint: Float,
    sway: Float,
    baseAlpha: Float,
) {
    val corner = when (anchor) {
        Alignment.TopStart -> Offset(0f, 0f)
        else -> Offset(size.width, size.height)
    }
    val swayRad = sway * 0.035f // tiny angular sway, radians

    // Spokes sweep a quarter turn into the screen from the corner.
    val spokeAngles = FloatArray(spokeCount) { i ->
        val t = i.toFloat() / (spokeCount - 1)
        val base = if (anchor == Alignment.TopStart) {
            (t * (Math.PI / 2.0)).toFloat()
        } else {
            (Math.PI + t * (Math.PI / 2.0)).toFloat()
        }
        base + swayRad
    }

    val strandColor = AuroraCyan.copy(alpha = baseAlpha * breathe)
    val strandColorFaint = AuroraIndigo.copy(alpha = baseAlpha * 0.6f * breathe)

    // Spokes.
    for (angle in spokeAngles) {
        val end = Offset(corner.x + cos(angle) * radius, corner.y + sin(angle) * radius)
        drawLine(
            color = strandColor,
            start = corner,
            end = end,
            strokeWidth = 1.1f,
        )
    }

    // Concentric rings connecting the spokes (classic web look), each ring
    // slightly irregular by nudging radius with a sine so it doesn't look
    // like a perfect drafting-compass circle.
    for (r in 1..ringCount) {
        val ringRadius = radius * r / ringCount
        val path = androidx.compose.ui.graphics.Path()
        spokeAngles.forEachIndexed { i, angle ->
            val jitter = 1f + 0.03f * sin(angle * 3 + r)
            val p = Offset(
                corner.x + cos(angle) * ringRadius * jitter,
                corner.y + sin(angle) * ringRadius * jitter,
            )
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path = path,
            color = strandColorFaint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )
    }

    // Traveling glint: a soft dot sliding outward along one spoke, fading
    // in/out at the ends, cycling through the spokes over time.
    val activeIndex = ((glint * spokeCount).toInt()).coerceIn(0, spokeCount - 1)
    val localT = (glint * spokeCount) - activeIndex
    val glintAngle = spokeAngles[activeIndex]
    val glintDist = radius * (0.15f + 0.8f * localT)
    val glintFade = (sin(localT * Math.PI)).toFloat().coerceIn(0f, 1f)
    val glintPos = Offset(corner.x + cos(glintAngle) * glintDist, corner.y + sin(glintAngle) * glintDist)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                AuroraCyan.copy(alpha = 0.55f * glintFade * breathe),
                AuroraCyan.copy(alpha = 0f),
            ),
            center = glintPos,
            radius = 14f,
        ),
        radius = 14f,
        center = glintPos,
    )
}

private fun DrawScope.drawHangingSpider(
    webRadius: Float,
    bob: Float,
    sway: Float,
    baseAlpha: Float,
) {
    // Thread drops from the top-start corner's first inner ring down toward
    // the middle of the web, the spider hangs at the end and bobs/sways.
    val threadAnchor = Offset(webRadius * 0.32f, webRadius * 0.32f)
    val restLength = webRadius * 0.22f
    val length = restLength + bob * webRadius * 0.14f
    val swayOffset = sway * 6f

    val bodyCenter = Offset(threadAnchor.x + swayOffset, threadAnchor.y + length)

    drawLine(
        color = AuroraCyan.copy(alpha = baseAlpha),
        start = threadAnchor,
        end = bodyCenter,
        strokeWidth = 1f,
    )

    // Tiny spider: a small body plus four short legs on each side — simple
    // enough to read at a glance without being a distracting illustration.
    val bodyRadius = 3.2f
    drawCircle(
        color = AuroraIndigo.copy(alpha = baseAlpha + 0.15f),
        radius = bodyRadius,
        center = bodyCenter,
    )
    drawCircle(
        color = AuroraCyan.copy(alpha = baseAlpha + 0.1f),
        radius = bodyRadius * 0.55f,
        center = Offset(bodyCenter.x, bodyCenter.y - bodyRadius * 0.9f),
    )
    val legColor = AuroraCyan.copy(alpha = baseAlpha)
    for (i in 0 until 3) {
        val spread = 4f + i * 2.6f
        val vert = 1.5f + i * 1.4f
        // Left legs
        drawLine(
            color = legColor,
            start = bodyCenter,
            end = Offset(bodyCenter.x - spread, bodyCenter.y + vert),
            strokeWidth = 0.8f,
        )
        // Right legs
        drawLine(
            color = legColor,
            start = bodyCenter,
            end = Offset(bodyCenter.x + spread, bodyCenter.y + vert),
            strokeWidth = 0.8f,
        )
    }
}
