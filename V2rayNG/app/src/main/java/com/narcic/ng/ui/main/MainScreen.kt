package com.narcic.ng.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.ui.compose.AnimatedCobwebBackground
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.LocalDarkTheme
import kotlinx.coroutines.launch

/**
 * Simplified connection screen:
 *  - Top bar: just the drawer menu + fetch-subscriptions action (search, manual
 *    import, and the config-management menu now live in the drawer).
 *  - ConnectHero: big connect circle + a "Test" button for a real/precise
 *    delay test of every config.
 *  - Suggested servers: the fastest-tested configs, tap to select.
 *  - AllServersList: a clean, flat, tap-to-select list of every received
 *    server — no tabs, no swipe-to-reveal delete/play icons.
 *  - The old bottom connect bar/FAB was removed since ConnectHero already
 *    covers connect/disconnect + status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val selectedGuid = uiState.selectedGuid
    val confirmRemove = uiState.confirmRemove

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    val removeServer: (String) -> Unit = { guid ->
        if (confirmRemove) showRemoveConfirm = guid else onAction(MainAction.RemoveServer(guid))
    }

    // AutoConnect (ViewModel) only selects the best server and bumps this
    // counter; the actual connect (VPN permission + start) is handled the
    // same way the hero button does it, via the Activity's ToggleService path.
    LaunchedEffect(uiState.autoConnectRequest) {
        if (uiState.autoConnectRequest > 0) {
            onAction(MainAction.ToggleService)
        }
    }

    MainDialogs(
        showDelAllConfirm = showDelAllConfirm,
        onDismissDelAll = { showDelAllConfirm = false },
        onConfirmDelAll = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
        showDelDuplicateConfirm = showDelDuplicateConfirm,
        onDismissDelDuplicate = { showDelDuplicateConfirm = false },
        onConfirmDelDuplicate = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
        showDelInvalidConfirm = showDelInvalidConfirm,
        onDismissDelInvalid = { showDelInvalidConfirm = false },
        onConfirmDelInvalid = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                },
                onAction = onAction,
                onDelAllConfig = { showDelAllConfirm = true },
                onDelDuplicateConfig = { showDelDuplicateConfirm = true },
                onDelInvalidConfig = { showDelInvalidConfirm = true },
            )
        }
    ) {
        val isDark = LocalDarkTheme.current
        val backdrop = remember(isDark) {
            if (isDark) {
                Brush.radialGradient(
                    colors = listOf(AuroraIndigo.copy(alpha = 0.16f), AuroraCyan.copy(alpha = 0.06f), AuroraDeep),
                    radius = 900f,
                )
            } else {
                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(backdrop)) {
            if (isDark) {
                AnimatedCobwebBackground(modifier = Modifier.fillMaxSize())
            }
            Scaffold(
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                containerColor = Color.Transparent,
                topBar = {
                    MainTopBar(
                        isLoading = isLoading,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onFetchConfig = { onAction(MainAction.UpdateSubscriptions) }
                    )
                },
            ) { innerPadding ->
                if (groups.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        ConnectHero(
                            isRunning = isRunning,
                            isTesting = uiState.isTesting,
                            statusText = displayText,
                            onToggle = { onAction(MainAction.ToggleService) },
                            onTest = { onAction(MainAction.TestRealAllServers) },
                            onAutoConnect = { onAction(MainAction.AutoConnect) },
                        )

                        ConnectionStatsPanel(
                            isRunning = isRunning,
                            downloadSpeedText = uiState.downloadSpeedText,
                            uploadSpeedText = uiState.uploadSpeedText,
                            connectionDurationText = uiState.connectionDurationText,
                            remoteIp = uiState.remoteIp,
                            remoteCountryName = uiState.remoteCountryName,
                            remoteCountryCode = uiState.remoteCountryCode,
                        )

                        SuggestedServers(
                            mainViewModel = mainViewModel,
                            groupId = uiState.selectedGroupId,
                            selectedGuid = selectedGuid,
                            onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                            onViewAll = { /* full list already shown below */ },
                        )

                        Box(modifier = Modifier.padding(top = 12.dp))

                        AllServersList(
                            mainViewModel = mainViewModel,
                            groups = groups,
                            selectedGuid = selectedGuid,
                            onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }
}
