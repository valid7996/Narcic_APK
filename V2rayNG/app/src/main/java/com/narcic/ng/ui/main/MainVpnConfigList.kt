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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.AuroraViolet
import com.narcic.ng.ui.compose.colorConfigType
import com.narcic.ng.ui.compose.colorPing

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
    onSelectGroup: (String) -> Unit,
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
    val sorted = remember(groupServers) {
        groupServers.sortedWith(
            compareBy(
                { it.testDelayMillis <= 0L },
                { if (it.testDelayMillis > 0L) it.testDelayMillis else Long.MAX_VALUE },
            )
        )
    }
    val top5 = remember(sorted) { sorted.filter { it.testDelayMillis > 0L }.take(5) }
    val isDefaultGroup = selectedGroupId == AppConfig.DEFAULT_SUBSCRIPTION_ID || selectedGroupId.isEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        if (groups.size > 1) {
            GroupTabBar(
                groups = groups,
                selectedTabIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0),
                mainViewModel = mainViewModel,
                onTabClick = { index -> onSelectGroup(groups[index].id) },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                    .clip(RoundedCornerShape(16.dp)),
            )
        }

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
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation = if (isTesting) 0.dp else 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (isTesting) "در حال تست…" else "تست")
            }
        }

        // Top 5 best servers of THIS group only -- clearly identifiable as recommended.
        if (top5.isNotEmpty() && !isTesting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .shadow(3.dp, CircleShape, spotColor = AuroraIndigo.copy(alpha = 0.5f))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AuroraCyan, AuroraIndigo))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "\u2b50", fontSize = 13.sp)
                    }
                    Text(
                        text = "بهترین سرورها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${top5.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
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
                    text = if (isDefaultGroup) {
                        "هنوز کانفیگی اضافه نشده\nاز «افزودن از کلیپ‌بورد» یا «اسکن QR» استفاده کنید"
                    } else {
                        "این سابسکریپشن هنوز سروری ندارد\nبرای دریافت، از دکمه بروزرسانی بالای صفحه استفاده کنید"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("افزودن از کلیپ‌بورد", fontWeight = FontWeight.Bold)
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
    EConfigType.VMESS -> ProtocolBadge("VM", AuroraIndigo)
    EConfigType.VLESS -> ProtocolBadge("VL", AuroraCyan)
    EConfigType.SHADOWSOCKS -> ProtocolBadge("SS", colorPing)
    EConfigType.SOCKS -> ProtocolBadge("SK", AuroraCyan)
    EConfigType.TROJAN -> ProtocolBadge("TR", colorConfigType)
    EConfigType.WIREGUARD -> ProtocolBadge("WG", AuroraViolet)
    EConfigType.AMNEZIAWG -> ProtocolBadge("AW", AuroraViolet)
    EConfigType.HYSTERIA2, EConfigType.HYSTERIA -> ProtocolBadge("HY", colorConfigType)
    EConfigType.HTTP -> ProtocolBadge("HT", AuroraIndigo)
    EConfigType.CUSTOM -> ProtocolBadge("CF", AuroraViolet)
    EConfigType.POLICYGROUP -> ProtocolBadge("PG", AuroraIndigo)
    EConfigType.PROXYCHAIN -> ProtocolBadge("PC", AuroraCyan)
}

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
private fun RoundIconButton(
    icon: Int,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    size: Dp = 32.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(2.dp, CircleShape, spotColor = tint.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun VpnConfigRow(
    serverCache: ServersCache,
    selectedGuid: String?,
    isBest: Boolean,
    rank: Int?,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareClick: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val profile = serverCache.profile
    val isSelected = serverCache.guid == selectedGuid
    // Curated Narcic subscriptions (Irancell / NG-JSON / NG-WireGuard) are
    // read-only content: no Edit, no Share, and (per SubscriptionsScreen.kt)
    // their source link is never displayed either. Delete still works so
    // a customer can drop a single bad server locally.
    val isDefault = remember(profile.subscriptionId) { isDefaultConfig(profile) }
    val badge = remember(profile.configType) { protocolBadge(profile.configType) }

    val backgroundBrush = when {
        isSelected -> Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            )
        )
        isBest -> Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            )
        )
        else -> Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .shadow(
                elevation = if (isSelected) 6.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.32f else 0.1f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, Brush.linearGradient(listOf(AuroraCyan, AuroraIndigo)), RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                }
            )
            .clickable { onSelectServer(serverCache.guid) }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .shadow(3.dp, CircleShape, spotColor = AuroraIndigo.copy(alpha = 0.5f))
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AuroraCyan, AuroraIndigo))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .shadow(2.dp, CircleShape, spotColor = badge.color.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .background(badge.color.copy(alpha = 0.16f))
                        .border(1.dp, badge.color.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = badge.color
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.remarks.ifBlank { "بدون‌نام" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = simpleProtocolLabel(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (serverCache.testDelayString.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(8.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = serverCache.testDelayString,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.size(4.dp))
            }
            // Curated Narcic subscriptions never expose Share/Edit for their
            // servers -- see isDefaultConfig() above.
            if (!isDefault) {
                RoundIconButton(
                    icon = R.drawable.ic_share_24dp,
                    contentDescription = "Share",
                    tint = AuroraIndigo,
                    onClick = { onShareClick(serverCache.guid, profile) }
                )
                Spacer(Modifier.size(6.dp))
                RoundIconButton(
                    icon = R.drawable.ic_edit_24dp,
                    contentDescription = "Edit",
                    tint = AuroraCyan,
                    onClick = { onEditServer(serverCache.guid, profile) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSelected) "انتخاب‌شده • برای اتصال بزنید" else "برای انتخاب بزنید",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            RoundIconButton(
                icon = R.drawable.ic_delete_24dp,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = { onRemoveServer(serverCache.guid) },
                size = 28.dp
            )
        }
    }
}

private fun simpleProtocolLabel(profile: ProfileItem): String {
    val type = profile.configType.name
    val net = profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }?.let { " / $it" } ?: ""
    return type + net
}
