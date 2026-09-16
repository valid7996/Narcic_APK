package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import com.narcic.ng.R
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.verticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDrawerContent(
    onNavigate: (String) -> Unit,
    onAction: (MainAction) -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
) {
    val drawerScrollState = rememberScrollState()
    var showImportMenu by remember { mutableStateOf(false) }
    var showManageMenu by remember { mutableStateOf(false) }
    val importMenuScrollState = rememberScrollState()
    val manageMenuScrollState = rememberScrollState()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - navBarHeight - 20.dp

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .navigationBarsPadding(),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(drawerScrollState)
                .verticalScrollbar(drawerScrollState)
                .padding(bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AuroraDeep, Color(0xFF0E2338), AuroraIndigo.copy(alpha = 0.35f)),
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily(Font(R.font.montserrat_thin)),
                            fontWeight = FontWeight.Thin,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(36.dp)
                            .background(Brush.linearGradient(listOf(AuroraCyan, AuroraIndigo)))
                    )
                }
            }

            // Import / manage configs — moved here from the top bar to keep the
            // connection screen uncluttered.
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                DrawerMenuItem(
                    icon = painterResource(R.drawable.ic_add_24dp),
                    label = "Import config",
                    onClick = { showImportMenu = true }
                )
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false },
                    scrollState = importMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(importMenuScrollState)
                ) {
                    ImportMenuContent(
                        onAction = { action ->
                            showImportMenu = false
                            onAction(action)
                        }
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                DrawerMenuItem(
                    icon = painterResource(R.drawable.ic_more_vert_24dp),
                    label = "Manage configs",
                    onClick = { showManageMenu = true }
                )
                DropdownMenu(
                    expanded = showManageMenu,
                    onDismissRequest = { showManageMenu = false },
                    scrollState = manageMenuScrollState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScrollbar(manageMenuScrollState)
                ) {
                    MoreMenuContent(
                        onAction = { action ->
                            showManageMenu = false
                            onAction(action)
                        },
                        onDelAllConfig = { showManageMenu = false; onDelAllConfig() },
                        onDelDuplicateConfig = { showManageMenu = false; onDelDuplicateConfig() },
                        onDelInvalidConfig = { showManageMenu = false; onDelInvalidConfig() }
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Contents of the "Import config" dropdown: quick imports (QR / clipboard /
 * local file) plus manual entry for every supported protocol.
 */
@Composable
fun ImportMenuContent(
    onAction: (MainAction) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
        onClick = { onAction(MainAction.ImportQRcode) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
        onClick = { onAction(MainAction.ImportClipboard) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_local)) },
        onClick = { onAction(MainAction.ImportConfigLocal) }
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_policy_group)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.POLICYGROUP.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_proxy_chain)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.PROXYCHAIN.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vmess)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.VMESS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vless)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.VLESS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_ss)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_socks)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.SOCKS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_http)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.HTTP.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_trojan)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.TROJAN.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_wireguard)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.WIREGUARD.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_wow)) },
        onClick = { onAction(MainAction.ImportWarpOnWarp) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_hysteria2)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.HYSTERIA2.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_aether)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.AETHER.value)) }
    )
}

/**
 * Contents of the "Manage configs" dropdown: bulk test/sort/export actions
 * plus the three destructive delete actions, which are surfaced through the
 * dedicated callbacks so the caller can show a confirmation dialog first.
 */
@Composable
fun MoreMenuContent(
    onAction: (MainAction) -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_ping_all_server)) },
        onClick = { onAction(MainAction.TestAllServers) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_real_ping_all_server)) },
        onClick = { onAction(MainAction.TestRealAllServers) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sort_by_test_results)) },
        onClick = { onAction(MainAction.SortByTestResults) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sub_update)) },
        onClick = { onAction(MainAction.UpdateSubscriptions) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_export_all)) },
        onClick = { onAction(MainAction.ExportAll) }
    )
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_duplicate_config)) },
        onClick = onDelDuplicateConfig
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_invalid_config)) },
        onClick = onDelInvalidConfig
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_all_config)) },
        onClick = onDelAllConfig
    )
}
