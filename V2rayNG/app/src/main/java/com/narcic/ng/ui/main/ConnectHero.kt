package com.narcic.ng.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import com.narcic.ng.R
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.AuroraViolet
import com.narcic.ng.ui.compose.RoundedPolygonShape

// Local, hero-specific neutrals. The accent itself (AuroraCyan/Indigo/Violet)
// comes from Theme.kt so the whole app shares one signature gradient — the
// same one used in the app's own launcher icon — instead of a one-off color.
private val FgMuted = Color(0xFF8A97B0)
private val RingBorderIdle = Color(0xFF232E45)
private val CoreIdleTop = Color(0xFF1A2338)
private val CoreIdleBottom = Color(0xFF0A0E1A)
private val CoreRunningTop = Color(0xFF163852)
private val CoreRunningBottom = Color(0xFF0A0E1A)

// The hero core is a rounded hexagon instead of a plain circle — a more
// deliberate, "secured network" silhouette that still nests cleanly inside
// the circular orbit/pulse rings drawn around it.
private val HeroCoreShape = RoundedPolygonShape(sides = 6, cornerRadius = 26.dp)

@Composable
private fun AnimatedSpiderWeb(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "spider-web")
    val drift by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "web-drift",
    )
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "web-spin",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = spin * 0.12f
                translationX = drift
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = size.width * 0.72f
            val centerY = size.height * 0.52f
            val maxRadius = minOf(size.width, size.height) * 0.52f

            for (ring in 0..6) {
                val radius = maxRadius * (ring + 1) / 7f
                drawCircle(
                    color = AuroraIndigo.copy(alpha = 0.18f + ring * 0.02f),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.2f)
                )
            }

            for (i in 0..11) {
                val angle = Math.PI * 2 * i / 12.0
                val x = centerX + cos(angle.toFloat()) * maxRadius
                val y = centerY + sin(angle.toFloat()) * maxRadius
                drawLine(
                    color = AuroraCyan.copy(alpha = 0.14f),
                    start = Offset(centerX, centerY),
                    end = Offset(x.toFloat(), y.toFloat()),
                    strokeWidth = 1.2f
                )
            }

            for (i in 0..11) {
                val angle = (Math.PI / 12.0) + (Math.PI * 2 * i / 12.0)
                val endX = centerX + cos(angle.toFloat()) * maxRadius
                val endY = centerY + sin(angle.toFloat()) * maxRadius
                drawLine(
                    color = AuroraViolet.copy(alpha = 0.18f),
                    start = Offset(centerX, centerY),
                    end = Offset(endX.toFloat(), endY.toFloat()),
                    strokeWidth = 1f
                )
            }
        }
    }
}

@Composable
private fun AnimatedSpider(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "spider")
    val sway by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spider-sway",
    )
    val bob by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spider-bob",
    )
    val legWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spider-leg-wave",
    )

    Canvas(
        modifier = modifier
            .graphicsLayer {
                rotationZ = sway
                translationY = bob
            }
            .size(90.dp, 90.dp)
    ) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val bodyRadius = size.minDimension * 0.18f

        drawCircle(
            color = Color(0xFF080D18),
            radius = bodyRadius * 1.2f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = AuroraCyan,
            radius = bodyRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color(0xFF09111A),
            radius = bodyRadius * 0.45f,
            center = Offset(cx, cy + bodyRadius * 0.7f)
        )

        val legLength = size.width * 0.2f
        for (side in -1..1 step 2) {
            val sideMult = side.toFloat()
            repeat(4) { legIndex ->
                val offset = (legIndex - 1.5f) / 3f
                val startX = cx + sideMult * (bodyRadius * 0.9f)
                val startY = cy + offset * 10f
                val angle = if (side > 0) 0.55f + legWave * 0.4f else -0.55f - legWave * 0.4f
                val endX = startX + sideMult * (legLength * (0.7f + legIndex * 0.12f))
                val endY = startY + (if (side > 0) 1f else -1f) * (bodyRadius + legIndex * 6f) + (legWave - 0.5f) * 18f

                drawLine(
                    color = Color(0xFF0A111B),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.4f
                )
                drawLine(
                    color = AuroraViolet.copy(alpha = 0.7f),
                    start = Offset(startX, startY),
                    end = Offset(endX + sideMult * (if (side > 0) 4f else -4f), endY + 4f),
                    strokeWidth = 1.3f
                )
            }
        }

        drawCircle(
            color = Color(0xFF1DE9B6),
            radius = 3f,
            center = Offset(cx - 8f, cy - 8f)
        )
        drawCircle(
            color = Color(0xFF1DE9B6),
            radius = 3f,
            center = Offset(cx + 8f, cy - 8f)
        )
    }
}

