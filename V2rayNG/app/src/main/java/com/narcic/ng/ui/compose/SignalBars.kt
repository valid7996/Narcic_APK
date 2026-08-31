package com.narcic.ng.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Ping "signal strength" indicator — 4 bars, height 4/7/10/13dp, colored by
 * the same thresholds used across the app: <=100ms green, <=180ms lime,
 * <=300ms amber, else red. `pingMs == null` (not tested yet) shows all bars
 * dimmed/off.
 */
@Composable
fun SignalBars(pingMs: Int?, modifier: Modifier = Modifier) {
    val level = when {
        pingMs == null -> 0
        pingMs <= 100 -> 4
        pingMs <= 180 -> 3
        pingMs <= 300 -> 2
        else -> 1
    }
    val activeColor = when (level) {
        4 -> Nc.SignalGreen
        3 -> Nc.SignalLime
        2 -> Nc.SignalAmber
        1 -> Nc.SignalRed
        else -> Color.Transparent
    }
    Row(
        modifier.height(14.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(4.dp, 7.dp, 10.dp, 13.dp).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) activeColor else Nc.SignalOff)
            )
        }
    }
}
