package com.narcic.ng.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.R
import com.narcic.ng.aether.platform.PlatformContext
import com.narcic.ng.aether.platform.getSettings
import com.narcic.ng.aether.platform.getSystemUtils
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.AutoDetectRepository
import com.narcic.ng.aether.shared.data.DnsBenchmarkRepository
import com.narcic.ng.aether.shared.model.AetherConfig
import com.narcic.ng.aether.shared.model.AetherLogLevel
import com.narcic.ng.aether.shared.model.AetherNoise
import com.narcic.ng.aether.shared.model.AetherPerfProfile
import com.narcic.ng.aether.shared.model.AetherProtocol
import com.narcic.ng.aether.shared.model.AetherScanMode
import com.narcic.ng.aether.shared.model.AutoDetectPhase
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.aether.shared.model.ProbeStatus
import com.narcic.ng.extension.toast
import com.narcic.ng.extension.toastError
import com.narcic.ng.extension.toastSuccess
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.SettingsEditItem
import com.narcic.ng.ui.compose.SettingsListItem
import com.narcic.ng.ui.compose.SettingsSwitchItem

// ========================= Smart Auto Detect =========================
// Wired to the real AutoDetectRepository (ported verbatim from upstream
// AetherST). Requires the tunnel to be STOPPED/ERROR so probes measure the
// bare network instead of the VPN tunnel.

/**
 * Maps the repository's English step strings onto localized resources.
 * Unknown text (e.g. raw socket exceptions) falls back to the original
 * string so nothing is silently hidden.
 */
