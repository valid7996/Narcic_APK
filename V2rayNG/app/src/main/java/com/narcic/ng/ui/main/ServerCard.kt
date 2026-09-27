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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.SignalBars

/**
 * Purely presentational server row per the redesign spec: radio + name/
 * address + signal bars + protocol badge + edit/share/delete actions.
 * Business logic (which actions are shown, the share-method dialog, the
 * "curated subscription" read-only rule, ...) stays in the caller
 * (MainVpnConfigList.kt's VpnConfigRow) — this just draws the row.
 */
@Composable
fun ServerCard(
    name: String,
    address: String,
    pingMs: Int?,
    protoLabel: String,
    protoColor: Color,
    chips: List<String> = emptyList(),
    selected: Boolean,
    enabled: Boolean,
    accent: Color,
    showEditShare: Boolean,
    rankBadge: Int? = null,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected)
                    Brush.linearGradient(listOf(accent.copy(alpha = .11f), Color.White.copy(alpha = .04f)))
                else
                    Brush.linearGradient(listOf(Color(0x1794A3B8), Color(0x0894A3B8)))
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = .55f) else Nc.Stroke,
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            // Radio / rank badge.
            if (rankBadge != null) {
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .7f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$rankBadge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            } else {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(50))
                        .border(2.dp, if (selected) accent else Color(0x6694A3B8), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(accent))
                }
            }

            // Name + address.
            Column(Modifier.weight(1f)) {
                Text(
                    name, color = Nc.Txt, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    address, color = Nc.Sub, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            SignalBars(pingMs = pingMs)

            // Protocol badge.
            Text(
                protoLabel.uppercase(), color = protoColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(protoColor.copy(alpha = .1f), RoundedCornerShape(50))
                    .border(1.dp, protoColor.copy(alpha = .35f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 3.5.dp)
            )
        }

        // Engine-specific detail chips (Jc/MTU for AWG, transport/carriers
        // for Aether, Reality/Vision/desync for V2Ray) — mockup row 2.
        if (chips.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = 31.dp, top = 7.dp)
            ) {
                chips.take(3).forEach { chip ->
                    Text(
                        chip, color = Nc.Sub, fontSize = 8.5.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = .05f), RoundedCornerShape(50))
                            .border(1.dp, Nc.Stroke, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Action row: share/edit/delete kept on their own line so the top
        // row stays readable (name + ping + badge) like the mockup card.
        if (showEditShare) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
            ) {
                Icon(
                    Icons.Rounded.Share, "اشتراک‌گذاری", tint = Nc.Sub,
                    modifier = Modifier.size(15.dp).clickable(onClick = onShare)
                )
                Spacer(Modifier.padding(start = 13.dp))
                Icon(
                    Icons.Rounded.Edit, "ویرایش", tint = Nc.Sub,
                    modifier = Modifier.size(15.dp).clickable(onClick = onEdit)
                )
                Spacer(Modifier.padding(start = 13.dp))
                Icon(
                    Icons.Rounded.Delete, "حذف", tint = Nc.Red,
                    modifier = Modifier.size(15.dp).clickable(onClick = onDelete)
                )
            }
        }
    }
}
