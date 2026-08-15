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

private val NarcisYellow = Color(0xFFFFD166)
private val FgMuted = Color(0xFF8A8070)
private val FgDim = Color(0xFF4D4838)
private val BorderColor = Color(0x12FFD166)
private val CardBg = Color(0xFF1A1710)

/**
 * Large animated connect button (orbit ring + conic glow + pulse) matching
 * the Narcic NG "Narcis" design, wired to REAL app state and actions only
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
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pulseAlpha"
    )
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "orbit"
    )
    val conicAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Restart),
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
                Box(
                    modifier = Modifier
                        .size(185.dp)
                        .scale(pulseScale)
                        .background(NarcisYellow.copy(alpha = pulseAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(185.dp)
                        .scale(pulseScale * 0.9f)
                        .background(NarcisYellow.copy(alpha = pulseAlpha * 0.5f), CircleShape)
                )
                Canvas(modifier = Modifier.size(196.dp).rotate(orbitAngle)) {
                    drawCircle(
                        color = NarcisYellow.copy(alpha = 0.3f),
                        radius = 98.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                    drawCircle(color = NarcisYellow, radius = 3.dp.toPx(), center = Offset(size.width / 2, 0f))
                }
                Canvas(modifier = Modifier.size(172.dp).rotate(conicAngle)) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent, NarcisYellow.copy(0.4f), Color.Transparent,
                                NarcisYellow.copy(0.4f), Color.Transparent
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
                            colors = if (isRunning) listOf(Color(0xFF3D3520), Color(0xFF2A2410))
                            else listOf(Color(0xFF2E2A1A), Color(0xFF1A1710))
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isRunning) NarcisYellow else FgDim,
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
                        tint = if (isRunning) NarcisYellow else FgMuted,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            isTesting -> "…"
                            isRunning -> "قطع اتصال"
                            else -> "اتصال"
                        },
                        color = if (isRunning) NarcisYellow else FgMuted,
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
                color = if (isRunning) NarcisYellow else MaterialTheme.colorScheme.onSurfaceVariant,
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
                .background(NarcisYellow.copy(alpha = 0.1f))
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                .clickable(enabled = !isTesting) { onTest() }
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flash_on_24dp),
                contentDescription = null,
                tint = NarcisYellow,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isTesting) "در حال تست..." else "تست سرعت",
                color = NarcisYellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
