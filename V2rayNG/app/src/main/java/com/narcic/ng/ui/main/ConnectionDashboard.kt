package com.narcic.ng.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import kotlinx.coroutines.delay

/**
 * Premium connection dashboard per the 3-page redesign spec — replaces the
 * old 4-cell ConnectionStatsPanel grid as the "connected" view:
 *   - header: pulsing "متصل" dot + session duration (mono)
 *   - location: big flag + country + exit IP (mono)
 *   - hero metric: big live download number, upload & ping as side metrics
 *   - smooth live area chart (cubic-smoothed, gradient fill) from speedHistory
 *   - footer: session totals (accumulated here from the periodic speed text)
 *     + active protocol
 * Hidden while disconnected; while a handshake is in flight it shows the
 * [HandshakeStepsCard] checklist instead (pure presentation — the real
 * connect flow, timeouts and cancellation stay exactly as they were).
 */
@Composable
fun ConnectionDashboard(
    isRunning: Boolean,
    isConnecting: Boolean,
    isAwgProfile: Boolean,
    isAetherProfile: Boolean,
    pingText: String,
    downloadSpeedText: String,
    uploadSpeedText: String,
    connectionDurationText: String,
    remoteIp: String,
    remoteCity: String,
    remoteIsp: String,
    remoteCountryName: String,
    remoteCountryCode: String,
    engineLabel: String,
    protocolLabel: String,
    accent: Color,
    speedHistory: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isConnecting && !isRunning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            HandshakeStepsCard(isAwgProfile = isAwgProfile, isAetherProfile = isAetherProfile, accent = accent)
        }
        AnimatedVisibility(
            visible = isRunning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DashboardCard(
                isRunning = isRunning,
                pingText = pingText,
                downloadSpeedText = downloadSpeedText,
                uploadSpeedText = uploadSpeedText,
                connectionDurationText = connectionDurationText,
                remoteIp = remoteIp,
                remoteCity = remoteCity,
                remoteIsp = remoteIsp,
                remoteCountryName = remoteCountryName,
                remoteCountryCode = remoteCountryCode,
                engineLabel = engineLabel,
                protocolLabel = protocolLabel,
                accent = accent,
                speedHistory = speedHistory,
            )
        }
    }
}

// ───────────────────────────── dashboard card ─────────────────────────────

@Composable
private fun DashboardCard(
    isRunning: Boolean,
    pingText: String,
    downloadSpeedText: String,
    uploadSpeedText: String,
    connectionDurationText: String,
    remoteIp: String,
    remoteCity: String,
    remoteIsp: String,
    remoteCountryName: String,
    remoteCountryCode: String,
    engineLabel: String,
    protocolLabel: String,
    accent: Color,
    speedHistory: List<Float>,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .92f)
    val stroke = if (isDark) Color.White.copy(alpha = .13f) else Color(0x1A1B2230)

    // (value, unit) parsed from the service's periodic speed strings.
    val down = remember(downloadSpeedText) { parseSpeed(downloadSpeedText) }
    val up = remember(uploadSpeedText) { parseSpeed(uploadSpeedText) }

    // Session totals, accumulated client-side from the ~1s traffic updates.
    var totalDownKb by remember { mutableFloatStateOf(0f) }
    var totalUpKb by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isRunning) {
        if (!isRunning) {
            totalDownKb = 0f
            totalUpKb = 0f
        }
    }
    LaunchedEffect(downloadSpeedText, uploadSpeedText) {
        if (isRunning) {
            totalDownKb += toKb(down.first, down.second)
            totalUpKb += toKb(up.first, up.second)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        // ── header: pulse + متصل + duration ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pulse by rememberInfiniteTransition(label = "live").animateFloat(
                .35f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "liveA"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .background(Nc.Green, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text("متصل", color = Nc.Green, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(
                connectionDurationText.ifBlank { "00:00:00" },
                color = txtSub, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
        }

        // ── location: flag + city/country + IP ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 11.dp)) {
            Text(flagEmoji(remoteCountryCode), fontSize = 24.sp)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    listOf(remoteCity, remoteCountryName).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { engineLabel },
                    color = txtMain, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1
                )
                Text(
                    listOf(remoteIsp, remoteIp).filter { it.isNotBlank() }.joinToString(" · "),
                    color = txtSub, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1
                )
            }
        }

        // ── hero speed row ──
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("سرعت دانلود لحظه‌ای", color = txtSub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format("%.2f", down.first),
                        color = txtMain, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        down.second + "/s", color = txtSub, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("آپلود", color = txtSub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        trimNum(up.first), color = txtMain, fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        " " + up.second + "/s", color = txtSub, fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("تأخیر", color = txtSub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        pingText.ifBlank { "—" }, color = Nc.Green, fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        " ms", color = txtSub, fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }

        // ── live area chart ──
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(54.dp)
        ) {
            if (speedHistory.size < 2) return@Canvas
            val maxV = 4.5f
            val n = speedHistory.size
            val stepX = size.width / (n - 1)
            fun y(v: Float) = (size.height - 5f) - (v.coerceIn(0f, maxV) / maxV) * (size.height - 13f)
            val line = Path()
            speedHistory.forEachIndexed { i, v ->
                val x = i * stepX
                val yy = y(v)
                if (i == 0) line.moveTo(x, yy) else {
                    val px = (i - 1) * stepX
                    val py = y(speedHistory[i - 1])
                    val cx = (px + x) / 2f
                    line.cubicTo(cx, py, cx, yy, x, yy)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .32f), accent.copy(alpha = 0f))))
            drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        // ── footer: totals + protocol ──
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .height(0.5.dp)
                .background(stroke)
        )
        Row(Modifier.fillMaxWidth()) {
            FooterCell("مجموع دانلود", fmtTotal(totalDownKb), txtSub, txtMain, Modifier.weight(1f))
            FooterDivider(stroke)
            FooterCell("مجموع آپلود", fmtTotal(totalUpKb), txtSub, txtMain, Modifier.weight(1f))
            FooterDivider(stroke)
            FooterCell("پروتکل", protocolLabel, txtSub, accent, Modifier.weight(1f), small = true)
        }
    }
}

