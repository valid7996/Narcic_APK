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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.R
import com.narcic.ng.aether.platform.PlatformContext
import com.narcic.ng.aether.platform.getSettings
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.AutoDetectRepository
import com.narcic.ng.aether.shared.data.DnsBenchmarkRepository
import com.narcic.ng.aether.shared.data.DnsProbeResult
import com.narcic.ng.aether.shared.data.IpInfoRepository
import com.narcic.ng.aether.shared.model.AetherConfig
import com.narcic.ng.aether.shared.model.AetherLogLevel
import com.narcic.ng.aether.shared.model.AetherNoise
import com.narcic.ng.aether.shared.model.AetherPerfProfile
import com.narcic.ng.aether.shared.model.AetherProtocol
import com.narcic.ng.aether.shared.model.AetherScanMode
import com.narcic.ng.aether.shared.model.AutoDetectPhase
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.aether.shared.model.ProbeStatus
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.SettingsEditItem
import com.narcic.ng.ui.compose.SettingsListItem
import com.narcic.ng.ui.compose.SettingsSwitchItem

// ========================= Smart Auto Detect =========================
// Wired to the real AutoDetectRepository (ported verbatim from upstream
// AetherST). Requires the tunnel to be STOPPED/ERROR so probes measure the
// bare network instead of the VPN tunnel.

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
                        Text(state.currentStep, color = accent, fontSize = 12.sp, maxLines = 1)
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
                        stringResource(R.string.nps_generic_error, state.error ?: ""),
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
                    NpsRow(stringResource(R.string.nps_network), null,
                        when (fp.networkType) { "open" -> stringResource(R.string.nps_no); "restricted" -> stringResource(R.string.nps_yes); else -> "—" },
                        null, accent, titleColor, subColor)
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
        "turbo" to stringResource(R.string.nps_label_mtu).let { "Turbo" },
        "thorough" to "Thorough",
        "stealth" to "Stealth",
        "ironclad" to "Ironclad",
        "custom" to "Custom"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                presets.forEachIndexed { idx, (id, label) ->
                    if (idx > 0) NpsDivider(subColor.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { configRepository.applyPreset(id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (config.presetId == id) accent else subColor.copy(alpha = 0.3f)))
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = if (config.presetId == id) accent else titleColor, fontSize = 15.sp, fontWeight = if (config.presetId == id) FontWeight.Bold else FontWeight.Medium)
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
                        title = "HTTP/2 fallback",
                        checked = config.h2Mode,
                        onCheckedChange = { update(config.copy(h2Mode = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = "Packet fragmentation",
                        checked = config.h2Fragment,
                        onCheckedChange = { update(config.copy(h2Fragment = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = "ECH (Encrypted Client Hello)",
                        checked = config.echEnabled,
                        onCheckedChange = { update(config.copy(echEnabled = it)) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(
                        title = "MASQUE inner MTU",
                        value = if (config.masqueMtu > 0) config.masqueMtu.toString() else "",
                        keyboardNumber = true,
                        onValueChanged = { update(config.copy(masqueMtu = it.toIntOrNull()?.coerceIn(0, 9000) ?: 0)) }
                    )
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = "Disable data verification",
                    checked = config.noDataCheck,
                    onCheckedChange = { update(config.copy(noDataCheck = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = "Bypass / obfuscation (noise)",
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
                    title = "Speed strategy (scan mode)",
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
                    title = "Custom MTU",
                    value = config.mtu.toString(),
                    keyboardNumber = true,
                    onValueChanged = { update(config.copy(mtu = it.toIntOrNull() ?: 1320)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(
                    title = "TLS key groups",
                    value = config.tlsGroups,
                    onValueChanged = { update(config.copy(tlsGroups = it)) }
                )
            }
        }
        item {
            // Cloak obfuscation group
            NpsGroupCard(cardColor) {
                SettingsSwitchItem(
                    title = "Cloak decoy traffic",
                    checked = config.cloakEnabled,
                    onCheckedChange = { update(config.copy(cloakEnabled = it)) }
                )
                if (config.cloakEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "Decoy SNI list", value = config.cloakSniList, onValueChanged = { update(config.copy(cloakSniList = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "TTL list", value = config.cloakTtlList, onValueChanged = { update(config.copy(cloakTtlList = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = "Fragment real ClientHello", checked = config.cloakFragment, onCheckedChange = { update(config.copy(cloakFragment = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = "Adaptive statistics", checked = config.cloakAdaptive, onCheckedChange = { update(config.copy(cloakAdaptive = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = "Randomize SNI case", checked = config.cloakRandomizeSniCase, onCheckedChange = { update(config.copy(cloakRandomizeSniCase = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "Fallback ports", value = config.cloakFallbackPorts, onValueChanged = { update(config.copy(cloakFallbackPorts = it)) })
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
                    title = "Enable Psiphon chain",
                    summary = "Route tunnel traffic through a second Psiphon hop",
                    checked = config.psiphonEnabled,
                    onCheckedChange = { update(config.copy(psiphonEnabled = it)) }
                )
                if (config.psiphonEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsListItem(
                        title = "Chain mode",
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
                        title = "Exit region (ISO code, empty = auto)",
                        value = config.psiphonEgressRegion,
                        onValueChanged = { v -> update(config.copy(psiphonEgressRegion = v.trim().uppercase().take(2))) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(
                        title = "Psiphon SOCKS port",
                        value = config.psiphonSocksPort,
                        keyboardNumber = true,
                        onValueChanged = { update(config.copy(psiphonSocksPort = it.filter { c -> c.isDigit() }.take(5))) }
                    )
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(
                        title = "Bootstrap via tunnel",
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
                SettingsEditItem(title = "Organization team name", value = config.teamName, onValueChanged = { update(config.copy(teamName = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Access email", value = config.accessEmail, onValueChanged = { update(config.copy(accessEmail = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Use Cloudflare Gateway", checked = config.useGateway, onCheckedChange = { update(config.copy(useGateway = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Stay signed in", checked = config.ztStaySignedIn, onCheckedChange = { update(config.copy(ztStaySignedIn = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Service token ID", value = config.accessId, onValueChanged = { update(config.copy(accessId = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Service token secret", value = config.accessSecret, isPassword = true, onValueChanged = { update(config.copy(accessSecret = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Access token (JWT)", value = config.accessToken, isPassword = true, onValueChanged = { v -> update(config.copy(accessToken = v, ztTokenExpiry = config.parseJwtExpiry(v))) })
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
                    title = "Tunnel whole device",
                    summary = "Route all apps through the tunnel",
                    checked = config.tunnelAllApps,
                    onCheckedChange = { update(config.copy(tunnelAllApps = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = "Share via hotspot",
                    summary = "Expose the local SOCKS proxy to hotspot clients",
                    checked = config.shareHotspot,
                    onCheckedChange = { update(config.copy(shareHotspot = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(
                    title = "Routing rules (JSON, [{\"pattern\":...,\"mode\":\"DIRECT|BLOCK|TUNNEL\"}])",
                    value = config.routingRules.joinToString(", ") { "${it.pattern}=${it.mode.name}" },
                    onValueChanged = { /* read-only display of rules managed via import */ }
                )
            }
        }
    }
}

// ========================= Network & DNS =========================

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
        item {
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = "SOCKS host", value = config.socksHost, onValueChanged = { update(config.copy(socksHost = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "SOCKS port", value = config.socksPort, keyboardNumber = true, onValueChanged = { update(config.copy(socksPort = it.filter { c -> c.isDigit() }.take(5))) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "HTTP port", value = config.httpPort, keyboardNumber = true, onValueChanged = { update(config.copy(httpPort = it.filter { c -> c.isDigit() }.take(5))) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = "Internal HTTP proxy",
                    summary = if (httpLocked) "Locked while Psiphon chain is enabled" else null,
                    checked = config.httpProxyEnabled,
                    enabled = !httpLocked,
                    onCheckedChange = { update(config.copy(httpProxyEnabled = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(
                    title = "Custom DNS",
                    checked = config.dnsEnabled,
                    onCheckedChange = { update(config.copy(dnsEnabled = it, dnsList = if (it) config.dnsList.ifBlank { "1.1.1.1,1.0.0.1" } else config.dnsList)) }
                )
                if (config.dnsEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "DNS server list", value = config.dnsList, onValueChanged = { update(config.copy(dnsList = it.replace(Regex("\\s*,\\s*"), ","))) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Forced peer IP (e.g. 1.2.3.4:443)", value = config.peer, onValueChanged = { update(config.copy(peer = it)) })
                if (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "WireGuard peer endpoint", value = config.wgPeer, onValueChanged = { update(config.copy(wgPeer = it)) })
                }
                if (config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "WiW outer endpoint", value = config.wiwOuter, onValueChanged = { update(config.copy(wiwOuter = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "WiW inner endpoint", value = config.wiwInner, onValueChanged = { update(config.copy(wiwInner = it)) })
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = "WiW endpoint scan", checked = config.wiwScan, onCheckedChange = { update(config.copy(wiwScan = it)) })
                }
                if (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsSwitchItem(title = "Keepalive packets", checked = config.keepaliveEnabled, onCheckedChange = { update(config.copy(keepaliveEnabled = it)) })
                    if (config.keepaliveEnabled) {
                        NpsDivider(subColor.copy(alpha = 0.15f))
                        SettingsEditItem(title = "Keepalive interval (s)", value = config.keepalive.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(keepalive = it.toIntOrNull()?.coerceIn(1, 300) ?: 5)) })
                    }
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "Endpoint cooldown (s)", value = config.wgEndpointCooldownSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(wgEndpointCooldownSecs = it.toIntOrNull()?.coerceIn(30, 3600) ?: 300)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Validation interval (s)", value = config.validateSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(validateSecs = it.toIntOrNull()?.coerceIn(1, 300) ?: 10)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Reconnect interval (s)", value = config.reconnectSecs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(reconnectSecs = it.toIntOrNull()?.coerceIn(1, 300) ?: 2)) })
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
                SettingsSwitchItem(title = "Strict kill switch", checked = config.strictKillSwitch, onCheckedChange = { update(config.copy(strictKillSwitch = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Kill switch", checked = config.killSwitch, onCheckedChange = { update(config.copy(killSwitch = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "IPv6 leak protection", checked = config.ipv6Leak, onCheckedChange = { update(config.copy(ipv6Leak = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Smart reconnect", checked = config.smartReconnect, onCheckedChange = { update(config.copy(smartReconnect = it)) })
                if (config.smartReconnect) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "Max retries", value = config.reconnectRetryLimit.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(reconnectRetryLimit = it.toIntOrNull() ?: 10)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Reprovision profile on auth failure", checked = config.reprovision, onCheckedChange = { update(config.copy(reprovision = it)) })
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                SettingsEditItem(title = "Ping test URL", value = config.pingUrl, onValueChanged = { update(config.copy(pingUrl = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = "App log level",
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
                    title = "Core log level",
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
                    title = "Performance profile",
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
                    title = "External upstream proxy",
                    checked = config.upstreamProxyEnabled,
                    onCheckedChange = { update(config.copy(upstreamProxyEnabled = it, upstreamProxy = if (it) config.upstreamProxy.ifBlank { "socks5://127.0.0.1:1080" } else "")) }
                )
                if (config.upstreamProxyEnabled) {
                    NpsDivider(subColor.copy(alpha = 0.15f))
                    SettingsEditItem(title = "Upstream proxy URL", value = config.upstreamProxy, onValueChanged = { update(config.copy(upstreamProxy = it)) })
                }
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Domain sniffing", checked = config.routeSniffing, onCheckedChange = { update(config.copy(routeSniffing = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "Sniffing timeout (ms)", value = config.sniffingTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(sniffingTimeoutMs = it.toIntOrNull() ?: 100)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Quick reconnect", checked = config.quickReconnect, onCheckedChange = { update(config.copy(quickReconnect = it)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsSwitchItem(title = "Strict profile lock (no retry)", checked = config.noProfileRetry, onCheckedChange = { update(config.copy(noProfileRetry = it)) })
            }
        }
        item {
            // HEV engine group — Android-only native tun2socks parameters.
            NpsGroupCard(cardColor) {
                SettingsListItem(
                    title = "HEV log level",
                    entries = listOf("error", "warn", "info", "debug"),
                    values = listOf("error", "warn", "info", "debug"),
                    selectedValue = config.hevLogLevel,
                    onSelected = { update(config.copy(hevLogLevel = it)) }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "HEV connect timeout (ms)", value = config.hevConnectTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevConnectTimeoutMs = it.toIntOrNull()?.coerceIn(500, 120000) ?: 5000)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "HEV read/write timeout (ms)", value = config.hevReadWriteTimeoutMs.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevReadWriteTimeoutMs = it.toIntOrNull()?.coerceIn(1000, 600000) ?: 60000)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "HEV max sessions (0 = unlimited)", value = config.hevMaxSessionCount.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevMaxSessionCount = it.toIntOrNull()?.coerceIn(0, 200000) ?: 0)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsEditItem(title = "HEV mapdns cache size", value = config.hevMapdnsCacheSize.toString(), keyboardNumber = true, onValueChanged = { update(config.copy(hevMapdnsCacheSize = it.toIntOrNull()?.coerceIn(100, 1000000) ?: 10000)) })
                NpsDivider(subColor.copy(alpha = 0.15f))
                SettingsListItem(
                    title = "HEV UDP forwarding mode",
                    entries = listOf("UDP associate", "Over TCP (ICMP)", "Disabled"),
                    values = listOf("udp", "tcp", "off"),
                    selectedValue = config.hevUdpMode.lowercase().let { if (it == "icmp" || it == "true") "tcp" else it },
                    onSelected = { update(config.copy(hevUdpMode = it)) }
                )
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            NpsGroupCard(cardColor) {
                // Export backup: real Bridge.saveFile path via SystemUtils.exportFile.
                NpsRow(
                    title = "Export full backup (.astf)",
                    subtitle = "Save the whole PS configuration to a file",
                    value = null, icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    onClick = {
                        val json = configRepository.getFullConfigJson()
                        val utils = com.narcic.ng.aether.platform.getSystemUtils(PlatformContext(context))
                        utils.exportFile("NarcicPS_Backup.astf", json) { }
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                NpsRow(
                    title = "Restore full backup",
                    subtitle = "Import a previously exported .astf file",
                    value = null, icon = null, iconTint = accent, titleColor = titleColor, subColor = subColor,
                    onClick = {
                        val utils = com.narcic.ng.aether.platform.getSystemUtils(PlatformContext(context))
                        utils.importFile { content ->
                            if (content != null) configRepository.restoreFullConfig(content)
                        }
                    }
                )
                NpsDivider(subColor.copy(alpha = 0.15f))
                NpsRow(
                    title = "Reset to factory defaults",
                    subtitle = "Restore every PS setting to its default value",
                    value = null, icon = null, iconTint = Nc.Red, titleColor = Nc.Red, subColor = subColor,
                    onClick = { configRepository.resetToDefaults() }
                )
            }
        }
    }
}
