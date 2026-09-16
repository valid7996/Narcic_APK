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
    accent: Color,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareAction: (MainAction) -> Unit,
    onRemoveServer: (String) -> Unit,
    onDeleteAllServers: () -> Unit,
    onAddFromClipboard: () -> Unit,
    onScanVpnQr: () -> Unit,
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
                    "افزودن کانفیگ",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    val sorted = remember(sortedAll, engineIsAwg) {
        sortedAll.filter { isAwgConfig(it.profile.configType) == engineIsAwg }
    }
    val top5 = remember(sorted) { sorted.filter { it.testDelayMillis > 0L }.take(5) }
    val isDefaultGroup = selectedGroupId == AppConfig.DEFAULT_SUBSCRIPTION_ID || selectedGroupId.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {

        // ---- Header Bar: کلاینت‌های AmneziaWG + دکمه‌های عملیاتی ----
        ConfigsHeaderBar(
            title = if (engineIsAwg) "کلاینت‌های AmneziaWG" else "کلاینت‌های V2Ray",
            count = sorted.size,
            accent = accent,
            onAddClick = { showAddOptionsDialog = true },
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
