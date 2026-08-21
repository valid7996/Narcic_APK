package com.narcic.ng.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.ui.compose.AppTopBar
import com.narcic.ng.util.CountryFlags

private data class CountryOption(val flag: String, val name: String, val count: Int)

@Composable
fun LocationPickerScreen(
    servers: List<ServersCache>,
    selectedFlag: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val countries = remember(servers) {
        servers.mapNotNull { CountryFlags.extractFlag(it.profile.remarks) }
            .groupingBy { it }
            .eachCount()
            .map { (flag, count) -> CountryOption(flag, CountryFlags.displayNameFa(flag), count) }
            .sortedByDescending { it.count }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "انتخاب موقعیت", onBackClick = onBack)
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                LocationRow(
                    label = "خودکار",
                    subtitle = "بهترین کانفیگ در دسترس، بدون محدودیت به یک کشور",
                    count = null,
                    selected = selectedFlag.isEmpty(),
                    onClick = { onSelect("") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(countries, key = { it.flag }) { country ->
                LocationRow(
                    label = "${country.flag}  ${country.name}",
                    subtitle = null,
                    count = country.count,
                    selected = country.flag == selectedFlag,
                    onClick = { onSelect(country.flag) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LocationRow(
    label: String,
    subtitle: String?,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            SelectedDot()
        } else {
            Text(text = "", modifier = Modifier)
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (count != null) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
