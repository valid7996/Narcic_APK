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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// ───────────────── 3-engine tab bar (امنزیا / وی‌تو‌ری / اتر) ─────────────────

/**
 * Bottom tab bar of the 3-page redesign: one tab per engine page. Each tab
 * tints with its own engine accent (cyan/violet/orange) — replacing the old
 * 4-item nav whose سابسکریپشن/آمار/تنظیمات entries moved to the top bar
 * (gear + ⋯ sheet). Pure navigation; selection only drives list filtering,
 * never the ViewModel's real selected server.
 */
private data class EngineTabSpec(
    val id: String,
    val label: String,
    val accent: Color,
    val icon: @Composable (Color) -> Unit,
)

@Composable
fun MainEngineTabBar(
    selectedTab: String,
    onSelect: (String) -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val tabs = listOf(
        EngineTabSpec("awg", "امنزیا", Nc.AwgAccent) { tint ->
            Icon(Icons.Rounded.Shield, null, tint = tint, modifier = Modifier.size(19.dp))
        },
        EngineTabSpec("v2", "وی‌تو‌ری", Nc.V2Accent) { tint ->
            Icon(Icons.Rounded.Bolt, null, tint = tint, modifier = Modifier.size(19.dp))
        },
        EngineTabSpec("ae", "اتر", Nc.BadgeAether) { tint ->
            Icon(painterResource(R.drawable.ic_public_24dp), null, tint = tint, modifier = Modifier.size(19.dp))
        },
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .height(62.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isDark) Nc.Bg.copy(alpha = .85f) else Color.White.copy(alpha = .94f))
            .border(1.dp, if (isDark) Color.White.copy(alpha = .10f) else Color(0x141B2230), RoundedCornerShape(24.dp))
    ) {
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(onClick = { onSelect(tab.id) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    tab.icon(if (selected) tab.accent else Nc.Sub)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        color = if (selected) tab.accent else Nc.Sub,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(if (selected) 26.dp else 0.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) Brush.horizontalGradient(listOf(tab.accent, tab.accent.copy(alpha = .45f)))
                                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            )
                    )
                }
            }
        }
    }
}
