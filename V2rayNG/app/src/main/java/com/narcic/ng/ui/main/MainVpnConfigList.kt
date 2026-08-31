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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
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
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.compose.Nc

/**
 * The server list that lives directly under the connect button.
 *
 * - Horizontal group tabs on top: "پیش‌فرض" (manually-entered servers, like
 *   V2rayNg) plus one tab per subscription the user added (e.g. "Narcic
 *   Irancell").  Switching tabs switches which group's servers are shown.
 * - ONE "Test" button, scoped to whichever tab/group is currently selected
 *   (never tests every group at once).
 * - After a test finishes, servers are always sorted best-ping-first, both
 *   in the "Best Servers" (top 5) box and in the full list beneath it.
 * - Rows for servers that belong to one of the built-in Narcic subscriptions
 *   (see [AppConfig.DEFAULT_SUBSCRIPTIONS] / [isDefaultConfig]) never show
 *   Share or Edit -- those configs are curated by Narcic and aren't meant to
 *   be modified or re-shared by the customer. Only Delete stays available,
 *   so a customer can still drop a single bad server from the list.
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
    onAddFromClipboard: () -> Unit,
    onScanVpnQr: () -> Unit,
    onRetest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shareTarget by remember { mutableStateOf<Pair<String, ProfileItem>?>(null) }
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

    // Only the servers belonging to the currently selected tab/group --
    // this is what makes "Test" and the list itself scoped to one group.
    val groupServers by remember(selectedGroupId) { mainViewModel.serversForGroup(selectedGroupId) }
        .collectAsStateWithLifecycle()

    // Best ping first, always -- both right after a test and on every
    // recomposition, so the ordering never goes stale.
    val sortedAll = remember(groupServers) {
        groupServers.sortedWith(
            compareBy(
                { it.testDelayMillis <= 0L },
                { if (it.testDelayMillis > 0L) it.testDelayMillis else Long.MAX_VALUE },
            )
        )
    }
    // Scoped further to whichever engine (AmneziaWG / V2Ray) is active in
    // the EngineSwitch above — the two engines never run at once, so the
    // list only ever shows configs the user could actually connect with
    // right now.
    val sorted = remember(sortedAll, engineIsAwg) {
        sortedAll.filter { isAwgConfig(it.profile.configType) == engineIsAwg }
    }
    val top5 = remember(sorted) { sorted.filter { it.testDelayMillis > 0L }.take(5) }
    val isDefaultGroup = selectedGroupId == AppConfig.DEFAULT_SUBSCRIPTION_ID || selectedGroupId.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onRetest,
                enabled = !isTesting && sorted.isNotEmpty(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = if (isTesting) 0.dp else 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = accent.copy(alpha = 0.25f),
                        spotColor = accent.copy(alpha = 0.35f)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (isTesting) "در حال تست…" else "تست", color = Color.White)
            }
        }

        // Top 5 best servers of THIS group+engine only -- clearly identifiable as recommended.
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
                        Text(text = "\u2b50", fontSize = 13.sp)
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
                            "این گروه کانفیگ ${if (engineIsAwg) "AmneziaWG" else "V2Ray"} ندارد\nموتور دیگر را امتحان کنید"
                        isDefaultGroup ->
                            "هنوز کانفیگی اضافه نشده\nاز «افزودن از کلیپ‌بورد» یا «اسکن QR» استفاده کنید"
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
            Text(
                text = "لیست سرورها (${sorted.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
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

        // Manually adding a config always lands in "پیش‌فرض" (Default),
        // exactly like V2rayNg -- available no matter which tab is open.
        if (isDefaultGroup || groups.size <= 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddFromClipboard,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = accent.copy(alpha = 0.25f),
                            spotColor = accent.copy(alpha = 0.35f)
                        ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("افزودن از کلیپ‌بورد", fontWeight = FontWeight.Bold, color = Color.White)
                }
                OutlinedButton(
                    onClick = onScanVpnQr,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("اسکن QR")
                }
            }
        }
    }
}

/**
 * Small colored circular badge showing a short protocol abbreviation
 * (e.g. "VL" for VLESS, "WG" for WireGuard) so rows are visually
 * distinguishable at a glance instead of relying on plain text alone.
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
}

/** AmneziaWG engine == WireGuard-family config types (WIREGUARD + AMNEZIAWG). */
private fun isAwgConfig(type: EConfigType): Boolean =
    type == EConfigType.WIREGUARD || type == EConfigType.AMNEZIAWG

/**
 * True when [profile] belongs to one of the built-in Narcic subscriptions
 * (see [AppConfig.DEFAULT_SUBSCRIPTIONS]). Used to hide Share/Edit for
 * those servers -- the customer can still Delete a single bad entry, but
 * can't modify or re-export the curated config itself.
 */
private fun isDefaultConfig(profile: ProfileItem): Boolean {
    val subUrl = MmkvManager.decodeSubscription(profile.subscriptionId)?.url ?: return false
    return AppConfig.isDefaultSubscriptionUrl(subUrl)
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
    val profile = serverCache.profile
    val isSelected = serverCache.guid == selectedGuid
    // Curated Narcic subscriptions (Irancell / NG-JSON / NG-WireGuard) are
    // read-only content: no Edit/Share, only Delete (a customer can still
    // drop a single bad server locally) -- see isDefaultConfig() above.
    val isDefault = remember(profile.subscriptionId) { isDefaultConfig(profile) }
    val badge = remember(profile.configType) { protocolBadge(profile.configType) }
    val pingMs = if (serverCache.testDelayMillis > 0L) serverCache.testDelayMillis.toInt() else null

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        ServerCard(
            name = profile.remarks.ifBlank { "بدون‌نام" },
            address = profile.getServerAddressAndPort().ifBlank { simpleProtocolLabel(profile) },
            pingMs = pingMs,
            protoLabel = badge.label,
            protoColor = badge.color,
            selected = isSelected,
            enabled = true,
            accent = accent,
            showEditShare = !isDefault,
            rankBadge = rank,
            onSelect = { onSelectServer(serverCache.guid) },
            onEdit = { onEditServer(serverCache.guid, profile) },
            onShare = { onShareClick(serverCache.guid, profile) },
            onDelete = { onRemoveServer(serverCache.guid) },
        )
    }
}

private fun simpleProtocolLabel(profile: ProfileItem): String {
    val type = profile.configType.name
    val net = profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }?.let { " / $it" } ?: ""
    return type + net
}
