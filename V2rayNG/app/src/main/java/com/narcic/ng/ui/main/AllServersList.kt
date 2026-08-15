package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.ui.compose.colorPing
import com.narcic.ng.ui.compose.colorPingRed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Clean, read-only, tap-to-select list of every received server across all
 * groups/subscriptions — replaces the old tabbed + swipe-to-reveal list.
 * No reordering, no per-item delete/share icon: management actions live in
 * the drawer now, this is just "pick a server".
 */
@Composable
fun AllServersList(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    selectedGuid: String?,
    onSelectServer: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val groupIds = remember(groups) { groups.map { it.id } }
    val allServersFlow = remember(groupIds) {
        if (groupIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(groupIds.map { mainViewModel.serversForGroup(it) }) { lists ->
                lists.toList().flatten()
            }
        }
    }
    val allServers by allServersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val sorted = remember(allServers) {
        allServers.sortedWith(
            compareBy(
                { it.testDelayMillis <= 0L },
                { if (it.testDelayMillis > 0L) it.testDelayMillis else Long.MAX_VALUE },
            )
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(sorted, key = { it.guid }) { server ->
            NiceServerRow(
                server = server,
                isSelected = server.guid == selectedGuid,
                onClick = { onSelectServer(server.guid) },
            )
        }
    }
}

@Composable
private fun NiceServerRow(
    server: ServersCache,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val pingColor = when {
        server.testDelayMillis <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        server.testDelayMillis in 1..2000 -> colorPing
        else -> colorPingRed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.profile.remarks,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = protocolLabel(server.profile),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = server.testDelayString.ifBlank { "--" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = pingColor,
        )
    }
}

private fun protocolLabel(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) return profile.configType.name
    val parts = mutableListOf(profile.configType.name)
    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) parts.add(net)
    }
    profile.security?.let { sec ->
        if (sec.isNotBlank()) parts.add(sec)
    }
    return parts.joinToString(" / ")
}