/**
 * Large animated connect button (orbit ring + conic glow + pulse, rounded
 * hexagonal core) matching the Narcic NG "Aurora" design, wired to REAL app
 * state and actions only (isRunning, isTesting, statusText, ToggleService,
 * TestRealAllServers, AutoConnect). No mock data, no simulated timers —
 * everything here reflects the actual VPN service state.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    isTesting: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onAutoConnect: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulseAlpha"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "orbit"
    )
    val conicAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "conic"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedSpiderWeb(modifier = Modifier.matchParentSize())
            AnimatedSpider(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = 18.dp)
            )

            Box(modifier = Modifier.size(190.dp), contentAlignment = Alignment.Center) {

            if (isRunning) {
                // Two-tone aurora pulse (cyan core wave, violet trailing wave)
                Box(
                    modifier = Modifier
                        .size(185.dp)
                        .scale(pulseScale)
                        .background(AuroraCyan.copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(185.dp)
                        .scale(pulseScale * 0.9f)
                        .background(AuroraViolet.copy(alpha = pulseAlpha * 0.6f), CircleShape)
                )
                Canvas(modifier = Modifier.size(196.dp).rotate(orbitAngle)) {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(AuroraCyan.copy(alpha = 0.4f), AuroraIndigo.copy(alpha = 0.4f))
                        ),
                        radius = 98.dp.toPx(),
                        style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                    drawCircle(color = AuroraCyan, radius = 3.dp.toPx(), center = Offset(size.width / 2, 0f))
                }
                Canvas(modifier = Modifier.size(172.dp).rotate(conicAngle)) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent, AuroraCyan.copy(0.38f), Color.Transparent,
                                AuroraIndigo.copy(0.38f), Color.Transparent
                            )
                        ),
                        radius = 86.dp.toPx()
                    )
                }
            } else {
                // Idle state still gets a faint static hex outline one size up,
                // so the core doesn't read as a flat, static circle-in-waiting.
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .border(1.dp, RingBorderIdle, HeroCoreShape)
                )
            }

            Box(
                modifier = Modifier
                    .size(158.dp)
                    .shadow(
                        elevation = if (isRunning) 22.dp else 8.dp,
                        shape = HeroCoreShape,
                        ambientColor = if (isRunning) AuroraCyan.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.3f),
                        spotColor = if (isRunning) AuroraCyan.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.3f),
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isRunning) listOf(CoreRunningTop, CoreRunningBottom)
                            else listOf(CoreIdleTop, CoreIdleBottom)
                        ),
                        shape = HeroCoreShape
                    )
                    .border(
                        width = 2.dp,
                        brush = if (isRunning) {
                            Brush.sweepGradient(listOf(AuroraCyan, AuroraIndigo, AuroraViolet, AuroraCyan))
                        } else {
                            Brush.linearGradient(listOf(RingBorderIdle, RingBorderIdle))
                        },
                        shape = HeroCoreShape
                    )
                    .clip(HeroCoreShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_power_24dp),
                        contentDescription = null,
                        tint = if (isRunning) AuroraCyan else FgMuted,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            isTesting -> "…"
                            isRunning -> "قطع اتصال"
                            else -> "اتصال"
                        },
                        color = if (isRunning) AuroraCyan else FgMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Small "secured" badge on the core's edge, only while connected.
            if (isRunning) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 48.dp, y = 48.dp)
                        .size(26.dp)
                        .shadow(elevation = 4.dp, shape = CircleShape)
                        .background(AuroraCyan, CircleShape)
                        .border(2.dp, CoreRunningBottom, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fab_check),
                        contentDescription = null,
                        tint = Color(0xFF04121F),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = if (isRunning) AuroraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(14.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Test speed button — dispatches the app's real ping-test action
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(AuroraCyan.copy(alpha = 0.10f))
                    .border(1.dp, AuroraCyan.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                    .clickable(enabled = !isTesting) { onTest() }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_flash_on_24dp),
                    contentDescription = null,
                    tint = AuroraCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isTesting) "در حال تست..." else "تست سرعت",
                    color = AuroraCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Auto Connect — picks the fastest known server (testing first if
            // none has been measured yet) and connects with a single tap.
            if (!isRunning) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(AuroraViolet.copy(alpha = 0.12f))
                        .border(1.dp, AuroraViolet.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
                        .clickable(enabled = !isTesting) { onAutoConnect() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_autoconnect_24dp),
                        contentDescription = null,
                        tint = AuroraViolet,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "اتصال خودکار",
                        color = AuroraViolet,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

