package com.narcic.ng.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small pill above the connect button: "قطع شده" / "در حال اتصال..." /
 * "متصل · AWG" (or V2Ray). The dot pulses with a soft glow while running.
 */
@Composable
fun StatusPill(
    isRunning: Boolean,
    isConnecting: Boolean,
    engineLabel: String,
    modifier: Modifier = Modifier,
) {
    val dotColor = when {
        isRunning -> Nc.Green
        isConnecting -> Nc.StateConnecting
        else -> Nc.Sub
    }
    val text = when {
        isRunning -> "متصل · $engineLabel"
        isConnecting -> "در حال اتصال..."
        else -> "قطع شده"
    }
    val textColor = when {
        isRunning -> Nc.Green
        isConnecting -> Nc.StateConnecting
        else -> Nc.Sub
    }

    val infinite = rememberInfiniteTransition(label = "statusDot")
    val glowAlpha by infinite.animateFloat(
        initialValue = .3f, targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotGlow"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = .05f))
            .padding(vertical = 8.dp, horizontal = 18.dp)
    ) {
        Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            if (isRunning || isConnecting) {
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = glowAlpha * .5f }
                        .background(Brush.radialGradient(listOf(dotColor, Color.Transparent)), CircleShape)
                )
            }
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = textColor, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}