@Composable
private fun localizedAutoDetectStep(step: String): String {
    return when {
        step == "Checking internet connection..." -> stringResource(R.string.nps_step_checking_net)
        step == "Checking IPv6 connectivity..." -> stringResource(R.string.nps_step_checking_ipv6)
        step.startsWith("Checking IPv6... attempt ") ->
            stringResource(R.string.nps_step_checking_ipv6_attempt, step.substringAfter("attempt ").trim().toIntOrNull() ?: 1)
        step == "Checking DPI restrictions..." -> stringResource(R.string.nps_step_checking_dpi)
        step == "Detecting ISP..." -> stringResource(R.string.nps_step_detecting_isp)
        step.startsWith("Detecting ISP... attempt ") ->
            stringResource(R.string.nps_step_detecting_isp_attempt, step.substringAfter("attempt ").trim().toIntOrNull() ?: 1)
        step == "Network fingerprint complete" -> stringResource(R.string.nps_step_fingerprint_done)
        step == "Measuring protocol latency..." -> stringResource(R.string.nps_step_protocol_latency)
        step.startsWith("Measuring ") && step.endsWith(" latency...") ->
            stringResource(R.string.nps_step_protocol_one, step.removePrefix("Measuring ").removeSuffix(" latency..."))
        step.startsWith("MASQUE: TCP latency...") || step.startsWith("WireGuard: TCP latency...") || step.startsWith("Gool: TCP latency...") ->
            stringResource(R.string.nps_proto_tcp_latency, step.substringBefore(":"))
        step.startsWith("MASQUE: HTTPS probe...") || step.startsWith("WireGuard: HTTPS probe...") || step.startsWith("Gool: HTTPS probe...") ->
            stringResource(R.string.nps_proto_https_probe, step.substringBefore(":"))
        step.startsWith("MASQUE: HTTPS latency...") || step.startsWith("WireGuard: HTTPS latency...") || step.startsWith("Gool: HTTPS latency...") ->
            stringResource(R.string.nps_proto_https_latency, step.substringBefore(":"))
        step == "Discovering optimal MTU..." -> stringResource(R.string.nps_step_mtu)
        step.startsWith("Probing MTU ") && step.contains("... best ") -> {
            val nums = step.removePrefix("Probing MTU ").removeSuffix("").split("... best ")
            stringResource(R.string.nps_step_mtu_probe, nums.getOrNull(0)?.trim()?.toIntOrNull() ?: 0, nums.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
        }
        step == "Testing obfuscation modes..." -> stringResource(R.string.nps_step_noise)
        step.startsWith("Testing ") && step.endsWith(" obfuscation") -> stringResource(R.string.nps_step_noise)
        step.startsWith("Testing ") && step.contains(" obfuscation ") -> {
            // "Testing <name> obfuscation <i>/<n>..."
            val rest = step.removePrefix("Testing ").removeSuffix("...")
            val idx = rest.substringAfterLast(" ").split("/")
            val namePart = rest.substringBefore(" obfuscation ")
            stringResource(R.string.nps_step_noise_one, namePart, idx.getOrNull(0)?.toIntOrNull() ?: 1, idx.getOrNull(1)?.toIntOrNull() ?: 1)
        }
        step == "Evaluating scan strategies..." -> stringResource(R.string.nps_step_scan)
        step.startsWith("Testing ") && step.contains(" scan ") -> {
            // "Testing <mode> scan <i>/<n>..."
            val rest = step.removePrefix("Testing ").removeSuffix("...")
            val idx = rest.substringAfterLast(" ").split("/")
            val namePart = rest.substringBefore(" scan ")
            stringResource(R.string.nps_step_scan_one, namePart, idx.getOrNull(0)?.toIntOrNull() ?: 1, idx.getOrNull(1)?.toIntOrNull() ?: 1)
        }
        step == "Computing optimal configuration..." -> stringResource(R.string.nps_step_analyzing)
        step == "Optimal configuration found!" -> stringResource(R.string.nps_step_complete)
        step == "Detection failed" -> stringResource(R.string.nps_err_detection_failed)
        step.startsWith("No internet connection") -> stringResource(R.string.nps_err_no_internet)
        else -> step
    }
}

@Composable
fun NarcicAutoDetectPage(
    connectionStatus: ConnectionStatus,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    onApplyResult: (com.narcic.ng.aether.shared.model.AutoDetectResult) -> Unit
) {
    val context = LocalContext.current
    val state by AutoDetectRepository.state.collectAsStateWithLifecycle()
    val phase = state.phase
    val isRunning = phase != AutoDetectPhase.IDLE && phase != AutoDetectPhase.COMPLETE && phase != AutoDetectPhase.ERROR
    val disconnected = connectionStatus == ConnectionStatus.STOPPED ||
            connectionStatus == ConnectionStatus.ERROR ||
            connectionStatus == ConnectionStatus.FAILED

    fun start() {
        AutoDetectRepository.reset()
        AutoDetectRepository.startDetection(PlatformContext(context))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (phase == AutoDetectPhase.IDLE) {
            item {
                Button(
                    onClick = { start() },
                    enabled = disconnected,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF070B14))
                ) { Text(stringResource(R.string.nps_start_scan), fontWeight = FontWeight.Bold) }
                if (!disconnected) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.nps_disconnect_first), color = Nc.Amber, fontSize = 12.sp)
                }
            }
        }
        if (isRunning) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).padding(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.nps_scan_running), color = titleColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${state.progressPercent}%", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = subColor.copy(alpha = 0.15f)
                    )
                    if (state.currentStep.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            localizedAutoDetectStep(state.currentStep),
                            color = accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { AutoDetectRepository.cancel() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.nps_cancel_scan), color = Nc.Red, fontWeight = FontWeight.Bold) }
            }
        }
        if (phase == AutoDetectPhase.ERROR) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).padding(16.dp)) {
                    Text(
                        // Repository error is a technical identifier (exception
                        // message or the fixed "No internet connection..." text
                        // which is mapped above); show the localized frame with
                        // the raw detail inside.
                        stringResource(
                            R.string.nps_generic_error,
                            localizedAutoDetectStep(state.error ?: "")
                        ),
                        color = Nc.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { start() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.nps_retest))
                    }
                }
            }
        }
        val fp = state.finalResult?.networkFingerprint ?: state.liveFingerprint
        if (fp != null && phase != AutoDetectPhase.IDLE) {
            item {
                NpsGroupCard(cardColor) {
                    // Fix 6: meaningful localized network state instead of Yes/No.
                    NpsRow(stringResource(R.string.nps_network), null,
                        when (fp.networkType) {
                            "open" -> stringResource(R.string.nps_network_open)
                            "restricted" -> stringResource(R.string.nps_network_restricted)
                            else -> "—"
                        },
                        null, if (fp.networkType == "restricted") Nc.Amber else Nc.Green, titleColor, subColor)
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    NpsRow(stringResource(R.string.nps_dpi), null,
                        if (fp.supportsDPI) stringResource(R.string.nps_yes) else stringResource(R.string.nps_no),
                        null, if (fp.supportsDPI) Nc.Red else Nc.Green, titleColor, subColor)
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    NpsRow(stringResource(R.string.nps_ipv6), null,
                        if (fp.supportsIPv6) stringResource(R.string.nps_yes) else stringResource(R.string.nps_no),
                        null, if (fp.supportsIPv6) Nc.Green else subColor, titleColor, subColor)
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    NpsRow("UDP", null,
                        if (fp.supportsUDP) stringResource(R.string.nps_yes) else stringResource(R.string.nps_no),
                        null, if (fp.supportsUDP) Nc.Green else Nc.Red, titleColor, subColor)
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    NpsRow(stringResource(R.string.nps_isp), null, fp.carrierOrIsp.ifBlank { "—" }, null, accent, titleColor, subColor)
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    NpsRow(stringResource(R.string.nps_ip), null, fp.ipAddress.ifBlank { "—" }, null, accent, titleColor, subColor)
                }
            }
        }
        if (state.protocolResults.isNotEmpty()) {
            item { Text(stringResource(R.string.nps_protocol_latency), color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(state.protocolResults.size) { i ->
                val r = state.protocolResults[i]
                NpsGroupCard(cardColor) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(when (r.status) {
                            ProbeStatus.SUCCESS -> Nc.Green; ProbeStatus.RUNNING -> Nc.Amber
                            ProbeStatus.FAILED -> Nc.Red; else -> subColor
                        }))
                        Spacer(Modifier.width(10.dp))
                        Text(r.protocol.displayName, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                r.status == ProbeStatus.RUNNING -> "…"
                                r.status == ProbeStatus.SUCCESS -> "${r.latencyMs} ms"
                                else -> r.error ?: "✕"
                            },
                            color = subColor, fontSize = 12.sp
                        )
                    }
                }
            }
        }
        if (state.mtuResult.status != ProbeStatus.IDLE) {
            item {
                Text(stringResource(R.string.nps_path_mtu), color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                NpsGroupCard(cardColor) {
                    NpsRow(
                        stringResource(R.string.nps_path_mtu), null,
                        if (state.mtuResult.status == ProbeStatus.SUCCESS)
                            stringResource(R.string.nps_mtu_optimal, state.mtuResult.discoveredMtu, state.mtuResult.rawPathMtu)
                        else stringResource(R.string.nps_mtu_safe),
                        null,
                        if (state.mtuResult.status == ProbeStatus.SUCCESS) Nc.Green else Nc.Red,
                        titleColor, subColor
                    )
                }
            }
        }
        if (state.noiseResults.isNotEmpty()) {
            item { Text(stringResource(R.string.nps_obfuscation), color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(state.noiseResults.size) { i ->
                val r = state.noiseResults[i]
                NpsGroupCard(cardColor) {
                    NpsRow(r.noise.displayName, null,
                        if (r.effective) stringResource(R.string.nps_yes) else null,
                        null, if (r.effective) Nc.Green else subColor, titleColor, subColor)
                }
            }
        }
        if (state.scanModeResults.isNotEmpty()) {
            item { Text(stringResource(R.string.nps_scan_strategies), color = subColor, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(state.scanModeResults.size) { i ->
                val r = state.scanModeResults[i]
                NpsGroupCard(cardColor) {
                    NpsRow(r.scanMode.name.lowercase().replaceFirstChar { it.uppercase() }, null,
                        if (r.gatewayFound) stringResource(R.string.nps_yes) else null,
                        null, if (r.gatewayFound) Nc.Green else subColor, titleColor, subColor)
                }
            }
        }
        val result = state.finalResult
        if (phase == AutoDetectPhase.COMPLETE && result != null) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cardColor)
                    .border(1.dp, Nc.Green.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.nps_optimal_found), color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.nps_confidence, (result.confidence * 100).toInt()),
                        color = if (result.confidence > 0.7f) Nc.Green else Nc.Amber,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    NpsGroupCard(cardColor) {
                        NpsRow(stringResource(R.string.nps_label_protocol), null, result.recommendedProtocol.displayName, null, accent, titleColor, subColor)
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        NpsRow(stringResource(R.string.nps_label_noise), null, result.recommendedNoise.displayName, null, accent, titleColor, subColor)
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        NpsRow(stringResource(R.string.nps_label_scan_mode), null, result.recommendedScanMode.name.lowercase(), null, accent, titleColor, subColor)
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        NpsRow(stringResource(R.string.nps_label_mtu), null, "${result.recommendedMtu} bytes", null, accent, titleColor, subColor)
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        NpsRow(stringResource(R.string.nps_label_ipmode), null, result.recommendedIpMode.displayName, null, accent, titleColor, subColor)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            onApplyResult(result)
                            AutoDetectRepository.reset()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Nc.Green, contentColor = Color(0xFF070B14))
                    ) { Text(stringResource(R.string.nps_apply), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { AutoDetectRepository.reset() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.nps_retest), color = subColor) }
                }
            }
        }
    }
}

