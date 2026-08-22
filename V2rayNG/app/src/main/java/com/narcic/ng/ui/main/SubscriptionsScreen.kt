package com.narcic.ng.ui.main

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.ui.compose.AppTopBar

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
) {
    Scaffold(
        topBar = { AppTopBar(title = "سابسکریپشن", onBackClick = onBack, isLoading = isAdding) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "منبع را انتخاب کنید، اتصال‌هایش را تست کنید یا گزینه‌ها را باز کنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onAddClick, enabled = !isAdding, shape = RoundedCornerShape(14.dp)) {
                    Text(if (isAdding) "در حال افزودن…" else "+ افزودن")
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
                        onRefresh = { onRefresh(group.id) },
                        onTest = { onTest(group.id) },
                        onDelete = { onDelete(group.id) },
                    )
                }
            }
        }
    }
}

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
    val servers by mainViewModel.serversForGroup(group.id).collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
            text = "داخلی · ${servers.size} اتصال",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
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
                        onClick = {
                            menuOpen = false
                            onRefresh()
                        },
                    )
                    // The "all configs" filter card (group.id == "") isn't a real
                    // subscription — there's nothing to delete, only to display.
                    if (group.id.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("حذف سابسکریپشن", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
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
                Text("تست  ⏱", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
