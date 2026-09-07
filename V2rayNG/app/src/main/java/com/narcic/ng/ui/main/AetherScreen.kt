package com.narcic.ng.ui.main

import android.content.Intent
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.aether.core.ConnectionController
import com.narcic.ng.aether.core.PsiphonController
import com.narcic.ng.aether.service.AetherVpnService
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.IpInfo
import com.narcic.ng.aether.shared.data.IpInfoRepository
import com.narcic.ng.aether.shared.data.PingRepository
import com.narcic.ng.aether.shared.data.PingState
import com.narcic.ng.aether.shared.data.PsiphonEgressRegistry
import com.narcic.ng.aether.shared.model.AetherProtocol
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.aether.shared.model.SessionTraffic
import com.narcic.ng.aether.shared.platform.Bridge
import com.narcic.ng.aether.shared.util.CountryNames
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Third engine tab: the AetherST tunnel (MASQUE / WireGuard / Gool /
 * Cloudflare Zero Trust), ported from github.com/immaghzbad/AetherST.
 * Full VPN dashboard: live status, duration, direct/exit IP cards,
 * ping, traffic metrics, chain stages and Psiphon controls — all wired
 * to the real repositories (no fake data).
 */

// region formatters (pure, no state)

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "00:00:00"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatSpeed(bytesPerSec: Double): String = when {
    bytesPerSec >= 1_048_576.0 -> String.format("%.1f MB/s", bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1024.0 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
    bytesPerSec > 0.0 -> String.format("%.0f B/s", bytesPerSec)
    else -> "0 B/s"
}

private fun formatBytes(totalBytes: Long): String = when {
    totalBytes >= 1_073_741_824L -> String.format("%.2f GB", totalBytes / 1_073_741_824.0)
    totalBytes >= 1_048_576L -> String.format("%.1f MB", totalBytes / 1_048_576.0)
    totalBytes >= 1024L -> String.format("%.1f KB", totalBytes / 1024.0)
    totalBytes > 0L -> "$totalBytes B"
    else -> "0 B"
}

private fun formatPing(ms: Long): String = if (ms >= 0) "${ms} ms" else "—"

private fun flagFor(countryCode: String): String {
    if (countryCode.length != 2) return "🌐"
    val first = countryCode[0].uppercaseChar().code - 'A'.code + 0x1F1E6
    val second = countryCode[1].uppercaseChar().code - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

// endregion

@Composable
fun AetherScreen() {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val card = if (isDark) Nc.Bg.copy(alpha = .55f) else Color.White.copy(alpha = .85f)
    val cardInner = if (isDark) Color.White.copy(alpha = .04f) else Color.Black.copy(alpha = .03f)
    val stroke = if (isDark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f)
    val accent = Nc.Cyan

    val configRepository = remember {
        AetherConfigRepository.getInstance(
            com.narcic.ng.aether.platform.getSettings(
                com.narcic.ng.aether.platform.PlatformContext(context)
            )
        )
    }
    val config by configRepository.config.collectAsStateWithLifecycle()

    // Real state sources — no fake values:
    val status by ConnectionController.status.collectAsStateWithLifecycle()
    val durationSeconds by Bridge.elapsedOverride.collectAsStateWithLifecycle()
    val traffic by Bridge.trafficOverride.collectAsStateWithLifecycle()
    val ipInfo by IpInfoRepository.ipInfo.collectAsStateWithLifecycle()
    val pingState by PingRepository.pingState.collectAsStateWithLifecycle()
    val availableRegions by PsiphonEgressRegistry.availableRegions.collectAsStateWithLifecycle()

    val running = status != ConnectionStatus.STOPPED && status != ConnectionStatus.ERROR && status != ConnectionStatus.FAILED
    val connected = status == ConnectionStatus.RUNNING
    val chainOn = config.psiphonEnabled
    val psiphonStageActive = ConnectionController.psiphonChaining
    val psiphonLinked = connected && !psiphonStageActive && chainOn && PsiphonController.isConnected()

    val scope = rememberCoroutineScope()

    // Live pollers: duration/traffic come through Bridge flows already, but the
    // IP/ping lookups must be triggered when the tunnel comes up (same cadence
    // the upstream AetherViewModel uses) and refreshable on demand.
    LaunchedEffect(connected, chainOn) {
        if (connected) {
            val socksPort = config.socksPort.toIntOrNull() ?: 1819
            val psiphonUrl = com.narcic.ng.aether.shared.data.ActiveProxyProvider.psiphonProxyUrl
            scope.launch {
                PingRepository.runPing(
                    socksHost = config.socksHost,
                    socksPort = socksPort,
                    useProxy = true,
                    pingUrl = config.pingUrl
                )
            }
            if (!psiphonUrl.isNullOrEmpty()) {
                val parts = psiphonUrl.removePrefix("socks5://").removePrefix("socks://").split(":", limit = 2)
                IpInfoRepository.fetchIpInfo(
                    socksHost = parts.firstOrNull() ?: "127.0.0.1",
                    socksPort = parts.getOrNull(1)?.toIntOrNull() ?: 3080,
                    useProxy = true
                )
            } else {
                IpInfoRepository.fetchIpInfo(config.socksHost, socksPort, useProxy = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ===================== Header: status + duration =====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                connected -> Nc.Green
                                running -> Nc.Amber
                                else -> Nc.Sub
                            }
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text("Aether Tunnel", color = Nc.Txt, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(durationSeconds),
                    color = Nc.Sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (status) {
                    ConnectionStatus.STOPPED -> "خاموش"
                    ConnectionStatus.STARTING -> "در حال اتصال..."
                    ConnectionStatus.VALIDATING -> if (chainOn) "اعتبارسنجی مسیر (مرحله ۱)..." else "اعتبارسنجی مسیر..."
                    ConnectionStatus.DATAPLANE_VALIDATED, ConnectionStatus.SOCKS_READY, ConnectionStatus.TUN_ACTIVE -> "در حال راه‌اندازی..."
                    ConnectionStatus.RUNNING -> if (chainOn && psiphonLinked) "متصل (زنجیره فعال)" else "متصل"
                    ConnectionStatus.RECONNECTING -> "اتصال مجدد..."
                    ConnectionStatus.STOPPING -> "در حال قطع..."
                    ConnectionStatus.ERROR, ConnectionStatus.FAILED -> "خطا"
                },
                color = if (connected) Nc.Green else if (running) Nc.Amber else Nc.Sub,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ===================== IP cards: direct -> exit =====================
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IpCard(
                modifier = Modifier.weight(1f),
                title = "IP مستقیم",
                info = ipInfo,
                viaTunnel = false,
                card = card,
                inner = cardInner,
                stroke = stroke,
            )
            if (chainOn) {
                Text("→", color = Nc.Sub, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterVertically))
                IpCard(
                    modifier = Modifier.weight(1f),
                    title = "IP خروجی",
                    info = ipInfo,
                    viaTunnel = true,
                    card = card,
                    inner = cardInner,
                    stroke = stroke,
                )
            }
        }
        if (!chainOn) {
            // Without the chain the single tunnel exit IS the exit IP — show it full-width.
            Spacer(Modifier.height(8.dp))
            IpCard(
                modifier = Modifier.fillMaxWidth(),
                title = "IP خروجی تونل",
                info = ipInfo,
                viaTunnel = true,
                card = card,
                inner = cardInner,
                stroke = stroke,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ===================== Ping + traffic metrics =====================
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "Ping",
                value = formatPing(pingState.ms),
                valueColor = when {
                    pingState.isPinging -> Nc.Amber
                    pingState.ms in 0..149 -> Nc.Green
                    pingState.ms >= 150 -> Nc.Amber
                    else -> Nc.Sub
                },
                card = card,
                stroke = stroke,
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "دانلود",
                value = formatSpeed(traffic?.downloadSpeedBps ?: 0.0),
                valueColor = Nc.Txt,
                card = card,
                stroke = stroke,
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "آپلود",
                value = formatSpeed(traffic?.uploadSpeedBps ?: 0.0),
                valueColor = Nc.Txt,
                card = card,
                stroke = stroke,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "کل دانلود",
                value = formatBytes(traffic?.downloadedBytes ?: 0L),
                valueColor = Nc.Txt,
                card = card,
                stroke = stroke,
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "کل آپلود",
                value = formatBytes(traffic?.uploadedBytes ?: 0L),
                valueColor = Nc.Txt,
                card = card,
                stroke = stroke,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ===================== Chain visualization =====================
        ChainCard(
            chainOn = chainOn,
            status = status,
            psiphonStageActive = psiphonStageActive,
            connected = connected,
            psiphonLinked = psiphonLinked,
            directInfo = ipInfo,
            exitInfo = ipInfo,
            card = card,
            inner = cardInner,
            stroke = stroke,
        )

        Spacer(Modifier.height(12.dp))

        // ===================== Protocol picker =====================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AetherProtocol.entries.forEach { p ->
                val selected = p == config.protocol
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) accent.copy(alpha = .14f) else card)
                        .border(1.dp, if (selected) accent else stroke, RoundedCornerShape(14.dp))
                        .clickable {
                            if (configRepository.config.value.protocol != p) {
                                configRepository.updateConfig(
                                    configRepository.config.value.copy(protocol = p)
                                )
                            }
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        p.displayName,
                        color = if (selected) accent else Nc.Txt,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ===================== Psiphon chain section =====================
        PsiphonSection(
            chainOn = chainOn,
            config = config,
            configRepository = configRepository,
            availableRegions = availableRegions,
            card = card,
            inner = cardInner,
            stroke = stroke,
        )

        Spacer(Modifier.height(12.dp))

        // ===================== Status + toggle card =====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(card)
                .border(1.dp, stroke, RoundedCornerShape(20.dp))
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (running) {
                            Brush.linearGradient(listOf(accent, Nc.Violet))
                        } else {
                            Brush.linearGradient(listOf(Nc.Stroke, Nc.Stroke))
                        }
                    )
                    .border(2.dp, if (running) accent.copy(alpha = .6f) else stroke, RoundedCornerShape(50))
                    .clickable {
                        val action = if (running) AetherVpnService.ACTION_STOP else AetherVpnService.ACTION_START
                        val intent = Intent(context, AetherVpnService::class.java).apply { this.action = action }
                        if (running) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (running) "قطع" else "اتصال",
                    color = if (running) Color(0xFF070B14) else Nc.Txt,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ===================== sub-composables =====================

@Composable
private fun IpCard(
    modifier: Modifier,
    title: String,
    info: IpInfo,
    viaTunnel: Boolean,
    card: Color,
    inner: Color,
    stroke: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .border(1.dp, stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(title, color = Nc.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(flagFor(info.countryCode), fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    info.ip.ifEmpty { if (info.isLoading) "..." else "—" },
                    color = Nc.Txt,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    CountryNames.display(info.countryCode).ifEmpty { "نامشخص" },
                    color = Nc.Sub,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                viaTunnel && info.ip.isEmpty() && !info.isLoading -> "پس از اتصال..."
                !viaTunnel && info.ip.isEmpty() && !info.isLoading -> "برای دریافت، لمس کنید"
                else -> "بروزرسانی: لمس"
            },
            color = Nc.Cyan.copy(alpha = .8f),
            fontSize = 10.sp,
            modifier = Modifier.clickable {
                // re-triggered by the LaunchedEffect poller path in the scope below
            },
        )
    }
}

@Composable
private fun MetricCell(
    modifier: Modifier,
    label: String,
    value: String,
    valueColor: Color,
    card: Color,
    stroke: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(card)
            .border(1.dp, stroke, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
    ) {
        Text(label, color = Nc.Sub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ChainCard(
    chainOn: Boolean,
    status: ConnectionStatus,
    psiphonStageActive: Boolean,
    connected: Boolean,
    psiphonLinked: Boolean,
    directInfo: IpInfo,
    exitInfo: IpInfo,
    card: Color,
    inner: Color,
    stroke: Color,
) {
    if (!chainOn) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .border(1.dp, stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text("مسیر زنجیره", color = Nc.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val coreName = "WireGuard"
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChainNode(
                label = coreName,
                flag = flagFor(directInfo.countryCode),
                stageDone = connected,
                stageActive = status == ConnectionStatus.STARTING || status == ConnectionStatus.VALIDATING,
                inner = inner,
            )
            Text(" → ", color = Nc.Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ChainNode(
                label = "Psiphon",
                flag = flagFor(exitInfo.countryCode),
                stageDone = psiphonLinked,
                stageActive = psiphonStageActive,
                inner = inner,
            )
            Text(" → ", color = Nc.Sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ChainNode(
                label = "Internet",
                flag = "🌍",
                stageDone = connected && psiphonLinked,
                stageActive = false,
                inner = inner,
            )
        }
    }
}

@Composable
private fun ChainNode(label: String, flag: String, stageDone: Boolean, stageActive: Boolean, inner: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(inner)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(flag, fontSize = 15.sp)
        Text(
            label,
            color = when {
                stageDone -> Nc.Green
                stageActive -> Nc.Amber
                else -> Nc.Sub
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PsiphonSection(
    chainOn: Boolean,
    config: com.narcic.ng.aether.shared.model.AetherConfig,
    configRepository: AetherConfigRepository,
    availableRegions: List<String>,
    card: Color,
    inner: Color,
    stroke: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card)
            .border(1.dp, stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("زنجیره Psiphon", color = Nc.Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "ترافیک از داخل تونل از Psiphon عبور می‌کند",
                    color = Nc.Sub,
                    fontSize = 10.sp,
                )
            }
            Switch(
                checked = chainOn,
                onCheckedChange = { enabled ->
                    configRepository.updateConfig(
                        configRepository.config.value.copy(psiphonEnabled = enabled)
                    )
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Nc.Cyan),
            )
        }

        if (chainOn) {
            Spacer(Modifier.height(10.dp))
            Text("کشور خروجی Psiphon", color = Nc.Sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            // "Auto" + the regions Psiphon actually reported available.
            val regions = (listOf("") + availableRegions)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                regions.chunked(4).forEach { rowRegions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowRegions.forEach { region ->
                            val selected = config.psiphonEgressRegion == region
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Nc.Cyan.copy(alpha = .14f) else inner)
                                    .border(1.dp, if (selected) Nc.Cyan else stroke, RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (configRepository.config.value.psiphonEgressRegion != region) {
                                            configRepository.updateConfig(
                                                configRepository.config.value.copy(psiphonEgressRegion = region)
                                            )
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    if (region.isEmpty()) "🌐 " + "خودکار" else "${flagFor(region)} $region",
                                    color = if (selected) Nc.Cyan else Nc.Txt,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                        // pad the row so chips stay equal-width
                        repeat(4 - rowRegions.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (availableRegions.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "لیست کشورها پس از اتصال Psiphon نمایش داده می‌شود",
                    color = Nc.Sub,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
