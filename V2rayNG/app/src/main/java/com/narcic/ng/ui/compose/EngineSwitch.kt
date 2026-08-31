package com.narcic.ng.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One tab in [EngineSwitch]: id is "awg" or "v2ray". */
data class EngineOption(
    val id: String,
    val label: String,
    val count: Int,
    val icon: @Composable (Color) -> Unit,
)

/**
 * Two-way sliding switch between the app's two connection engines
 * (AmneziaWG vs V2Ray). Locked (enabled = false) while a tunnel is up,
 * since the engines never run at the same time and switching mid-connection
 * doesn't make sense.
 */
@Composable
fun EngineSwitch(
    options: List<EngineOption>,
    selectedId: String,
    accent: Color,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .05f))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp))
            .padding(5.dp)
    ) {
        val segWidth = maxWidth / options.size.coerceAtLeast(1)
        val selIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
        val thumbX by animateDpAsState(
            segWidth * selIndex,
            spring(dampingRatio = 0.75f, stiffness = 300f), label = "thumb"
        )

        // Sliding highlight behind the active tab.
        Box(
            Modifier
                .offset(x = thumbX)
                .width(segWidth).height(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = .18f), Color.White.copy(alpha = .04f))
                    )
                )
                .border(1.dp, accent.copy(alpha = .4f), RoundedCornerShape(13.dp))
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEach { opt ->
                val active = opt.id == selectedId
                Row(
                    Modifier
                        .weight(1f).height(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable(enabled = enabled) { onSelect(opt.id) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    opt.icon(if (active) Nc.Txt else Nc.Sub)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        opt.label,
                        color = if (active) Nc.Txt else Nc.Sub,
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        opt.count.toString(),
                        color = if (active) accent else Nc.Sub,
                        fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .background(
                                if (active) accent.copy(alpha = .15f) else Color.White.copy(alpha = .12f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 7.dp, vertical = 1.5.dp)
                    )
                }
            }
        }
    }
}
