package com.narcic.ng.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.GooLoader
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

/**
 * Big connect button per the two-engine redesign: idle=red power icon,
 * connecting=amber + [GooLoader] + fast spinning ring, connected=engine
 * accent + double outward ripple. `accent`/`accent2` come from
 * `accentFor(isAwg)` (Theme's DesignTokens) so the whole hero (ring, glow,
 * button gradient, "test connection" chip) recolors with the active engine.
 *
 * `isConnecting` is UI-local (MainScreen flips it true on tap and clears it
 * once `isRunning` actually turns true, or after a timeout) since the real
 * ViewModel doesn't expose a dedicated "handshaking" state.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    isConnecting: Boolean,
    isTesting: Boolean,
    statusText: String,
    timeText: String,
    accent: Color,
    accent2: Color,
    onToggle: () -> Unit,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val statusColor = when {
        isRunning -> accent
        isConnecting -> Nc.StateConnecting
        else -> txtSub
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {

            // ── outward double ripple, only while actually connected ──
            if (isRunning) repeat(2) { idx ->
                val p by rememberInfiniteTransition(label = "rip$idx").animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2600, easing = LinearEasing),
                        initialStartOffset = StartOffset(idx * 1300),
                    ), label = "ripP$idx"
                )
                Box(
                    Modifier
                        .size(202.dp)
                        .graphicsLayer {
                            val s = 0.62f + p * 0.83f
                            scaleX = s; scaleY = s
                            alpha = (0.55f * (1f - p)).coerceIn(0f, 1f)
                        }
                        .border(1.5.dp, accent, CircleShape)
                )
            }

            // ── pulsing glow halo ──
            if (isRunning || isConnecting) {
                val glowColor = if (isConnecting) Nc.StateConnecting else accent
                val glow by rememberInfiniteTransition(label = "glow").animateFloat(
                    1f, 1.12f,
                    infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "glowS"
                )
                val glowAlpha by rememberInfiniteTransition(label = "glowA").animateFloat(
                    .85f, .5f,
                    infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "glowAl"
                )
                Box(
                    Modifier
                        .size(210.dp)
                        .graphicsLayer { scaleX = glow; scaleY = glow; alpha = glowAlpha }
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(glowColor.copy(alpha = .30f), Color.Transparent)
                                )
                            )
                        }
                )
            }

            // ── spinning gradient ring — fast while connecting, slow while connected, static idle ──
            val ringAngle by rememberInfiniteTransition(label = "ring").animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(if (isConnecting) 900 else 7000, easing = LinearEasing)),
                label = "ringA"
            )
            Box(
                Modifier
                    .size(198.dp)
                    .graphicsLayer { rotationZ = ringAngle }
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, accent2.copy(alpha = .9f), accent, Color.Transparent)
                            ),
                            style = Stroke(3.5.dp.toPx()),
                            alpha = if (isConnecting || isRunning) 1f else 0.3f,
                        )
                    }
            )

            // ── main button ──
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val btnScale by animateFloatAsState(
                if (pressed) 0.93f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "btnS"
            )

            val btnColors = when {
                isRunning -> listOf(accent.copy(alpha = .8f), accent, accent2.copy(alpha = .7f))
                isConnecting -> listOf(Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFFB45309))
                else -> listOf(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFFBE123C))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(158.dp)
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(btnColors))
                    .drawBehind {
                        // top-left glassy highlight
                        drawCircle(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = .28f), Color.Transparent),
                                center = Offset(size.width * .32f, size.height * .22f),
                                radius = size.width * .55f
                            )
                        )
                    }
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            ) {
                if (isConnecting) {
                    GooLoader(size = 96.dp, color = Color.White)
                } else {
                    Icon(
                        Icons.Filled.PowerSettingsNew, null, tint = Color.White,
                        modifier = Modifier.size(58.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            timeText,
            color = if (isRunning) txtMain else txtSub,
            fontSize = if (isRunning) 16.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            statusText,
            color = statusColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (isRunning) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDark) .06f else .9f))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(50))
                    .clickable(onClick = onCheckConnection)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Outlined.NetworkCheck, null, tint = accent, modifier = Modifier.size(14.dp))
                Text(
                    if (isTesting) "در حال تست..." else "تست اتصال",
                    color = txtMain, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
