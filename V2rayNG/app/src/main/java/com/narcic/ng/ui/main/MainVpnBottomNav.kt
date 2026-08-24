package com.narcic.ng.ui.main

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.narcic.ng.R

enum class MainHomeTab { VPN, SUBSCRIPTIONS }

@Composable
fun MainVpnBottomNav(
    selectedTab: MainHomeTab,
    onSelectTab: (MainHomeTab) -> Unit,
    onSettingsClick: () -> Unit,
    onStatisticsClick: () -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        NavigationBarItem(
            selected = selectedTab == MainHomeTab.SUBSCRIPTIONS,
            onClick = { onSelectTab(MainHomeTab.SUBSCRIPTIONS) },
            icon = { Icon(painterResource(R.drawable.ic_subscriptions_24dp), contentDescription = null) },
            label = { Text("سابسکریپشن") },
            colors = navColors(),
        )
        NavigationBarItem(
            selected = selectedTab == MainHomeTab.VPN,
            onClick = { onSelectTab(MainHomeTab.VPN) },
            icon = { Icon(painterResource(R.drawable.ic_power_24dp), contentDescription = null) },
            label = { Text("وی‌پی‌ان") },
            colors = navColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onStatisticsClick,
            icon = { Icon(painterResource(R.drawable.ic_stats_24dp), contentDescription = null) },
            label = { Text("آمار") },
            colors = navColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettingsClick,
            icon = { Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null) },
            label = { Text("تنظیمات") },
            colors = navColors(),
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
