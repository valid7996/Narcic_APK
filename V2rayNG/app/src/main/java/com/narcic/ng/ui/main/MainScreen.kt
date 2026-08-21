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
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.SpiderWebCorners
import kotlinx.coroutines.launch

/**
 * Simplified connection screen:
 *  - Top bar: just the drawer menu + fetch-subscriptions action (search, manual
 *    import, and the config-management menu now live in the drawer).
 *  - ConnectHero: big connect circle + a "Test" button for a real/precise
 *    delay test of every config.
 *  - HomeVpnCard: the tappable موقعیت / اتصال / سابسکریپشن card. Each row
 *    opens its own full-screen picker (see the `show*` overlays below).
 *  - Suggested servers: the fastest-tested configs, tap to select.
 *  - AllServersList: a clean, flat, tap-to-select list of every received
 *    server — no tabs, no swipe-to-reveal delete/play icons.
 *  - Bottom nav: سابسکریپشن / وی‌پی‌ان / تنظیمات. Only the VPN tab renders
 *    this Scaffold; the other two either open a full-screen overlay or
 *    launch SettingsActivity, matching the reference screenshots (none of
 *    the sub-screens keep the bottom bar visible).
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

    // Home "VPN" card + bottom-nav overlay screens.
    var showLocationPicker by remember { mutableStateOf(false) }
    var showConnectionPicker by remember { mutableStateOf(false) }
    var showSubscriptions by remember { mutableStateOf(false) }
    var showAddSubscription by remember { mutableStateOf(false) }

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

    if (showAddSubscription) {
        AddSubscriptionDialog(
            onAdd = { name, content ->
                onAction(MainAction.AddSubscriptionFromText(name, content))
                showAddSubscription = false
            },
            onDismiss = { showAddSubscription = false },
        )
    }

    // ---- Full-screen overlays (image 3 / image 4-8 / image 5-7) ----
    if (showLocationPicker) {
        val servers by mainViewModel.serversForGroup(uiState.selectedGroupId).collectAsStateWithLifecycle()
        LocationPickerScreen(
            servers = servers,
            selectedFlag = uiState.locationFlag,
            onSelect = { flag ->
                onAction(MainAction.SetLocationFilter(flag))
                showLocationPicker = false
            },
            onBack = { showLocationPicker = false },
        )
        return
    }

    if (showConnectionPicker) {
        val servers by mainViewModel.serversForGroup(uiState.selectedGroupId).collectAsStateWithLifecycle()
        ConnectionPickerScreen(
            servers = servers,
            groups = groups,
            selectedGroupId = uiState.selectedGroupId,
            isTesting = uiState.isTesting,
            autoConnection = uiState.autoConnection,
            selectedGuid = selectedGuid,
            onSelectAuto = {
                onAction(MainAction.SetAutoConnection)
                showConnectionPicker = false
            },
            onSelectServer = { guid ->
                onAction(MainAction.SetManualConnection(guid))
                showConnectionPicker = false
            },
            onRetest = { onAction(MainAction.TestRealAllServers) },
            onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
            onBack = { showConnectionPicker = false },
        )
        return
    }

    if (showSubscriptions) {
        SubscriptionsScreen(
            mainViewModel = mainViewModel,
            groups = groups,
            selectedGroupId = uiState.selectedGroupId,
            onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
            onRefresh = { id -> onAction(MainAction.RefreshSubscription(id)) },
            onTest = { id ->
                onAction(MainAction.SelectGroup(id))
                showSubscriptions = false
                showConnectionPicker = true
            },
            onAddClick = { showAddSubscription = true },
            onBack = { showSubscriptions = false },
        )
        return
    }

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
                // Decorative animated spiderweb in the four corners, echoing the
                // launcher icon. Sits behind all real UI and never intercepts touch.
                SpiderWebCorners(modifier = Modifier.fillMaxSize())
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
                bottomBar = {
                    MainVpnBottomNav(
                        selectedTab = MainHomeTab.VPN,
                        onSelectTab = { tab ->
                            if (tab == MainHomeTab.SUBSCRIPTIONS) showSubscriptions = true
                        },
                        onSettingsClick = { onNavigate("settings") },
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
                            onToggle = {
                                if (!isRunning && uiState.autoConnection) {
                                    onAction(MainAction.AutoConnect)
                                } else {
                                    onAction(MainAction.ToggleService)
                                }
                            },
                            onTest = { onAction(MainAction.TestRealAllServers) },
                            onAutoConnect = { onAction(MainAction.AutoConnect) },
                        )

                        HomeVpnCard(
                            isRunning = isRunning,
                            locationFlag = uiState.locationFlag,
                            autoConnection = uiState.autoConnection,
                            connectedServer = mainViewModel.findServerCache(selectedGuid),
                            subscriptionName = groups.firstOrNull { it.id == uiState.selectedGroupId }?.remarks.orEmpty(),
                            onOpenLocation = { showLocationPicker = true },
                            onOpenConnection = { showConnectionPicker = true },
                            onOpenSubscriptions = { showSubscriptions = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
