package com.narcic.ng.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.R
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.AuroraViolet

/**
 * Live connection info shown under the hero once the tunnel is up: real
 * download/upload throughput (reusing the same speed-notification data
 * source gated by "Enable speed display" in Settings), how long the current
 * session has been connected, and the exit IP/country. Hidden while
 * disconnected since none of these values mean anything then.
 */
@Composable
fun ConnectionStatsPanel(
    isRunning: Boolean,
    downloadSpeedText: String,
    uploadSpeedText: String,
    connectionDurationText: String,
    remoteIp: String,
    remoteCountryName: String,
    remoteCountryCode: String,
) {
    AnimatedVisibility(
        visible = isRunning,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_arrow_downward_24dp,
                    accent = AuroraCyan,
                    label = "دانلود",
                    value = downloadSpeedText.ifBlank { "—" },
                )
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_arrow_upward_24dp,
                    accent = AuroraIndigo,
                    label = "آپلود",
                    value = uploadSpeedText.ifBlank { "—" },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_timer_24dp,
                    accent = AuroraViolet,
                    label = "زمان اتصال",
                    value = connectionDurationText.ifBlank { "—" },
                )
                StatChip(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_public_24dp,
                    accent = AuroraCyan,
                    label = countryLabel(remoteCountryName, remoteCountryCode),
                    value = remoteIp.ifBlank { "در حال یافتن..." },
                )
            }
        }
    }
}

private fun countryLabel(countryName: String, countryCode: String): String = when {
    countryName.isNotBlank() && countryCode.isNotBlank() -> "$countryName ($countryCode)"
    countryCode.isNotBlank() -> countryCode
    else -> "IP و کشور"
}

@Composable
private fun StatChip(
    modifier: Modifier = Modifier,
    icon: Int,
    accent: Color,
    label: String,
    value: String,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
