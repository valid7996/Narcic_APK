package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.ui.compose.colorPing
import com.narcic.ng.ui.compose.colorPingRed

/**
 * Horizontal strip of the fastest-tested servers in the current group,
 * shown between the connect hero and the full server list so the user
 * can jump straight to a good server without scrolling.
 * Reuses MainViewModel.serversForGroup(groupId), which the full list
 * (GroupPagerPage) already relies on, so no new state wiring is needed.
 */
@Composable
fun SuggestedServers(
    mainViewModel: MainViewModel,
    groupId: String,
    selectedGuid: String?,
    onSelectServer: (String) -> Unit,
    onViewAll: () -> Unit,
) {
    val serverFlow = remember(groupId) { mainViewModel.serversForGroup(groupId) }
    val servers by serverFlow.collectAsStateWithLifecycle()

    val suggestions = remember(servers) {
        servers
            .filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)
    }

    if (suggestions.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Suggested servers",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onViewAll) {
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(suggestions, key = { it.guid }) { server ->
                SuggestedServerCard(
                    server = server,
                    isSelected = server.guid == selectedGuid,
                    onClick = { onSelectServer(server.guid) },
                )
            }
        }
    }
}

@Composable
private fun SuggestedServerCard(
    server: ServersCache,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val pingColor = if (server.testDelayMillis in 1..2000) colorPing else colorPingRed

    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = server.profile.remarks,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(modifier = Modifier.padding(top = 4.dp))
        Text(
            text = server.testDelayString,
            style = MaterialTheme.typography.bodySmall,
            color = pingColor,
        )
    }
}
