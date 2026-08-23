package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.extension.displayLabel
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.ui.compose.DeleteConfirmDialog
import com.narcic.ng.ui.compose.SelectListDialog
import com.narcic.ng.ui.compose.colorPing
import com.narcic.ng.ui.compose.colorPingRed
import com.narcic.ng.util.CountryFlags

private const val FILTER_ALL = "" // empty selection == "همه ..."

@Composable
fun ConnectionPickerScreen(
    servers: List<ServersCache>,
    groups: List<GroupMapItem>,
    selectedGroupId: String,
    isTesting: Boolean,
    autoConnection: Boolean,
    selectedGuid: String?,
    onSelectAuto: () -> Unit,
    onSelectServer: (String) -> Unit,
    onRetest: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSortByTest: () -> Unit,
    onRemoveInvalid: () -> Unit,
    onBack: () -> Unit,
) {
    var typeFilter by remember { mutableStateOf(FILTER_ALL) }
    var countryFilter by remember { mutableStateOf(FILTER_ALL) }
    var openDialog by remember { mutableStateOf<FilterKind?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRemoveInvalidConfirm by remember { mutableStateOf(false) }

    val types = remember(servers) {
        servers.map { it.profile.configType.displayLabel() }.distinct().sorted()
    }
    val countries = remember(servers) {
        servers.mapNotNull { CountryFlags.extractFlag(it.profile.remarks) }.distinct()
    }

    val filtered = remember(servers, typeFilter, countryFilter) {
        servers.filter { s ->
            (typeFilter == FILTER_ALL || s.profile.configType.displayLabel() == typeFilter) &&
                (countryFilter == FILTER_ALL || CountryFlags.extractFlag(s.profile.remarks) == countryFilter)
        }.sortedWith(
            compareBy(
                { it.testDelayMillis <= 0L },
                { if (it.testDelayMillis > 0L) it.testDelayMillis else Long.MAX_VALUE },
            )
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "آزمایش اتصال‌ها",
                onBackClick = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert_24dp),
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_sort_by_test_results)) },
                                onClick = {
                                    showMoreMenu = false
                                    onSortByTest()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.title_del_invalid_config),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    showRemoveInvalidConfirm = true
                                },
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "مهلت ۱۵ ثانیه · ۱۰ همزمان · تست سرعت ۱ مگابایت",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    label = typeFilter.ifEmpty { "همه نوع‌ها" },
                    modifier = Modifier.weight(1f),
                    onClick = { openDialog = FilterKind.TYPE },
                )
                FilterChip(
                    label = countryFilter.ifEmpty { "همه کشورها" },
                    modifier = Modifier.weight(1f),
                    onClick = { openDialog = FilterKind.COUNTRY },
                )
                FilterChip(
                    label = groups.firstOrNull { it.id == selectedGroupId }?.remarks ?: "سابسکریپشن",
                    modifier = Modifier.weight(1f),
                    onClick = { openDialog = FilterKind.SUBSCRIPTION },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            if (isTesting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "در حال آزمایش اتصال‌ها…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }

            Button(
                onClick = onRetest,
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (isTesting) "در حال تست…" else "تست دوباره", fontWeight = FontWeight.Bold)
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    AutoConnectionRow(selected = autoConnection, onClick = onSelectAuto)
                }
                items(filtered, key = { it.guid }) { server ->
                    ConnectionRow(
                        server = server,
                        selected = !autoConnection && server.guid == selectedGuid,
                        onClick = { onSelectServer(server.guid) },
                        onDelete = { onDelete(server.guid) },
                    )
                }
            }
        }
    }

    when (openDialog) {
        FilterKind.TYPE -> SelectListDialog(
            title = "نوع اتصال",
            options = listOf("همه نوع‌ها") + types,
            onSelected = { index, _ ->
                typeFilter = if (index == 0) FILTER_ALL else types[index - 1]
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        FilterKind.COUNTRY -> SelectListDialog(
            title = "کشور",
            options = listOf("همه کشورها") + countries.map { "$it  ${CountryFlags.displayNameFa(it)}" },
            onSelected = { index, _ ->
                countryFilter = if (index == 0) FILTER_ALL else countries[index - 1]
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        FilterKind.SUBSCRIPTION -> SelectListDialog(
            title = "سابسکریپشن (${groups.size})",
            options = groups.map { it.remarks },
            selectedOption = groups.firstOrNull { it.id == selectedGroupId }?.remarks ?: "",
            showRadio = true,
            onSelected = { index, _ ->
                onSelectGroup(groups[index].id)
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )

        null -> Unit
    }

    if (showRemoveInvalidConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_invalid_profiles),
            onConfirm = {
                showRemoveInvalidConfirm = false
                onRemoveInvalid()
            },
            onDismiss = { showRemoveInvalidConfirm = false },
        )
    }
}

private enum class FilterKind { TYPE, COUNTRY, SUBSCRIPTION }

@Composable
private fun FilterChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AutoConnectionRow(selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                SelectedDot()
                Box(modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = "خودکار",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "بهترین اتصال در دسترس از سابسکریپشن انتخاب‌شده",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConnectionRow(server: ServersCache, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val pingColor = when {
        server.testDelayMillis <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
        server.testDelayMillis in 1..2000 -> colorPing
        else -> colorPingRed
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = server.testDelayString.ifBlank { "--" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = pingColor,
            )
            Text(
                text = server.profile.remarks,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = server.profile.configType.displayLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
