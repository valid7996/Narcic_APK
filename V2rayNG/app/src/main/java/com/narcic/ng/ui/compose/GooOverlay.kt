package com.narcic.ng.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen busy overlay for "تست همه سرورها" / "بروزرسانی سابسکریپشن‌ها".
 * Sits above the whole screen (place at the root Box, after everything
 * else) so it blocks interaction with a translucent scrim while showing a
 * [GooLoader] + status message.
 */
@Composable
fun GooOverlay(visible: Boolean, message: String, accent: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x8C030509)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                GooLoader(size = 120.dp, color = accent)
                Text(message, color = Nc.Txt, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
