package com.narcic.ng.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Large animated connect button (orbit ring + conic glow + pulse, rounded
 * hexagonal core) matching the Narcic NG "Aurora" design, wired to REAL app
 * state and actions only (isRunning, isTesting, statusText, ToggleService,
 * TestRealAllServers, AutoConnect). No mock data, no simulated timers —
 * everything here reflects the actual VPN service state.
 *
 * While connected, statusText reads "Connected, tap to check connection"
 * (R.string.connection_connected) and is itself tappable, triggering
 * onCheckConnection (MainAction.TestCurrentServer) to re-run a real ping
 * against the current server.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    isTesting: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    onCheckConnection: () -> Unit,
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

        Spacer(Modifier.height(14.dp))

        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = if (isRunning) AuroraCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clickable(enabled = isRunning, onClick = onCheckConnection)
            )
        }
    }
}