// ========================= DNS Benchmark =========================

@Composable
fun NarcicDnsBenchmarkPage(
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val state by DnsBenchmarkRepository.state.collectAsStateWithLifecycle()
    val running = state.phase == com.narcic.ng.aether.shared.data.DnsBenchmarkPhase.RUNNING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { if (running) DnsBenchmarkRepository.cancel() else DnsBenchmarkRepository.startScan() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = if (running) ButtonDefaults.buttonColors(containerColor = Nc.Red, contentColor = Color.White)
                else ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF070B14))
            ) {
                Text(
                    if (running) stringResource(R.string.nps_cancel_scan) else stringResource(R.string.nps_dns_start),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (running || state.progressPercent in 1..99) {
            item {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = accent, trackColor = subColor.copy(alpha = 0.15f)
                )
            }
        }
        if (state.bestDnsList.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).padding(16.dp)) {
                    Text(stringResource(R.string.nps_dns_best, state.bestDnsList), color = Nc.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            configRepository.updateConfig(
                                configRepository.config.value.copy(dnsEnabled = true, dnsList = state.bestDnsList)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.nps_dns_apply_best)) }
                }
            }
        }
        items(state.results.size) { i ->
            val r = state.results[i]
            NpsGroupCard(cardColor) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.status == ProbeStatus.SUCCESS) Nc.Green else Nc.Red))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${r.name} (${r.ip})", color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (r.status == ProbeStatus.SUCCESS) {
                            Text("${r.successCount}/${r.totalCount}", color = subColor, fontSize = 11.sp)
                        }
                    }
                    Text(if (r.status == ProbeStatus.SUCCESS) "${r.medianMs} ms" else "✕", color = subColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_dns_include_v6),
                    checked = state.includeIpv6,
                    onCheckedChange = { DnsBenchmarkRepository.setIncludeIpv6(it) }
                )
            }
        }
        item {
            NpsGroupCard(cardColor) {
                SettingsEditItem(
                    title = stringResource(R.string.nps_dns_custom),
                    value = state.customDns,
                    keyboardNumber = false,
                    onValueChanged = { DnsBenchmarkRepository.setCustomDns(it) }
                )
            }
        }
    }
}

