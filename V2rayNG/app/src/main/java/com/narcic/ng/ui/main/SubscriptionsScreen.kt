package com.narcic.ng.ui.main

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.extension.toast
import com.narcic.ng.ui.ScannerActivity
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.util.Utils

/**
 * Subscriptions screen — kept intentionally plain, like V2rayNg's own
 * subscription list: you enter/paste a subscription and it loads. There is
 * no per-item "Test" and no per-item options (⋮) menu here any more — a
 * subscription's servers are tested from its group tab on the main screen
 * instead. The built-in Narcic "repos" are never listed here in plain view;
 * they only ever appear one step behind the "+ افزودن" button, inside the
 * "مخزن موجود" picker (see AddSubscriptionDialog), so nothing shows up
 * until the user actually presses that button.
 */
@Composable
fun SubscriptionsScreen(
    mainViewModel: MainViewModel,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    isAdding: Boolean,
    onSelectGroup: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (groupId: String, name: String, url: String) -> Unit = { _, _, _ -> },
    onAddClick: () -> Unit,
    onBack: () -> Unit,
    onScanSubscriptionQr: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var editingGroup by remember { mutableStateOf<GroupMapItem?>(null) }
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
                    text = "لینک سابسکریپشن را وارد کنید تا لود شود.",
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
                items(groups, key = { it.id }) { group ->
                    SubscriptionCard(
                        mainViewModel = mainViewModel,
                        group = group,
                        selected = group.id == selectedGroupId,
                        onSelect = { onSelectGroup(group.id) },
                        onDelete = { onDelete(group.id) },
                        onEditClick = { editingGroup = group },
                    )
                }
            }
        }
    }

    val groupBeingEdited = editingGroup
    if (groupBeingEdited != null) {
        val currentSub = remember(groupBeingEdited.id) {
            runCatching { mainViewModel.getSubscriptionItem(groupBeingEdited.id) }.getOrNull()
        }
        EditSubscriptionDialog(
            initialName = groupBeingEdited.remarks,
            initialUrl = currentSub?.url.orEmpty(),
            onSave = { name, url ->
                onEdit(groupBeingEdited.id, name, url)
                editingGroup = null
            },
            onDismiss = { editingGroup = null },
        )
    }
}

/**
 * "ویرایش سابسکریپشن": lets the user rename a subscription and/or change
 * its URL without deleting and re-adding it. Every other field (enabled,
 * autoUpdate, filter, ...) is preserved untouched by the caller.
 */
@Composable
private fun EditSubscriptionDialog(
    initialName: String,
    initialUrl: String,
    onSave: (name: String, url: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش سابسکریپشن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("آدرس (URL)") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), url.trim()) },
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// کارت هر سابسکریپشن — فقط نام و آدرس، شبیه لیست سابسکریپشن V2rayNg
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SubscriptionCard(
    mainViewModel: MainViewModel,
    group: GroupMapItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit,
) {
    val subscription = remember(group.id) { runCatching { mainViewModel.getSubscriptionItem(group.id) }.getOrNull() }
    // All subscriptions -- including the curated Narcic ones -- are now
    // editable and their URL is shown, same as a custom subscription.

    Row(
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.remarks,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "انتخاب‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Text(
                text = subscription?.url?.takeIf { it.isNotBlank() }?.let { url ->
                    if (url.length > 40) url.take(40) + "…" else url
                } ?: "سابسکریپشن محلی",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (subscription?.lastUpdated != null && subscription.lastUpdated > 0) {
                Text(
                    text = "بروزرسانی: ${Utils.formatTimestamp(subscription.lastUpdated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (group.id.isNotEmpty()) {
            // Every subscription -- including the curated Narcic ones -- can
            // now be renamed / re-pointed and deleted like any custom sub.
            IconButton(onClick = onEditClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = "ویرایش",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
