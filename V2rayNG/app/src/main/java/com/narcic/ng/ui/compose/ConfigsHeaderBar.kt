package com.narcic.ng.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.R

@Composable
fun ConfigsHeaderBar(
    title: String,
    count: Int,
    accent: Color,
    onAddClick: () -> Unit,
    onSortClick: () -> Unit,
    onTestClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Actions (Delete All, Add, Sort/Best, Ping Test)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Delete All (سطل زباله برای حذف همه)
            HeaderActionButton(
                icon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "حذف همه",
                        tint = Nc.Red,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = onDeleteAllClick
            )

            // 2. Add (+ برای افزودن از طریق کلیپ‌بورد یا اسکن QR)
            HeaderActionButton(
                icon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "افزودن",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onAddClick
            )

            // 3. Sort / Auto-connect Best Server (تنظیم به بهترین سرور)
            HeaderActionButton(
                icon = {
                    Icon(
                        Icons.Rounded.SwapVert,
                        contentDescription = "بهترین سرور",
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                },
                onClick = onSortClick
            )

            // 4. Pulse / Heart / Ping Test (تست پینگ سرورها)
            HeaderActionButton(
                icon = {
                    Icon(
                        painterResource(R.drawable.narcis_3d_stats),
                        contentDescription = "تست",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = onTestClick
            )
        }

        // Right side: Badge with count + Title ("کلاینت‌های AmneziaWG" یا "کلاینت‌های V2Ray")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = faDigits(count.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    fontSize = 12.sp
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Nc.Txt,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x1894A3B8))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
