package com.narcic.ng.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.aether.shared.data.AetherConfigRepository
import com.narcic.ng.aether.shared.data.PsiphonEgressRegistry
import com.narcic.ng.aether.shared.model.AetherConfig
import com.narcic.ng.aether.shared.model.AetherProtocol
import com.narcic.ng.aether.shared.util.CountryNames
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.util.CountryFlags

/**
 * Compact pre-connection setup card for the Narcic PS engine on the main
 * screen. Pure presentation + existing repository writes only:
 *  - Method selector: MASQUE / GOOL / WireGuard / Zero Trust (AetherProtocol).
 *  - Psiphon Chain toggle (config.psiphonEnabled).
 *  - Exit-country chips (config.psiphonEgressRegion) fed by the real
 *    PsiphonEgressRegistry — shown only while the chain is on AND the
 *    selected protocol actually supports it (Zero Trust does not).
 * No connect logic, no state ownership, no fake data.
 */

// Psiphon Chain is surfaced for the WireGuard method only. GOOL, MASQUE and
// Zero Trust hide the whole chain section (Issue 2): this is display gating
// only — the backend's chain support and config semantics are untouched.
private fun AetherConfig.supportsPsiphonChain(): Boolean = protocol == AetherProtocol.WG

private fun methodLabel(p: AetherProtocol): String = when (p) {
    AetherProtocol.MASQUE -> "MASQUE"
    AetherProtocol.GOOL -> "GOOL"
    AetherProtocol.WG -> "WireGuard"
    AetherProtocol.ZERO_TRUST -> "Zero Trust"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupCard(
    config: AetherConfig,
    configRepository: AetherConfigRepository,
    availableRegions: List<String>,
    accent: Color,
) {
    val isDark = LocalDarkTheme.current
    val card = Color.White.copy(alpha = if (isDark) .05f else .9f)
    val stroke = Color.White.copy(alpha = if (isDark) .1f else .08f)
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val inner = if (isDark) Color.White.copy(alpha = .05f) else Color.Black.copy(alpha = .04f)

    var showMethodSheet by remember { mutableStateOf(false) }
    var showRegionSheet by remember { mutableStateOf(false) }

    // Zero Trust needs team + one auth method before it can connect
    // (AetherConfig.zeroTrustError drives both this badge and the start gate).
    val isZtUnconfigured =
        config.protocol == AetherProtocol.ZERO_TRUST &&
        config.zeroTrustError() != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(card)
            .border(1.dp, stroke, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        // ── Method selector row ─────────────────────────────────────────
        Text("روش اتصال", color = txtSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(inner)
                .border(1.dp, stroke, RoundedCornerShape(13.dp))
                .clickable { showMethodSheet = true }
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                methodLabel(config.protocol),
                color = txtMain, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (isZtUnconfigured) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "تنظیم‌نشده",
                    color = Nc.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Nc.Red.copy(alpha = .13f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "تغییر",
                color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = .13f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }

        if (isZtUnconfigured) {
            Spacer(Modifier.height(8.dp))
            Text(
                "اطلاعات تیم و احراز هویت در تنظیمات Zero Trust وارد نشده است.",
                color = Nc.Red, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Psiphon chain (Issue 2: visible for the WireGuard method only) ──
        if (config.supportsPsiphonChain()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("زنجیره Psiphon", color = txtMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "خروجی ترافیک از Psiphon",
                        color = txtSub, fontSize = 10.sp,
                    )
                }
                Switch(
                    checked = config.psiphonEnabled,
                    onCheckedChange = { enabled ->
                        configRepository.updateConfig(
                            configRepository.config.value.copy(psiphonEnabled = enabled)
                        )
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent),
                )
            }

            // ── Exit-country selector: only when the chain is on ────────────
            AnimatedVisibility(
                visible = config.psiphonEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(inner)
                            .border(1.dp, stroke, RoundedCornerShape(13.dp))
                            .clickable { showRegionSheet = true }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("کشور خروجی", color = txtSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                exitRegionLabel(config.psiphonEgressRegion),
                                color = txtMain, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Text("▾", color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (availableRegions.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "لیست کشورها پس از اتصال Psiphon نمایش داده می‌شود",
                            color = txtSub, fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }

    // ── Method picker sheet ─────────────────────────────────────────────
    if (showMethodSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMethodSheet = false },
            containerColor = if (isDark) Nc.Bg else Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SheetTitle("روش اتصال", accent)
            listOf(
                AetherProtocol.MASQUE,
                AetherProtocol.GOOL,
                AetherProtocol.WG,
                AetherProtocol.ZERO_TRUST,
            ).forEach { p ->
                val ztPending = p == AetherProtocol.ZERO_TRUST && config.zeroTrustError() != null
                SheetOptionRow(
                    title = methodLabel(p),
                    subtitle = if (ztPending) {
                        "Cloudflare for Organizations — تنظیم‌نشده (نیاز به تیم و احراز هویت)"
                    } else {
                        p.description
                    },
                    selected = p == config.protocol,
                    accent = accent,
                    txtMain = txtMain,
                    txtSub = txtSub,
                    inner = inner,
                    onClick = {
                        if (configRepository.config.value.protocol != p) {
                            configRepository.updateConfig(
                                configRepository.config.value.copy(protocol = p)
                            )
                        }
                        showMethodSheet = false
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    // ── Exit-country picker sheet ("Auto" + real PsiphonEgressRegistry) ──
    if (showRegionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRegionSheet = false },
            containerColor = if (isDark) Nc.Bg else Color.White,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SheetTitle("کشور خروجی Psiphon", accent)
            ExitRegionOptionRow(
                flag = "🌐",
                title = "خودکار",
                subtitle = "انتخاب خودکار کشور توسط Psiphon",
                selected = config.psiphonEgressRegion.isEmpty(),
                accent = accent,
                txtMain = txtMain,
                txtSub = txtSub,
                inner = inner,
                onClick = {
                    if (configRepository.config.value.psiphonEgressRegion.isNotEmpty()) {
                        configRepository.updateConfig(
                            configRepository.config.value.copy(psiphonEgressRegion = "")
                        )
                    }
                    showRegionSheet = false
                },
            )
            availableRegions.forEach { region ->
                ExitRegionOptionRow(
                    flag = flagEmojiFor(region),
                    title = regionFaName(region),
                    subtitle = "$region · ${CountryNames.display(region)}",
                    selected = config.psiphonEgressRegion == region,
                    accent = accent,
                    txtMain = txtMain,
                    txtSub = txtSub,
                    inner = inner,
                    onClick = {
                        if (configRepository.config.value.psiphonEgressRegion != region) {
                            configRepository.updateConfig(
                                configRepository.config.value.copy(psiphonEgressRegion = region)
                            )
                        }
                        showRegionSheet = false
                    },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SheetTitle(text: String, accent: Color) {
    Text(
        text,
        color = accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    txtMain: Color,
    txtSub: Color,
    inner: Color,
    flag: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) accent.copy(alpha = .13f) else inner)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flag.isNotEmpty()) {
            Text(flag, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) accent else txtMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = txtSub, fontSize = 10.sp)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text("✓", color = accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// Issue 1: Persian display name for a Psiphon region. Uses the project's
// existing CountryFlags Persian map (the same source the location picker
// uses); falls back to the existing CountryNames table, then the code.
private fun regionFaName(region: String): String {
    if (region.isEmpty()) return "خودکار"
    val fa = CountryFlags.displayNameFa(flagEmojiFor(region))
    return if (fa != region) fa else CountryNames.display(region).ifEmpty { region }
}

@Composable
private fun ExitRegionOptionRow(
    flag: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    txtMain: Color,
    txtSub: Color,
    inner: Color,
    onClick: () -> Unit,
) {
    SheetOptionRow(
        title, subtitle, selected, accent, txtMain, txtSub, inner,
        flag = flag, onClick = onClick,
    )
}

private fun exitRegionLabel(region: String): String =
    if (region.isEmpty()) "🌐 خودکار" else "${flagEmojiFor(region)} ${regionFaName(region)}"

private fun flagEmojiFor(code: String): String {
    val c = code.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌐"
    return c.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}
