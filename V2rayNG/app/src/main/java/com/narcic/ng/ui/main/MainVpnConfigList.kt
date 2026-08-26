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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache

/**
 * The server list that lives directly under the connect button.
 *
 * - Horizontal group tabs on top: "پیش‌فرض" (manually-entered servers, like
 *   V2rayNg) plus one tab per subscription the user added (e.g. "Narcic
 *   Irancell"). Switching tabs switches which group's servers are shown.
 * - ONE "Test" button, scoped to whichever tab/group is currently selected
 *   (never tests every group at once).
 * - After a test finishes, servers are always sorted best-ping-first, both
 *   in the "Best Servers" (top 5) box and in the full list beneath it.
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

    // Only the servers belonging to the currently selected tab/group —
    // this is what makes "Test" and the list itself scoped to one group.
    val groupServers by remember(selectedGroupId) { mainViewModel.serversForGroup(selectedGroupId) }
        .collectAsStateWithLifecycle()

    // Best ping first, always — both right after a test and on every
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
                modifier = Modifier.padding(bottom = 4.dp),
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
                modifier = Modifier.weight(1f),
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

        // Top 5 best servers of THIS group only — clearly identifiable as recommended.
        if (top5.isNotEmpty() && !isTesting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "⭐", fontSize = 14.sp)
                    Text(
                        text = "بهترین سرورها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${top5.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(8.dp))
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
        // exactly like V2rayNg — available no matter which tab is open.
        if (isDefaultGroup || groups.size <= 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddFromClipboard,
                    modifier = Modifier.fillMaxWidth(),
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
    val containerColor = when {
        isBest -> MaterialTheme.colorScheme.surface
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = when {
        isBest -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelectServer(serverCache.guid) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.size(8.dp))
            }
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
                Text(
                    text = serverCache.testDelayString,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(Modifier.size(4.dp))
            }
            IconButton(onClick = { onShareClick(serverCache.guid, profile) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_share_24dp),
                    contentDescription = "Share",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onEditServer(serverCache.guid, profile) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSelected) "انتخاب‌شده • برای اتصال بزنید" else "برای انتخاب بزنید",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = { onRemoveServer(serverCache.guid) }, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun simpleProtocolLabel(profile: ProfileItem): String {
    val type = profile.configType.name
    val net = profile.network?.takeIf { it.isNotBlank() && !it.equals("tcp", ignoreCase = true) }?.let { " / $it" } ?: ""
    return type + net
}
