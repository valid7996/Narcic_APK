package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.extension.displayLabel
import com.narcic.ng.util.CountryFlags

/**
 * The "location / connection / subscription" card that sits on the VPN home
 * tab. Every row just opens its own picker screen — no inline editing here.
 */
@Composable
fun HomeVpnCard(
    isRunning: Boolean,
    locationFlag: String,
    autoConnection: Boolean,
    connectedServer: ServersCache?,
    subscriptionName: String,
    onOpenLocation: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            )
    ) {
        HomeCardRow(
            label = "موقعیت",
            value = if (locationFlag.isEmpty()) {
                "خودکار"
            } else {
                "$locationFlag ${CountryFlags.displayNameFa(locationFlag)}"
            },
            onClick = onOpenLocation,
        )
        HomeCardDivider()
        HomeCardRow(
            label = "اتصال",
            value = connectionRowValue(isRunning, autoConnection, connectedServer),
            subtitle = if (isRunning && connectedServer != null) connectionRowSubtitle(connectedServer) else null,
            onClick = onOpenConnection,
        )
        HomeCardDivider()
        HomeCardRow(
            label = "سابسکریپشن",
            value = subscriptionName.ifBlank { "—" },
            onClick = onOpenSubscriptions,
        )
    }
}

private fun connectionRowValue(
    isRunning: Boolean,
    autoConnection: Boolean,
    connectedServer: ServersCache?,
): String {
    if (isRunning && connectedServer != null) return connectedServer.profile.remarks
    if (autoConnection) return "خودکار"
    return connectedServer?.profile?.remarks ?: "خودکار"
}

private fun connectionRowSubtitle(server: ServersCache): String {
    val protocol = server.profile.configType.displayLabel()
    val ech = if (server.profile.echConfigList.isNullOrBlank()) "ECH not applicable" else "ECH configured"
    return "$protocol outbound · $ech"
}

@Composable
private fun HomeCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun HomeCardRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small filled status dot used next to "خودکار" style selected rows. */
@Composable
fun SelectedDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}
