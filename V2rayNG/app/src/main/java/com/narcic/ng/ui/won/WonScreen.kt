package com.narcic.ng.ui.won

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.faDigits
import com.narcic.ng.won.WonMode
import com.narcic.ng.won.WonVpnService

/**
 * The "W on N" section: MSN-GUARD's five transports (+ Psiphon over WARP) as a
 * sixth engine inside Narcic, restyled with Narcic's design language.
 *
 * Layout mirrors the VPN tab's grammar — StatusPill-style header, a hero dial,
 * a glass stats card — but the dial here is the "personal connection button":
 * a concentric ring with a live progress arc while connecting (fed by the real
 * per-milestone progress the ported service publishes), instead of ConnectHero.
 */

private fun accentForMode(mode: WonMode): Color = when (mode) {
    WonMode.MASQUE -> Nc.Cyan
    WonMode.WIREGUARD -> Nc.Green
    WonMode.WOW -> Nc.Violet
    WonMode.PSIPHON -> Nc.Amber
    WonMode.TOR -> Nc.Red
    WonMode.PSIPHON_OVER_WARP -> Color(0xFF38BDF8)
}

@Composable
fun WonScreen(
    viewModel: WonViewModel,
) {
    val onPrepareAndConnect = com.narcic.ng.won.LocalWonVpnPreparer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDark = LocalDarkTheme.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedMode by rememberSaveable { mutableStateOf(WonMode.MASQUE) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }

    val accent = accentForMode(selectedMode)
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.9f)
    val stroke = if (isDark) Nc.Stroke else Color(0x148B95A9)

    if (showSettings) {
        WonSettingsScreen(onBack = { showSettings = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ---- section header -------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "W on N",
                color = txtMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = "لاگ",
                tint = if (showLog) accent else txtSub,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { showLog = !showLog },
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "تنظیمات W on N",
                tint = txtSub,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { showSettings = true },
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- mode chips ------------------------------------------------------
        val rows = remember { listOf(WonMode.entries.take(3), WonMode.entries.drop(3)) }
        rows.forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowModes.forEach { mode ->
                    val modeAccent = accentForMode(mode)
                    val selected = mode == selectedMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) modeAccent.copy(alpha = if (isDark) 0.16f else 0.12f)
                                else cardBg
                            )
                            .border(
                                1.dp,
                                if (selected) modeAccent.copy(alpha = 0.7f) else stroke,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(enabled = !state.isRunning && !state.isConnecting) {
                                selectedMode = mode
                                viewModel.setModeLabel(mode.label)
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            mode.label,
                            color = if (selected) modeAccent else txtSub,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(14.dp))

        // ---- status line ------------------------------------------------------
        val statusColor = when {
            state.isRunning -> accent
            state.isConnecting -> Nc.StateConnecting
            else -> txtSub
        }
        val statusText = when {
            state.isRunning -> "متصل · ${state.modeLabel.ifBlank { selectedMode.label }}"
            state.isConnecting -> {
                val p = state.progress
                if (p in 1..99) "در حال اتصال... ${faDigits(p.toString())}٪"
                else state.detail.ifBlank { "در حال اتصال..." }
            }
            else -> "قطع شده"
        }
        Text(
            statusText,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        // ---- the personal connection dial ------------------------------------
        WonDial(
            accent = accent,
            accent2 = accent.copy(alpha = 0.55f),
            isRunning = state.isRunning,
            isConnecting = state.isConnecting,
            progress = state.progress,
            onToggle = {
                when {
                    state.isRunning -> viewModel.disconnect()
                    state.isConnecting -> viewModel.disconnect()
                    else -> {
                        viewModel.setModeLabel(selectedMode.label)
                        // Persist the choice so the QS tile connects with the
                        // same mode (WonConfig reads default_protocol).
                        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                            .edit().putString("default_protocol", selectedMode.key).apply()
                        val config = com.narcic.ng.won.WonController.configFor(context, selectedMode)
                        onPrepareAndConnect(config)
                    }
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        // ---- stats card --------------------------------------------------------
        if (state.isRunning || state.isConnecting) {
            WonStatsCard(state, accent, cardBg, stroke, txtMain, txtSub)
            Spacer(Modifier.height(12.dp))
        }

        // ---- live log ----------------------------------------------------------
        if (showLog) {
            WonLogConsole(state.logs, accent, cardBg, stroke, txtSub)
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(86.dp)) // clearance for the floating bottom bar
    }
}

// ---------------------------------------------------------------------------
// Dial
// ---------------------------------------------------------------------------

@Composable
private fun WonDial(
    accent: Color,
    accent2: Color,
    isRunning: Boolean,
    isConnecting: Boolean,
    progress: Int,
    onToggle: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val idle = if (isDark) Color(0x66FB7185) else Color(0xFFFB7185)

    // Progress arc 0..1; -1 means no measurable progress this tick.
    val progressFraction = if (progress in 0..100) progress / 100f else 0f
    val animatedProgress by animateFloatAsState(progressFraction, label = "wonProgress")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(216.dp)) {

            // outward double ripple while connected
            if (isRunning) repeat(2) { idx ->
                val p by rememberInfiniteTransition(label = "wonRip$idx").animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2600, easing = LinearEasing),
                        initialStartOffset = StartOffset(idx * 1300),
                    ), label = "wonRipP$idx"
                )
                Box(
                    Modifier
                        .size(190.dp)
                        .graphicsLayer {
                            val s = 0.62f + p * 0.83f
                            scaleX = s; scaleY = s
                            alpha = (0.55f * (1f - p)).coerceIn(0f, 1f)
                        }
                        .border(1.5.dp, accent, CircleShape)
                )
            }

            // slow-rotating dashed outer ring
            val ringRotation by rememberInfiniteTransition(label = "wonRing").animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
                label = "wonRingR",
            )
            Box(
                Modifier
                    .size(206.dp)
                    .rotate(ringRotation)
                    .drawBehind {
                        val gap = 11f
                        var angle = 0f
                        while (angle < 360f) {
                            drawArc(
                                color = if (isDark) Nc.Stroke else Color(0x2E8B95A9),
                                startAngle = angle,
                                sweepAngle = gap / 2f,
                                useCenter = false,
                                style = Stroke(width = 2f),
                            )
                            angle += gap * 2f
                        }
                    },
            )

            // progress arc while connecting
            if (isConnecting) {
                Box(
                    Modifier
                        .size(206.dp)
                        .drawBehind {
                            drawArc(
                                brush = Brush.sweepGradient(listOf(accent, accent2, Color.Transparent)),
                                startAngle = -90f,
                                sweepAngle = animatedProgress * 359.9f,
                                useCenter = false,
                                style = Stroke(width = 5f),
                            )
                        },
                )
            }

            // glow + button
            val glowAlpha by animateFloatAsState(
                if (isRunning || isConnecting) 0.55f else 0.25f, label = "wonGlow",
            )
            Box(
                Modifier
                    .size(168.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accent.copy(alpha = 0.30f * glowAlpha / 0.55f), Color.Transparent),
                                radius = size.minDimension / 2f,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                0f to accent.copy(alpha = 0.30f),
                                0.72f to accent.copy(alpha = 0.02f),
                                1f to accent.copy(alpha = 0.16f),
                            )
                        )
                        .border(
                            2.5.dp,
                            Brush.linearGradient(listOf(accent, accent2)),
                            CircleShape,
                        )
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = if (isRunning) "قطع اتصال" else "اتصال",
                        tint = when {
                            isRunning -> accent
                            isConnecting -> Nc.StateConnecting
                            else -> idle
                        },
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stats card + log console
// ---------------------------------------------------------------------------

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1 shl 20 -> String.format("%.2f MB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatSpeed(bps: Long): String = when {
    bps >= 1 shl 20 -> String.format("%.2f MB/s", bps / 1048576.0)
    bps >= 1 shl 10 -> String.format("%.1f KB/s", bps / 1024.0)
    else -> "$bps B/s"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun WonStatsCard(
    state: WonUiState,
    accent: Color,
    cardBg: Color,
    stroke: Color,
    txtMain: Color,
    txtSub: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(state.exitIp.ifBlank { "در حال اندازه‌گیری..." }, color = txtMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            state.country.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(6.dp))
                Text("· $it", color = txtSub, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell("دانلود", formatSpeed(state.speedRx), Nc.ChartDownloadTop, Modifier.weight(1f), txtSub)
            StatCell("آپلود", formatSpeed(state.speedTx), Nc.ChartUploadTop, Modifier.weight(1f), txtSub)
            StatCell("مدت", faDigits(formatDuration(state.sessionSeconds)), accent, Modifier.weight(1f), txtSub)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "مصرف این ماه: ↓ ${formatBytes(state.monthRx)} · ↑ ${formatBytes(state.monthTx)}",
            color = txtSub,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color, modifier: Modifier, labelColor: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = labelColor, fontSize = 10.5.sp)
    }
}

@Composable
private fun WonLogConsole(
    logs: List<String>,
    accent: Color,
    cardBg: Color,
    stroke: Color,
    txtSub: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text("لاگ اتصال", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (logs.isEmpty()) {
            Text("—", color = txtSub, fontSize = 10.5.sp)
        } else {
            logs.takeLast(14).forEach { line ->
                Text(
                    line,
                    color = txtSub,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
