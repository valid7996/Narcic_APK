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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.narcic.ng.R
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache

/**
 * Clean main VPN configuration list.
 * - No location, no connection card, no subscription metadata inside rows.
 * - Rows represent actual VPN servers/configs only.
 * - Each manual config has directly reachable Edit and Share.
 * - Top 5 best servers displayed vertically at top when testing completes.
 */
@Composable
fun MainVpnConfigSection(
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    isTesting: Boolean,
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
    val manualServers by mainViewModel.manualServersFlow().collectAsStateWithLifecycle()

    // Top 5: best tested manual servers, max 5, vertical, same visual language, sorted.
    val top5 = manualServers
        .filter { it.testDelayMillis > 0L }
        .sortedBy { it.testDelayMillis }
        .take(5)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "VPN Configurations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Top 5 best servers - clearly identifiable as recommended
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
                        text = "Best Servers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Top ${top5.size}",
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRetest,
                enabled = !isTesting,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isTesting) "Testing..." else "Test")
            }
        }

        if (manualServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No manual configurations yet\nUse Add from Clipboard or Scan QR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        } else {
            Text(
                text = "My Configurations (${manualServers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                manualServers.forEach { server ->
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

        // Action buttons: Add Server from Clipboard + Scan QR
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
                Text("Add Server from Clipboard", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onScanVpnQr,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(painterResource(R.drawable.ic_scan_24dp), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Scan VPN QR")
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
    // Simplified card: no location, no subscription info, only VPN config focus.
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
                    text = profile.remarks.ifBlank { "Unnamed" },
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
            // Ping badge
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
                Icon(painterResource(R.drawable.ic_share_24dp), contentDescription = "Share", Modifier.size(18.dp))
            }
            IconButton(onClick = { onEditServer(serverCache.guid, profile) }, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(R.drawable.ic_edit_24dp), contentDescription = "Edit", Modifier.size(18.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSelected) "Selected • Tap to connect" else "Tap to select",
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