@Composable
private fun FooterCell(
    label: String,
    value: String,
    txtSub: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 7.dp),
    ) {
        Text(label, color = txtSub, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = valueColor,
            fontSize = if (small) 9.sp else 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun FooterDivider(color: Color) {
    Box(
        Modifier
            .width(0.5.dp)
            .height(26.dp)
            .background(color)
    )
}

// ───────────────────────── handshake steps card ─────────────────────────

private data class HandshakeStep(val label: String, val millis: Int)

private val stepsV2Ray = listOf(
    HandshakeStep("درخواست مجوز VPN", 700),
    HandshakeStep("شروع سرویس هسته", 1000),
    HandshakeStep("ساخت کانفیگ Xray و outbound ها", 900),
    HandshakeStep("راه‌اندازی اینترفیس TUN", 1200),
    HandshakeStep("تست تأخیر واقعی (generate_204)", 900),
    HandshakeStep("دریافت اطلاعات IP", 900),
)

private val stepsAwg = listOf(
    HandshakeStep("درخواست مجوز VPN", 700),
    HandshakeStep("آماده‌سازی GoBackend (org.amnezia.awg)", 900),
    HandshakeStep("اعمال کانفیگ تونل narcic-awg", 1100),
    HandshakeStep("انتظار برای دست‌دهی WireGuard", 1500),
    HandshakeStep("تأیید handshake با بررسی افزایش Rx", 900),
    HandshakeStep("تست تأخیر واقعی", 800),
)

private val stepsAether = listOf(
    HandshakeStep("اجرای هسته Aether (libaether.so)", 1200),
    HandshakeStep("اسکن و انتخاب اندپوینت Cloudflare", 1700),
    HandshakeStep("بارگذاری کلید WARP", 1000),
    HandshakeStep("انتظار برای پاسخ SOCKS روی 127.0.0.1:10819", 1500),
    HandshakeStep("تست تأخیر از داخل تونل", 900),
    HandshakeStep("دریافت اطلاعات IP خروجی", 900),
)

@Composable
private fun HandshakeStepsCard(
    isAwgProfile: Boolean,
    isAetherProfile: Boolean,
    accent: Color,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val steps = when {
        isAetherProfile -> stepsAether
        isAwgProfile -> stepsAwg
        else -> stepsV2Ray
    }

    // Pure presentation: steps light up on the same schedule as the mock;
    // the real connect flow (and its timeouts/cancel) is untouched.
    var currentStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(isAwgProfile, isAetherProfile) {
        while (true) {
            for (i in steps.indices) {
                currentStep = i
                delay(steps[i].millis.toLong())
            }
            currentStep = steps.size
            delay(1200)
        }
    }
    val doneCount = if (currentStep >= steps.size) steps.size - 1 else currentStep

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (isDark) .05f else .9f))
            .border(1.dp, if (isDark) Color.White.copy(alpha = .11f) else Color(0x1A94A3B8), RoundedCornerShape(18.dp))
            .padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        steps.forEachIndexed { i, s ->
            val state = when {
                i < doneCount -> "done"
                i == doneCount -> "run"
                else -> "wait"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.5.dp)
            ) {
                Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
                    when (state) {
                        "done" -> Text("✓", color = Nc.Green, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        "run" -> CircularProgressIndicator(
                            color = accent, strokeWidth = 2.dp, modifier = Modifier.size(12.dp)
                        )
                        else -> Box(
                            Modifier
                                .size(6.dp)
                                .background(txtSub.copy(alpha = .4f), CircleShape)
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    s.label,
                    color = if (state == "wait") txtSub.copy(alpha = .55f) else txtMain,
                    fontSize = 10.5.sp,
                    fontWeight = if (state == "run") FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
        Text(
            "برای لغو، دکمه اتصال را لمس کنید",
            color = txtSub.copy(alpha = .6f),
            fontSize = 8.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp, bottom = 5.dp)
        )
    }
}

// ───────────────────────────────── helpers ─────────────────────────────────

/** "3.4 MB/s" -> (3.4, "MB"); digits-only fallback keeps the old behavior. */
private fun parseSpeed(text: String): Pair<Float, String> {
    if (text.isBlank()) return 0f to "KB"
    val num = text.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
    val unit = when {
        text.contains("GB", true) -> "GB"
        text.contains("MB", true) -> "MB"
        else -> "KB"
    }
    return num to unit
}

private fun toKb(value: Float, unit: String): Float = when (unit) {
    "GB" -> value * 1048576f
    "MB" -> value * 1024f
    else -> value
}

private fun trimNum(v: Float): String =
    if (v >= 100f) String.format("%.0f", v) else String.format("%.1f", v)

private fun fmtTotal(kb: Float): String = when {
    kb < 1f -> "0 KB"
    kb < 1024f -> "${kb.toInt()} KB"
    kb < 1048576f -> String.format("%.1f MB", kb / 1024f)
    else -> String.format("%.2f GB", kb / 1048576f)
}

private fun flagEmoji(code: String): String {
    val c = code.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return c.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}
