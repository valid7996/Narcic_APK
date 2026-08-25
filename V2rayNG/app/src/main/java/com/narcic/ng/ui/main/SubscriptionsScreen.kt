package com.narcic.ng.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.extension.toast
import com.narcic.ng.ui.ScannerActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.util.Utils

@Composable
fun SubscriptionsScreen(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    isAdding: Boolean,
    onSelectGroup: (String) -> Unit,
    onRefresh: (String) -> Unit,
    onTest: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit,
    onBack: () -> Unit,
    onScanSubscriptionQr: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val subscriptionQrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = result.data?.getStringExtra("SCAN_RESULT")
            if (!scanResult.isNullOrBlank()) {
                // Strict subscription-only: reject ordinary VPN configs
                val isVpn = scanResult.trim().let { txt ->
                    listOf("vmess://", "vless://", "ss://", "trojan://", "wireguard://", "socks://", "hysteria2://", "hy2://", "tuic://", "hysteria://", "v2rayn://")
                        .any { txt.startsWith(it, ignoreCase = true) }
                }
                if (isVpn || !Utils.isValidSubUrl(scanResult.trim())) {
                    context.toast(R.string.toast_failure)
                } else {
                    onScanSubscriptionQr(scanResult)
                }
            } else {
                context.toast(R.string.toast_decoding_failed)
            }
        }
    }
    Scaffold(
        topBar = { AppTopBar(title = "سابسکریپشن", onBackClick = onBack, isLoading = isAdding) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── هدر + دکمه افزودن ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "منبع را انتخاب کنید، اتصال‌هایش را تست کنید یا گزینه‌ها را باز کنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddClick,
                        enabled = !isAdding,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isAdding) "در حال افزودن…" else "+ افزودن")
                    }
                    OutlinedButton(
                        onClick = { subscriptionQrLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                        enabled = !isAdding,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Subscription QR")
                    }
                }
            }

            if (groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "هنوز سابسکریپشنی اضافه نشده",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── مخازن اضافه‌شده توسط کاربر ──────────────────────────
                items(groups, key = { it.id }) { group ->
                    SubscriptionCard(
                        mainViewModel = mainViewModel,
                        group = group,
                        selected = group.id == selectedGroupId,
                        onSelect = { onSelectGroup(group.id) },
                        onRefresh = { onRefresh(group.id) },
                        onTest = { onTest(group.id) },
                        onDelete = { onDelete(group.id) },
                    )
                }

                // ── مخازن پیش‌فرض Narcic (جمع‌وجور، قابل باز/بسته شدن) ─
                item(key = "__presets__") {
                    DefaultSubscriptionsSection(
                        onAddPreset = { name, url ->
                            /* AddSubscriptionDialog already handles adding;
                               اینجا فقط برای نمایش اطلاعاته — دکمه «+ افزودن»
                               همان onAddClick صفحه رو فراخوانی می‌کند */
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// بخش مخازن پیش‌فرض — یک کارت جمع‌وجور با آیتم‌های قابل باز/بسته شدن
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DefaultSubscriptionsSection(
    onAddPreset: (name: String, url: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        // ── سربرگ قابل کلیک ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "مخازن موجود Narcic",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${AppConfig.DEFAULT_SUBSCRIPTIONS.size} مخزن پیش‌فرض",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "بستن" else "باز کردن",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── لیست آیتم‌ها (فقط وقتی expanded = true) ────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                AppConfig.DEFAULT_SUBSCRIPTIONS.forEachIndexed { index, (remarks, _) ->
                    if (index > 0) {
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = remarks,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// کارت هر سابسکریپشن اضافه‌شده — فقط اطلاعات سابسکریپشن، بدون لود کانفیگ‌های عادی
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SubscriptionCard(
    mainViewModel: MainViewModel,
    group: GroupMapItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Load subscription metadata only, not VPN configs - per separation requirement
    val subscription = remember(group.id) { runCatching { mainViewModel.getSubscriptionItem(group.id) }.getOrNull() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onSelect)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "انتخاب‌شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Box(modifier = Modifier)
            }
            Text(
                text = group.remarks,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = subscription?.url?.takeIf { it.isNotBlank() }?.let { url ->
                if (url.length > 40) url.take(40) + "…" else url
            } ?: "Local subscription",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (subscription?.lastUpdated != null && subscription.lastUpdated > 0) {
            Text(
                text = "Updated: ${Utils.formatTimestamp(subscription.lastUpdated)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("گزینه‌ها  ⋮")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("تازه‌سازی") },
                        onClick = { menuOpen = false; onRefresh() },
                    )
                    if (group.id.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "حذف سابسکریپشن",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            TextButton(
                onClick = onTest,
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "تست  ⏱",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
