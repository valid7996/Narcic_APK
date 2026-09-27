package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.ui.compose.ConfigsHeaderBar
import com.narcic.ng.ui.compose.Nc

/**
 * The server list that lives directly under the connect button.
 */
@Composable
fun MainServerListSection(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    selectedGuid: String?,
    isTesting: Boolean,
    engineIsAwg: Boolean,
    aetherOnly: Boolean = false,
    accent: Color,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareAction: (MainAction) -> Unit,
    onRemoveServer: (String) -> Unit,
    onDeleteAllServers: () -> Unit,
    onAddFromClipboard: () -> Unit,
    onScanVpnQr: () -> Unit,
    onAddManualConfig: (Int) -> Unit,
    onRetest: () -> Unit,
    onAutoSelectBest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shareTarget by remember { mutableStateOf<Pair<String, ProfileItem>?>(null) }
    var showAddOptionsDialog by remember { mutableStateOf(false) }

    if (showAddOptionsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            title = {
                Text(
                    "افزودن کانفیگ " + if (engineIsAwg) "AmneziaWG" else "V2Ray",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Button(
                        onClick = {
                            showAddOptionsDialog = false
                            onAddFromClipboard()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Icon(painterResource(R.drawable.ic_file_24dp), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("افزودن از کلیپ‌بورد")
                    }
                    OutlinedButton(
                        onClick = {
                            showAddOptionsDialog = false
                            onScanVpnQr()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("اسکن بارکد QR")
                    }
                    // Manual add is engine-scoped: the امنزیا page offers only
                    // the WireGuard-family editors and the وی‌تو‌ری page only
                    // the V2Ray-family ones. (Aether's manual add lives solely
                    // on the اتر page, where + opens it directly.)
                    val manualTypes = if (engineIsAwg) {
                        listOf(
                            "افزودن دستی [AmneziaWG]" to EConfigType.AMNEZIAWG.value,
                            "افزودن دستی [Wireguard]" to EConfigType.WIREGUARD.value,
                        )
                    } else {
                        listOf(
                            "افزودن دستی [VMess]" to EConfigType.VMESS.value,
                            "افزودن دستی [VLESS]" to EConfigType.VLESS.value,
                            "افزودن دستی [Shadowsocks]" to EConfigType.SHADOWSOCKS.value,
                            "افزودن دستی [Trojan]" to EConfigType.TROJAN.value,
                            "افزودن دستی [Hysteria2]" to EConfigType.HYSTERIA2.value,
                        )
                    }
                    manualTypes.forEach { (label, type) ->
                        OutlinedButton(
                            onClick = {
                                showAddOptionsDialog = false
                                onAddManualConfig(type)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(painterResource(R.drawable.ic_edit_24dp), contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAddOptionsDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    shareTarget?.let { (guid, profile) ->
        ShareMethodDialog(
            guid = guid,
            profile = profile,
            more = false,
            onDismiss = { shareTarget = null },
            onAction = { action -> shareTarget = null; onShareAction(action) },
            onRemove = { guidToRemove -> shareTarget = null; onRemoveServer(guidToRemove) }
        )
    }

    val groupServers by remember(selectedGroupId) { mainViewModel.serversForGroup(selectedGroupId) }
        .collectAsStateWithLifecycle()

    val sortedAll = remember(groupServers) {
        groupServers.sortedWith(
            compareBy(
                { it.testDelayMillis <= 0L },
                { if (it.testDelayMillis > 0L) it.testDelayMillis else Long.MAX_VALUE },
            )
        )
    }

    val sorted = remember(sortedAll, engineIsAwg, aetherOnly) {
        sortedAll.filter {
            when {
                aetherOnly -> it.profile.configType == EConfigType.AETHER
                else -> isAwgConfig(it.profile.configType) == engineIsAwg
            }
        }
    }
    val top5 = remember(sorted) { sorted.filter { it.testDelayMillis > 0L }.take(5) }
    val isDefaultGroup = selectedGroupId == AppConfig.DEFAULT_SUBSCRIPTION_ID || selectedGroupId.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {

        // ---- Header Bar: کلاینت‌های AmneziaWG + دکمه‌های عملیاتی ----
        ConfigsHeaderBar(
            title = when {
                aetherOnly -> "کانفیگ‌های Aether"
                engineIsAwg -> "کلاینت‌های AmneziaWG"
                else -> "کلاینت‌های V2Ray"
            },
            count = sorted.size,
            accent = accent,
            // Aether page: the + opens the Aether editor directly — no
            // intermediate dialog (manual Aether entry lives only here now).
            onAddClick = if (aetherOnly) {
                { onAddManualConfig(EConfigType.AETHER.value) }
            } else {
                { showAddOptionsDialog = true }
            },
            onSortClick = onAutoSelectBest,
            onTestClick = onRetest,
            onDeleteAllClick = onDeleteAllServers,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Top 5 best servers of THIS group+engine only
        if (top5.isNotEmpty() && !isTesting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .7f)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⭐", fontSize = 13.sp)
                    }
                    Text(
                        text = "بهترین سرورها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Nc.Txt
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${top5.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    top5.forEachIndexed { index, server ->
                        VpnConfigRow(
                            serverCache = server,
                            selectedGuid = selectedGuid,
                            isBest = true,
                            rank = index + 1,
                            accent = accent,
                            onSelectServer = onSelectServer,
                            onEditServer = onEditServer,
                            onShareClick = { guid, profile -> shareTarget = guid to profile },
                            onRemoveServer = onRemoveServer
                        )
                    }
                }
            }
        }

        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        aetherOnly ->
                            "هنوز کانفیگ Aether ندارید\nاز دکمه + بالای لیست یکی اضافه کنید"
                        sortedAll.isNotEmpty() ->
                            "این گروه کانفیگ " + (if (engineIsAwg) "AmneziaWG" else "V2Ray") + " ندارد\nموتور دیگر را امتحان کنید"
                        isDefaultGroup ->
                            "هنوز کانفیگی اضافه نشده\nاز دکمه + بالای لیست استفاده کنید"
                        else ->
                            "این سابسکریپشن هنوز سروری ندارد\nبرای دریافت، از دکمه بروزرسانی بالای صفحه استفاده کنید"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                sorted.forEach { server ->
                    VpnConfigRow(
                        serverCache = server,
                        selectedGuid = selectedGuid,
                        isBest = false,
                        rank = null,
                        accent = accent,
                        onSelectServer = onSelectServer,
                        onEditServer = onEditServer,
                        onShareClick = { guid, profile -> shareTarget = guid to profile },
                        onRemoveServer = onRemoveServer
                    )
                }
            }
        }
    }
}

/**
 * Small colored circular badge showing a short protocol abbreviation
 */
private data class ProtocolBadge(val label: String, val color: Color)

private fun protocolBadge(type: EConfigType): ProtocolBadge = when (type) {
    EConfigType.VMESS -> ProtocolBadge("VM", Nc.BadgeVmess)
    EConfigType.VLESS -> ProtocolBadge("VL", Nc.BadgeVless)
    EConfigType.SHADOWSOCKS -> ProtocolBadge("SS", Nc.Green)
    EConfigType.SOCKS -> ProtocolBadge("SK", Nc.Cyan)
    EConfigType.TROJAN -> ProtocolBadge("TR", Nc.Red)
    EConfigType.WIREGUARD -> ProtocolBadge("WG", Nc.BadgeAwg)
    EConfigType.AMNEZIAWG -> ProtocolBadge("AWG", Nc.BadgeAwg)
    EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> ProtocolBadge("HY2", Nc.BadgeHy2)
    EConfigType.HTTP -> ProtocolBadge("HT", Nc.Violet)
    EConfigType.CUSTOM -> ProtocolBadge("CF", Nc.Violet)
    EConfigType.POLICYGROUP -> ProtocolBadge("PG", Nc.Violet)
    EConfigType.PROXYCHAIN -> ProtocolBadge("PC", Nc.Cyan)
    EConfigType.AETHER -> ProtocolBadge("AE", Nc.BadgeAether)
}

/** AmneziaWG engine == WireGuard-family config types (WIREGUARD + AMNEZIAWG). */
private fun isAwgConfig(type: EConfigType): Boolean =
    type == EConfigType.WIREGUARD || type == EConfigType.AMNEZIAWG

/** ISO-2 country code → flag emoji; "" for non-code values ("DE,SE", "!IR"). */
private fun flagOf(code: String): String {
    val c = code.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return ""
    return c.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}

/**
 * Small engine-specific detail chips drawn under a server card's name, per
 * the 3-page redesign mockup: junk-packet summary for AmneziaWG .conf
 * profiles, WARP transport + carrier chain for Aether, and security/desync
 * hints for the V2Ray family. Pure presentation — read-only over the
 * profile fields, never mutates anything.
 */
private fun profileChips(profile: ProfileItem): List<String> = when (profile.configType) {
    EConfigType.AMNEZIAWG, EConfigType.WIREGUARD -> {
        val conf = profile.awgConfigText.orEmpty()
        buildList {
            Regex("Jc\\s*=\\s*(\\d+)").find(conf)?.let { add("Jc=${it.groupValues[1]}") }
            Regex("MTU\\s*=\\s*(\\d+)").find(conf)?.let { add("MTU ${it.groupValues[1]}") }
            if (Regex("\\bH1\\s*=").containsMatchIn(conf)) add("H1-H4")
        }
    }
    EConfigType.AETHER -> buildList {
        profile.aetherProtocol?.let {
            add(
                when (it) {
                    "wg" -> "WireGuard"
                    "gool" -> "WARP-in-WARP"
                    "mim" -> "MASQUE²"
                    else -> "MASQUE"
                }
            )
        }
        profile.aetherTransport?.takeIf { it == "h2" }?.let { add("HTTP/2") }
        profile.aetherEch?.takeIf { it }?.let { add("ECH") }
        profile.aetherPsiphon?.takeIf { it.isNotBlank() && it != "off" }?.let {
            add("Psiphon: " + when (it) {
                "chain" -> "زنجیره"
                "reverse" -> "معکوس"
                else -> "فقط"
            })
        }
        profile.aetherTor?.takeIf { it.isNotBlank() && it != "off" }?.let {
            add("Tor: " + when (it) {
                "chain" -> "زنجیره"
                "reverse" -> "معکوس"
                else -> "فقط"
            })
        }
        profile.aetherExitLoc?.takeIf { it.isNotBlank() }?.let {
            val flag = flagOf(it)
            add(if (flag.isNotEmpty()) "خروج: $flag ${it.uppercase()}" else "خروج: $it")
        }
    }
    else -> buildList {
        profile.security?.takeIf { it.equals("reality", true) }?.let { add("Reality") }
        profile.flow?.takeIf { it.isNotBlank() }?.let { add("Vision") }
        profile.desyncProfile?.takeIf { it.isNotBlank() && !it.equals("off", true) }?.let { add("دیسینک") }
    }
}

@Composable
private fun VpnConfigRow(
    serverCache: ServersCache,
    selectedGuid: String?,
    isBest: Boolean,
    rank: Int?,
    accent: Color,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareClick: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val guid = serverCache.guid
    val profile = serverCache.profile
    val isSelected = guid == selectedGuid
    val proto = protocolBadge(profile.configType)

    ServerCard(
        name = profile.remarks,
        address = profile.server.orEmpty().ifBlank { "..." },
        pingMs = if (serverCache.testDelayMillis > 0L) serverCache.testDelayMillis.toInt() else null,
        protoLabel = proto.label,
        protoColor = proto.color,
        chips = profileChips(profile),
        selected = isSelected,
        enabled = true,
        accent = accent,
        showEditShare = true,
        rankBadge = rank,
        onSelect = { onSelectServer(guid) },
        onEdit = { onEditServer(guid, profile) },
        onShare = { onShareClick(guid, profile) },
        onDelete = { onRemoveServer(guid) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
    )
}
