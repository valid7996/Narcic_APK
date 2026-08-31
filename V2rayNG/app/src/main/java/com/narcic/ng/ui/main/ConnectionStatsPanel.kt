package com.narcic.ng.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.Sparkline

/**
 * Live connection info shown under the hero once the tunnel is up: exit
 * country/IP + active engine badge, a rolling download-speed sparkline, and
 * a ping/download/upload/duration grid. Hidden while disconnected.
 */
@Composable
fun ConnectionStatsPanel(
    isRunning: Boolean,
    pingText: String,
    downloadSpeedText: String,
    uploadSpeedText: String,
    connectionDurationText: String,
    remoteIp: String,
    remoteCountryName: String,
    remoteCountryCode: String,
    engineLabel: String,
    accent: Color,
    speedHistory: List<Float>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isRunning,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val isDark = LocalDarkTheme.current
        val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
        val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
        val cellBg = Color.White.copy(alpha = .07f)

        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = if (isDark) .06f else .92f))
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            // ── header: flag + country + engine badge + IP ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flagEmoji(remoteCountryCode), fontSize = 22.sp)
                Spacer(Modifier.width(9.dp))
                Text(
                    remoteCountryName.ifBlank { "—" }, color = txtMain,
                    fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    engineLabel, color = accent, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .background(accent.copy(alpha = .12f), RoundedCornerShape(50))
                        .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 2.5.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    remoteIp.ifBlank { "—" }, color = txtSub, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── live sparkline ──
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("دانلود زنده", color = txtSub, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Text(downloadSpeedText.ifBlank { "—" }, color = txtSub, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
                Sparkline(
                    values = speedHistory, accent = accent,
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── 4-cell grid ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCell(Icons.Outlined.Speed, pingText, "پینگ", accent, cellBg, txtMain, txtSub, Modifier.weight(1f))
                StatCell(Icons.Filled.ArrowDownward, downloadSpeedText, "دانلود", accent, cellBg, txtMain, txtSub, Modifier.weight(1f))
                StatCell(Icons.Filled.ArrowUpward, uploadSpeedText, "آپلود", accent, cellBg, txtMain, txtSub, Modifier.weight(1f))
                StatCell(Icons.Outlined.Schedule, connectionDurationText, "زمان", accent, cellBg, txtMain, txtSub, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(
    icon: ImageVector, value: String, label: String,
    accent: Color, cellBg: Color, txtMain: Color, txtSub: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(cellBg)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.height(15.dp).width(15.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            value.ifBlank { "—" }, color = txtMain, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1
        )
        Text(label, color = txtSub, fontSize = 9.sp)
    }
}

private fun flagEmoji(code: String): String {
    val c = code.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return c.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}
