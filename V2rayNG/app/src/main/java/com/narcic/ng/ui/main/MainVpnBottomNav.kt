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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.R
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

enum class MainHomeTab { VPN, SUBSCRIPTIONS }

private data class NavItem(
    val icon: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Floating pill bottom bar per the redesign spec: 66dp tall, 24dp radius,
 * 18dp margin from the screen edges, a 3dp gradient indicator under the
 * active tab. Rendered inside Scaffold's `bottomBar` slot (rather than as a
 * true screen-overlay) so it still reserves its own space and content
 * scrolling underneath doesn't get obscured by it -- visually it still
 * reads as a floating glass card, it just doesn't overlap page content.
 */
@Composable
fun MainVpnBottomNav(
    selectedTab: MainHomeTab,
    onSelectTab: (MainHomeTab) -> Unit,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val accent = Nc.Cyan
    val items = listOf(
        NavItem(R.drawable.ic_subscriptions_24dp, "سابسکریپشن", selectedTab == MainHomeTab.SUBSCRIPTIONS) {
            onSelectTab(MainHomeTab.SUBSCRIPTIONS)
        },
        NavItem(R.drawable.ic_power_24dp, "وی‌پی‌ان", selectedTab == MainHomeTab.VPN) {
            onSelectTab(MainHomeTab.VPN)
        },
        NavItem(R.drawable.ic_stats_24dp, "آمار", false, onStatisticsClick),
        NavItem(R.drawable.ic_settings_24dp, "تنظیمات", false, onSettingsClick),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isDark) Nc.Bg.copy(alpha = .82f) else androidx.compose.ui.graphics.Color.White.copy(alpha = .92f))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .10f), RoundedCornerShape(24.dp))
    ) {
        Row(Modifier.fillMaxWidth().height(66.dp)) {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = item.label,
                        tint = if (item.selected) accent else Nc.Sub,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        color = if (item.selected) accent else Nc.Sub,
                        fontSize = 9.5.sp,
                        fontWeight = if (item.selected) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(if (item.selected) 24.dp else 0.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (item.selected) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = .5f)))
                                else Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent))
                            )
                    )
                }
            }
        }
    }
}