// ========================= Connection Profiles =========================

@Composable
fun NarcicProfilesPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val presets = listOf(
        Triple("turbo", stringResource(R.string.nps_profile_turbo), stringResource(R.string.nps_profile_turbo_sub)),
        Triple("thorough", stringResource(R.string.nps_profile_thorough), stringResource(R.string.nps_profile_thorough_sub)),
        Triple("stealth", stringResource(R.string.nps_profile_stealth), stringResource(R.string.nps_profile_stealth_sub)),
        Triple("ironclad", stringResource(R.string.nps_profile_ironclad), stringResource(R.string.nps_profile_ironclad_sub)),
        Triple("custom", stringResource(R.string.nps_profile_custom), stringResource(R.string.nps_profile_custom_sub))
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                presets.forEachIndexed { idx, (id, label, subtitle) ->
                    if (idx > 0) NpsDivider(subColor.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { configRepository.applyPreset(id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (config.presetId == id) accent else subColor.copy(alpha = 0.3f)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, color = if (config.presetId == id) accent else titleColor, fontSize = 15.sp, fontWeight = if (config.presetId == id) FontWeight.Bold else FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, color = subColor, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ========================= Protocol & Transport =========================

@Composable
fun NarcicProtocolPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    inner: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsListItem(
                    title = stringResource(R.string.nps_label_protocol),
                    entries = AetherProtocol.entries.map { it.displayName },
                    values = AetherProtocol.entries.map { it.name },
                    selectedValue = config.protocol.name,
                    onSelected = { v ->
                        val p = AetherProtocol.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(protocol = p))
                    }
                )
                if (config.protocol == AetherProtocol.MASQUE) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = stringResource(R.string.nps_proto_h2),
                        checked = config.h2Mode,
                        onCheckedChange = { update(config.copy(h2Mode = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = stringResource(R.string.nps_proto_fragment),
                        checked = config.h2Fragment,
                        onCheckedChange = { update(config.copy(h2Fragment = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = stringResource(R.string.nps_proto_ech),
                        checked = config.echEnabled,
                        onCheckedChange = { update(config.copy(echEnabled = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(
                        title = stringResource(R.string.nps_proto_masque_mtu),
                        value = if (config.masqueMtu > 0) config.masqueMtu.toString() else "",
                        keyboardNumber = true,
                        onValueChanged = { update(config.copy(masqueMtu = it.toIntOrNull()?.coerceIn(0, 9000) ?: 0)) }
                    )
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_proto_no_data_check),
                    checked = config.noDataCheck,
                    onCheckedChange = { update(config.copy(noDataCheck = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = stringResource(R.string.nps_proto_noise),
                    entries = AetherNoise.entries.map { it.displayName },
                    values = AetherNoise.entries.map { it.name },
                    selectedValue = config.noise.name,
                    onSelected = { v ->
                        val n = AetherNoise.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(noise = n))
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = stringResource(R.string.nps_proto_scan_mode),
                    entries = AetherScanMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    values = AetherScanMode.entries.map { it.name },
                    selectedValue = config.scanMode.name,
                    onSelected = { v ->
                        val s = AetherScanMode.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(scanMode = s))
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(
                    title = stringResource(R.string.nps_proto_mtu),
                    value = config.mtu.toString(),
                    keyboardNumber = true,
                    onValueChanged = { update(config.copy(mtu = it.toIntOrNull() ?: 1320)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(
                    title = stringResource(R.string.nps_proto_tls_groups),
                    value = config.tlsGroups,
                    onValueChanged = { update(config.copy(tlsGroups = it)) }
                )
            }
        }
        item {
            // Cloak obfuscation group
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_cloak_title),
                    checked = config.cloakEnabled,
                    onCheckedChange = { update(config.copy(cloakEnabled = it)) }
                )
                if (config.cloakEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_cloak_sni), value = config.cloakSniList, onValueChanged = { update(config.copy(cloakSniList = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_cloak_ttl), value = config.cloakTtlList, onValueChanged = { update(config.copy(cloakTtlList = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = stringResource(R.string.nps_cloak_fragment), checked = config.cloakFragment, onCheckedChange = { update(config.copy(cloakFragment = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = stringResource(R.string.nps_cloak_adaptive), checked = config.cloakAdaptive, onCheckedChange = { update(config.copy(cloakAdaptive = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = stringResource(R.string.nps_cloak_random_sni), checked = config.cloakRandomizeSniCase, onCheckedChange = { update(config.copy(cloakRandomizeSniCase = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_cloak_ports), value = config.cloakFallbackPorts, onValueChanged = { update(config.copy(cloakFallbackPorts = it)) })
                }
            }
        }
    }
}

// ========================= Psiphon Chain =========================

@Composable
fun NarcicChainPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_chain_enable),
                    summary = stringResource(R.string.nps_chain_enable_sub),
                    checked = config.psiphonEnabled,
                    onCheckedChange = { update(config.copy(psiphonEnabled = it)) }
                )
                if (config.psiphonEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsListItem(
                        title = stringResource(R.string.nps_chain_mode),
                        entries = listOf("Auto", "Always", "Fallback"),
                        values = listOf("AUTO", "ALWAYS", "FALLBACK"),
                        selectedValue = config.psiphonChainMode.name,
                        onSelected = { v ->
                            val m = com.narcic.ng.aether.shared.model.PsiphonChainMode.entries.firstOrNull { it.name == v }
                                ?: return@SettingsListItem
                            update(config.copy(psiphonChainMode = m))
                        }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(
                        title = stringResource(R.string.nps_chain_region),
                        value = config.psiphonEgressRegion,
                        onValueChanged = { v -> update(config.copy(psiphonEgressRegion = v.trim().uppercase().take(2))) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(
                        title = stringResource(R.string.nps_chain_socks_port),
                        value = config.psiphonSocksPort,
                        keyboardNumber = true,
                        onValueChanged = { update(config.copy(psiphonSocksPort = it.filter { c -> c.isDigit() }.take(5))) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = stringResource(R.string.nps_chain_via_tunnel),
                        checked = config.psiphonViaAether,
                        onCheckedChange = { update(config.copy(psiphonViaAether = it)) }
                    )
                }
            }
        }
    }
}

// ========================= Cloudflare Zero Trust =========================

@Composable
fun NarcicZeroTrustPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    inner: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    val ztError = config.zeroTrustError()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = stringResource(R.string.nps_zt_team), value = config.teamName, onValueChanged = { update(config.copy(teamName = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_zt_email), value = config.accessEmail, onValueChanged = { update(config.copy(accessEmail = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_zt_gateway), checked = config.useGateway, onCheckedChange = { update(config.copy(useGateway = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_zt_stay), checked = config.ztStaySignedIn, onCheckedChange = { update(config.copy(ztStaySignedIn = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_zt_token_id), value = config.accessId, onValueChanged = { update(config.copy(accessId = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_zt_token_secret), value = config.accessSecret, isPassword = true, onValueChanged = { update(config.copy(accessSecret = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_zt_jwt), value = config.accessToken, isPassword = true, onValueChanged = { v -> update(config.copy(accessToken = v, ztTokenExpiry = config.parseJwtExpiry(v))) })
            }
        }
        if (ztError != null) {
            item { Text(ztError, color = Nc.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ========================= Split Tunneling & Routing =========================

@Composable
fun NarcicSplitPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_split_whole),
                    summary = stringResource(R.string.nps_split_whole_sub),
                    checked = config.tunnelAllApps,
                    onCheckedChange = { update(config.copy(tunnelAllApps = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_split_hotspot),
                    summary = stringResource(R.string.nps_split_hotspot_sub),
                    checked = config.shareHotspot,
                    onCheckedChange = { update(config.copy(shareHotspot = it)) }
                )
            }
        }
        item {
            // Fix 3: purely informational read-only summary. Not clickable, no
            // dialog — no user input is ever requested here.
            NpsGroupCard(cardColor) {
                NpsRow(
                    title = stringResource(R.string.nps_routing_rules_title),
                    subtitle = stringResource(R.string.nps_routing_rules_hint),
                    value = if (config.routingRules.isEmpty())
                        stringResource(R.string.nps_routing_rules_none)
                    else
                        stringResource(R.string.nps_routing_rules_count, config.routingRules.size),
                    icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    enabled = true,
                    onClick = null
                )
            }
        }
    }
}

// ========================= Network & DNS =========================

@Composable
private fun NpsGroupHeader(title: String, color: Color) {
    Text(
        title,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun NarcicNetworkPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color,
    inner: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    val httpLocked = config.psiphonEnabled
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Group 1: Local listeners ----
        item {
            NpsGroupHeader(stringResource(R.string.nps_group_listeners), subColor)
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = stringResource(R.string.nps_net_socks_host), value = config.socksHost, onValueChanged = { update(config.copy(socksHost = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_net_socks_port), value = config.socksPort, keyboardNumber = true, onValueChanged = { update(config.copy(socksPort = it.filter { c -> c.isDigit() }.take(5))) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_net_http_port), value = config.httpPort, keyboardNumber = true, onValueChanged = { update(config.copy(httpPort = it.filter { c -> c.isDigit() }.take(5))) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_net_http_proxy),
                    summary = if (httpLocked) stringResource(R.string.nps_net_http_proxy_locked) else null,
                    checked = config.httpProxyEnabled,
                    enabled = !httpLocked,
                    onCheckedChange = { update(config.copy(httpProxyEnabled = it)) }
                )
            }
        }
        // ---- Group 2: DNS ----
        item {
            NpsGroupHeader(stringResource(R.string.nps_group_dns), subColor)
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_net_custom_dns),
                    checked = config.dnsEnabled,
                    onCheckedChange = { update(config.copy(dnsEnabled = it, dnsList = if (it) config.dnsList.ifBlank { "1.1.1.1,1.0.0.1" } else config.dnsList)) }
                )
                if (config.dnsEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_net_dns_list), value = config.dnsList, onValueChanged = { update(config.copy(dnsList = it.replace(Regex("\\s*,\\s*"), ","))) })
                }
            }
        }
        // ---- Group 3: Protocol endpoints & timing ----
        item {
            NpsGroupHeader(stringResource(R.string.nps_group_endpoints), subColor)
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = stringResource(R.string.nps_net_forced_peer), value = config.peer, onValueChanged = { update(config.copy(peer = it)) })
                if (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_net_wg_peer), value = config.wgPeer, onValueChanged = { update(config.copy(wgPeer = it)) })
                }
                if (config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_net_wiw_outer), value = config.wiwOuter, onValueChanged = { update(config.copy(wiwOuter = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_net_wiw_inner), value = config.wiwInner, onValueChanged = { update(config.copy(wiwInner = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = stringResource(R.string.nps_net_wiw_scan), checked = config.wiwScan, onCheckedChange = { update(config.copy(wiwScan = it)) })
                }
                if (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = stringResource(R.string.nps_net_keepalive), checked = config.keepaliveEnabled, onCheckedChange = { update(config.copy(keepaliveEnabled = it)) })
                    if (config.keepaliveEnabled) {
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        SettingsEditItem(title = stringResource(R.string.nps_net_keepalive_interval), value = config.keepalive.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(keepalive = it.toIntOrNull()?.coerceIn(1, 300) ?: 5)) })
                    }
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_net_cooldown), value = config.wgEndpointCooldownSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(wgEndpointCooldownSecs = it.toIntOrNull()?.coerceIn(30, 3600) ?: 300)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_net_validate), value = config.validateSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(validateSecs = it.toIntOrNull()?.coerceIn(1, 300) ?: 10)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_net_reconnect_interval), value = config.reconnectSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(reconnectSecs = it.toIntOrNull()?.coerceIn(1, 300) ?: 2)) })
            }
        }
    }
}

// ========================= Security & Reliability =========================

@Composable
fun NarcicSecurityPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(title = stringResource(R.string.nps_sec_strict_ks), checked = config.strictKillSwitch, onCheckedChange = { update(config.copy(strictKillSwitch = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_sec_ks), checked = config.killSwitch, onCheckedChange = { update(config.copy(killSwitch = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_sec_ipv6), checked = config.ipv6Leak, onCheckedChange = { update(config.copy(ipv6Leak = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_sec_smart_reconnect), checked = config.smartReconnect, onCheckedChange = { update(config.copy(smartReconnect = it)) })
                if (config.smartReconnect) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_sec_max_retries), value = config.reconnectRetryLimit.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(reconnectRetryLimit = it.toIntOrNull() ?: 10)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_sec_reprovision), checked = config.reprovision, onCheckedChange = { update(config.copy(reprovision = it)) })
            }
        }
    }
}

// ========================= Diagnostics & HEV =========================

@Composable
fun NarcicDiagPage(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val update: (AetherConfig) -> Unit = { configRepository.updateConfig(it) }
    var hevExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = stringResource(R.string.nps_diag_ping_url), value = config.pingUrl, onValueChanged = { update(config.copy(pingUrl = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = stringResource(R.string.nps_diag_app_log),
                    entries = AetherLogLevel.entries.map { it.rawValue },
                    values = AetherLogLevel.entries.map { it.name },
                    selectedValue = config.appLogLevel.name,
                    onSelected = { v ->
                        val l = AetherLogLevel.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(appLogLevel = l))
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = stringResource(R.string.nps_diag_core_log),
                    entries = AetherLogLevel.entries.map { it.rawValue },
                    values = AetherLogLevel.entries.map { it.name },
                    selectedValue = config.coreLogLevel.name,
                    onSelected = { v ->
                        val l = AetherLogLevel.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(coreLogLevel = l))
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = stringResource(R.string.nps_diag_perf),
                    entries = AetherPerfProfile.entries.map { it.displayName },
                    values = AetherPerfProfile.entries.map { it.name },
                    selectedValue = config.perfProfile.name,
                    onSelected = { v ->
                        val p = AetherPerfProfile.entries.firstOrNull { it.name == v } ?: return@SettingsListItem
                        update(config.copy(perfProfile = p))
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = stringResource(R.string.nps_diag_upstream),
                    checked = config.upstreamProxyEnabled,
                    onCheckedChange = { update(config.copy(upstreamProxyEnabled = it, upstreamProxy = if (it) config.upstreamProxy.ifBlank { "socks5://127.0.0.1:1080" } else "")) }
                )
                if (config.upstreamProxyEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_diag_upstream_url), value = config.upstreamProxy, onValueChanged = { update(config.copy(upstreamProxy = it)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_diag_sniffing), checked = config.routeSniffing, onCheckedChange = { update(config.copy(routeSniffing = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = stringResource(R.string.nps_diag_sniff_timeout), value = config.sniffingTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(sniffingTimeoutMs = it.toIntOrNull() ?: 100)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_diag_quick_reconnect), checked = config.quickReconnect, onCheckedChange = { update(config.copy(quickReconnect = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = stringResource(R.string.nps_diag_profile_lock), checked = config.noProfileRetry, onCheckedChange = { update(config.copy(noProfileRetry = it)) })
            }
        }
        item {
            // Fix 9: Advanced HEV — collapsed by default, tap header to expand.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable { hevExpanded = !hevExpanded }
                    .padding(vertical = 4.dp)
            ) {
                NpsRow(
                    title = stringResource(R.string.nps_hev_advanced_title),
                    subtitle = stringResource(R.string.nps_hev_advanced_sub),
                    value = null, icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    onClick = { hevExpanded = !hevExpanded }
                )
                if (hevExpanded) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsListItem(
                        title = stringResource(R.string.nps_hev_log_level),
                        entries = listOf("error", "warn", "info", "debug"),
                        values = listOf("error", "warn", "info", "debug"),
                        selectedValue = config.hevLogLevel,
                        onSelected = { update(config.copy(hevLogLevel = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_hev_connect_timeout), value = config.hevConnectTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevConnectTimeoutMs = it.toIntOrNull()?.coerceIn(500, 120000) ?: 5000)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_hev_rw_timeout), value = config.hevReadWriteTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevReadWriteTimeoutMs = it.toIntOrNull()?.coerceIn(1000, 600000) ?: 60000)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_hev_max_sessions), value = config.hevMaxSessionCount.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevMaxSessionCount = it.toIntOrNull()?.coerceIn(0, 200000) ?: 0)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = stringResource(R.string.nps_hev_mapdns), value = config.hevMapdnsCacheSize.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevMapdnsCacheSize = it.toIntOrNull()?.coerceIn(100, 1000000) ?: 10000)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsListItem(
                        title = stringResource(R.string.nps_hev_udp_mode),
                        entries = listOf("UDP associate", "Over TCP (ICMP)", "Disabled"),
                        values = listOf("udp", "tcp", "off"),
                        selectedValue = config.hevUdpMode.lowercase().let { if (it == "icmp" || it == "true") "tcp" else it },
                        onSelected = { update(config.copy(hevUdpMode = it)) }
                    )
                }
            }
        }
    }
}

// ========================= System & Backup =========================

@Composable
fun NarcicSystemPage(
    configRepository: AetherConfigRepository,
    cardColor: Color,
    subColor: Color,
    titleColor: Color,
    accent: Color
) {
    val context = LocalContext.current
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                // Fix 4: explicit success/failure toast; picker cancel stays silent.
                NpsRow(
                    title = stringResource(R.string.nps_sys_export),
                    subtitle = stringResource(R.string.nps_sys_export_sub),
                    value = null, icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    onClick = {
                        val json = configRepository.getFullConfigJson()
                        val utils = getSystemUtils(PlatformContext(context))
                        utils.exportFile("NarcicPS_Backup.astf", json) { success ->
                            if (success) context.toastSuccess(R.string.nps_backup_exported)
                            else context.toastError(R.string.nps_backup_export_failed)
                        }
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                NpsRow(
                    title = stringResource(R.string.nps_sys_restore),
                    subtitle = stringResource(R.string.nps_sys_restore_sub),
                    value = null, icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    onClick = {
                        val utils = getSystemUtils(PlatformContext(context))
                        utils.importFile { content ->
                            if (content != null) {
                                if (configRepository.restoreFullConfig(content)) {
                                    context.toastSuccess(R.string.nps_backup_restored)
                                } else {
                                    context.toastError(R.string.nps_backup_restore_invalid)
                                }
                            }
                            // content == null → user cancelled the picker: silent.
                        }
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                NpsRow(
                    title = stringResource(R.string.nps_sys_reset),
                    subtitle = stringResource(R.string.nps_sys_reset_sub),
                    value = null, icon = null, iconTint = Nc.Red, titleColor = Nc.Red, subColor = subColor,
                    onClick = { showResetConfirm = true }
                )
            }
        }
    }
    // Fix 2: destructive reset requires explicit confirmation.
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.nps_reset_confirm_title)) },
            text = { Text(stringResource(R.string.nps_reset_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    configRepository.resetToDefaults()
                    context.toast(R.string.nps_reset_done)
                }) {
                    Text(stringResource(R.string.nps_reset_confirm_yes), color = Nc.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
