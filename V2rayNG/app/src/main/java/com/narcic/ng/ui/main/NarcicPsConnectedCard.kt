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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.aether.shared.data.IpInfo
import com.narcic.ng.aether.shared.data.PingState
import com.narcic.ng.aether.shared.model.SessionTraffic
import com.narcic.ng.aether.shared.util.CountryNames
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

/**
 * Compact post-connection status card for the Narcic PS engine on the main
 * screen. Shown only while the tunnel is RUNNING. Displays exactly five
 * real values — Country, IP, Ping, Download, Upload — straight from the
 * live IpInfoRepository / PingRepository / Bridge.trafficOverride flows.
 * Pure presentation: no state ownership, no connect logic, no fake data.
 */

// Local display formatters (the ones in AetherScreen.kt are private there;
// these read the same real values and format them for this card only).
private fun formatSpeed(bytesPerSec: Double): String = when {
    bytesPerSec >= 1_048_576.0 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024.0 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
    bytesPerSec > 0.0 -> String.format("%.0f B/s", bytesPerSec)
    else -> "0 B/s"
}

private fun formatPing(ms: Long): String = if (ms >= 0) "${ms}ms" else "—"

private fun flagFor(countryCode: String): String {
    val c = countryCode.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    val first = c[0].code - 'A'.code + 0x1F1E6
    val second = c[1].code - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

@Composable
fun NarcicPsConnectedCard(
    ipInfo: IpInfo,
    pingState: PingState,
    traffic: SessionTraffic?,
    accent: Color,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val isDark = LocalDarkTheme.current
        val card = Color.White.copy(alpha = if (isDark) .05f else .9f)
        val stroke = Color.White.copy(alpha = if (isDark) .1f else .08f)
        val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
        val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
        val cellBg = if (isDark) Color.White.copy(alpha = .06f) else Color.Black.copy(alpha = .04f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(14.dp),
        ) {
            // ── header: Country + IP ────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flagFor(ipInfo.countryCode), fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        CountryNames.display(ipInfo.countryCode).ifEmpty { "نامشخص" },
                        color = txtMain, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        ipInfo.ip.ifEmpty { if (ipInfo.isLoading) "..." else "—" },
                        color = txtSub, fontSize = 10.5.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── metric row: Ping / Download / Upload ────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MetricCell(
                    modifier = Modifier.weight(1f),
                    label = "پینگ",
                    value = formatPing(pingState.ms),
                    valueColor = when {
                        pingState.isPinging -> Nc.Amber
                        pingState.ms in 0..149 -> Nc.Green
                        pingState.ms >= 150 -> Nc.Amber
                        else -> Nc.Sub
                    },
                    bg = cellBg, txtSub = txtSub,
                )
                MetricCell(
                    modifier = Modifier.weight(1f),
                    label = "دانلود",
                    value = formatSpeed(traffic?.downloadSpeedBps ?: 0.0),
                    valueColor = txtMain,
                    bg = cellBg, txtSub = txtSub,
                )
                MetricCell(
                    modifier = Modifier.weight(1f),
                    label = "آپلود",
                    value = formatSpeed(traffic?.uploadSpeedBps ?: 0.0),
                    valueColor = txtMain,
                    bg = cellBg, txtSub = txtSub,
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    bg: Color,
    txtSub: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .padding(vertical = 9.dp, horizontal = 10.dp),
    ) {
        Text(label, color = txtSub, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
