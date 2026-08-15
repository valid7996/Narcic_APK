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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// Local, hero-specific neutrals. The accent itself (AuroraCyan/Indigo/Violet)
// comes from Theme.kt so the whole app shares one signature gradient — the
// same one used in the app's own launcher icon — instead of a one-off color.
private val FgMuted = Color(0xFF8A97B0)
private val RingBorderIdle = Color(0xFF232E45)
private val CoreIdleTop = Color(0xFF141C2E)
private val CoreIdleBottom = Color(0xFF0A0E1A)
private val CoreRunningTop = Color(0xFF14324B)
private val CoreRunningBottom = Color(0xFF0A0E1A)

/**
 * Large animated connect button (orbit ring + conic glow + pulse) matching
 * the Narcic NG "Aurora" design, wired to REAL app state and actions only
 * (isRunning, isTesting, statusText, ToggleService, TestRealAllServers).
 * No mock data, no simulated timers — everything here reflects the actual
 * VPN service state.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    isTesting: Boolean,
    statusText: String,
    onToggle: () -> Unit,
    onTest: () -> Unit,
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
            }

            Box(
                modifier = Modifier
                    .size(164.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isRunning) listOf(CoreRunningTop, CoreRunningBottom)
                            else listOf(CoreIdleTop, CoreIdleBottom)
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        brush = if (isRunning) {
                            Brush.sweepGradient(listOf(AuroraCyan, AuroraIndigo, AuroraViolet, AuroraCyan))
                        } else {
                            Brush.linearGradient(listOf(RingBorderIdle, RingBorderIdle))
                        },
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = if (isRunning) painterResource(R.drawable.ic_stop_24dp)
                        else painterResource(R.drawable.ic_play_24dp),
                        contentDescription = null,
                        tint = if (isRunning) AuroraCyan else FgMuted,
                        modifier = Modifier.size(44.dp)
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

        // Test speed button — dispatches the app's real ping-test action
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AuroraCyan.copy(alpha = 0.10f))
                .border(1.dp, AuroraCyan.copy(alpha = 0.28f), RoundedCornerShape(24.dp))
                .clickable(enabled = !isTesting) { onTest() }
                .padding(horizontal = 20.dp, vertical = 8.dp),
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
    }
}
