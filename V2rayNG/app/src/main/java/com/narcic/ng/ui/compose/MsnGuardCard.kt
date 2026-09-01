package com.narcic.ng.ui.compose

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.psiphon.PsiphonRegions

@Composable
fun MsnGuardCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    selectedRegion: String,
    onSelectRegion: (String) -> Unit,
    isLocked: Boolean = false,
    accent: Color = Nc.Emerald,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (enabled) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                        if (enabled) accent.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                1.dp,
                if (enabled) accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent, accent.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "حالت محافظ MSN-Guard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Nc.Txt
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ضد فیلتر",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                    Text(
                        text = "عبور خودکار از سانسور شدید و زنجیره سایفون",
                        style = MaterialTheme.typography.bodySmall,
                        color = Nc.Sub,
                        fontSize = 11.5.sp
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = { if (!isLocked) onToggle(it) },
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Nc.Sub,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }

        // Region picker appears when enabled
        AnimatedVisibility(visible = enabled) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Public,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "کشور خروجی (اختیاری):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Nc.Txt,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Auto item
                    item {
                        val isAuto = selectedRegion.isBlank()
                        RegionPill(
                            label = "اتوماتیک (سریع‌ترین)",
                            flag = "⚡",
                            selected = isAuto,
                            accent = accent,
                            onClick = { onSelectRegion("") }
                        )
                    }

                    // Country options
                    items(PsiphonRegions.options()) { (code, name) ->
                        val isSelected = selectedRegion.equals(code, ignoreCase = true)
                        val flag = PsiphonRegions.flagFor(code)
                        RegionPill(
                            label = name,
                            flag = flag,
                            selected = isSelected,
                            accent = accent,
                            onClick = { onSelectRegion(code) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionPill(
    label: String,
    flag: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = flag, fontSize = 14.sp)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Nc.Txt
            )
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
