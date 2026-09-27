package com.narcic.ng.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.AppConfig
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.enums.EConfigType
import kotlinx.coroutines.flow.StateFlow

/**
 * Horizontal pill tabs: "پیش‌فرض" (manually-added configs) + one per
 * subscription. Auto-scrolls to keep the selected tab in view. Each chip's
 * count is live and scoped to whichever engine (AmneziaWG / V2Ray) is
 * currently active, mirroring what MainServerListSection will actually show
 * if that tab is picked.
 */
@Composable
fun GroupTabs(
    groups: List<GroupMapItem>,
    selectedGroupId: String?,
    accent: Color,
    engineIsAwg: Boolean,
    aetherOnly: Boolean = false,
    serverFlowFor: (String) -> StateFlow<List<ServersCache>>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedGroupId, groups.size) {
        val idx = groups.indexOfFirst { it.id == selectedGroupId }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = modifier,
    ) {
        items(groups, key = { it.id }) { g ->
            GroupTabChip(
                group = g,
                selected = g.id == selectedGroupId,
                accent = accent,
                engineIsAwg = engineIsAwg,
                aetherOnly = aetherOnly,
                serverFlow = remember(g.id) { serverFlowFor(g.id) },
                enabled = enabled,
                onClick = { onSelect(g.id) },
            )
        }
    }
}

@Composable
private fun GroupTabChip(
    group: GroupMapItem,
    selected: Boolean,
    accent: Color,
    engineIsAwg: Boolean,
    aetherOnly: Boolean = false,
    serverFlow: StateFlow<List<ServersCache>>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val servers by serverFlow.collectAsStateWithLifecycle()
    val count = remember(servers, engineIsAwg, aetherOnly) {
        servers.count { s ->
            when {
                aetherOnly -> s.profile.configType == EConfigType.AETHER
                else -> (s.profile.configType == EConfigType.WIREGUARD || s.profile.configType == EConfigType.AMNEZIAWG) == engineIsAwg
            }
        }
    }
    val isManual = group.id == AppConfig.DEFAULT_SUBSCRIPTION_ID || group.id.isEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = .10f) else Color.White.copy(alpha = .04f))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = .45f) else Color.White.copy(alpha = .08f),
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (isManual) Icons.Rounded.Shield else Icons.Rounded.Subscriptions,
            contentDescription = null,
            tint = if (selected) accent else Nc.Sub,
            modifier = Modifier.size(13.dp)
        )
        Text(
            group.remarks, color = if (selected) accent else Nc.Sub,
            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            count.toString(), color = if (selected) accent else Nc.Sub,
            fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .background(
                    if (selected) accent.copy(alpha = .18f) else Color.White.copy(alpha = .12f),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 7.dp, vertical = 1.dp)
        )
    }
}
