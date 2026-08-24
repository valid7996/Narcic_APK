package com.narcic.ng.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Simplified connection screen:
 *  - Top bar: just the title + fetch-subscriptions action. There is no
 *    drawer/menu button — Import config / Manage configs have been removed.
 *  - ConnectHero: big connect circle + a "Test" button for a real/precise
 *    delay test of every config.
 *  - HomeVpnCard: the tappable موقعیت / اتصال / سابسکریپشن card. Each row
 *    opens its own full-screen picker (see the `show*` overlays below).
 *  - Every full-screen overlay (location/connection picker, subscriptions)
 *    registers a BackHandler so the hardware/gesture back button closes the
 *    overlay and returns to this screen instead of exiting the app. Once no
 *    overlay is showing, a root-level BackHandler takes over and minimizes
 *    the app (onMinimize) instead of finishing the activity.
 *  - ConnectionPickerScreen lists every config with a delete icon per row.
 *  - Bottom nav: سابسکریپشن / وی‌پی‌ان / آمار / تنظیمات. Only the VPN tab
 *    renders this Scaffold; the other three either open a full-screen
 *    overlay or launch their own Activity (Statistics, Settings), matching
 *    the reference screenshots (none of the sub-screens keep the bottom bar
 *    visible).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val groups = uiState.groups
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val isRunning = uiState.isRunning
    val displayText = uiState.statusText
    val selectedGuid = uiState.selectedGuid
    val confirmRemove = uiState.confirmRemove

    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }
    var showDelSubscriptionConfirm by remember { mutableStateOf<String?>(null) }

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
        showRemoveConfirm = showRemoveConfirm,
        onDismissRemove = { showRemoveConfirm = null },
        onConfirmRemove = { guid -> showRemoveConfirm = null; onAction(MainAction.RemoveServer(guid)) },
        showDelSubscriptionConfirm = showDelSubscriptionConfirm,
        onDismissDelSubscription = { showDelSubscriptionConfirm = null },
        onConfirmDelSubscription = { groupId ->
            showDelSubscriptionConfirm = null
            onAction(MainAction.RemoveSubscriptionGroup(groupId))
        }
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
        BackHandler { showLocationPicker = false }
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
        BackHandler { showConnectionPicker = false }
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
            onRetest = { onAction(MainAction.TestGroupServers(uiState.selectedGroupId)) },
            onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
            onDelete = removeServer,
            onSortByTest = { onAction(MainAction.SortByTestResults) },
            onRemoveInvalid = { onAction(MainAction.RemoveInvalidServers) },
            onBack = { showConnectionPicker = false },
        )
        return
    }

    if (showSubscriptions) {
        BackHandler { showSubscriptions = false }
        SubscriptionsScreen(
            mainViewModel = mainViewModel,
            groups = groups,
            selectedGroupId = uiState.selectedGroupId,
            isAdding = isLoading,
            onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
            onRefresh = { id -> onAction(MainAction.RefreshSubscription(id)) },
            onTest = { id ->
                onAction(MainAction.SelectGroup(id))
                showSubscriptions = false
                showConnectionPicker = true
            },
            onDelete = { id -> showDelSubscriptionConfirm = id },
            onAddClick = { showAddSubscription = true },
            onBack = { showSubscriptions = false },
        )
        return
    }

    // No overlay is showing (all branches above return early), so this is
    // the root connection screen: back should minimize the app instead of
    // finishing the activity, matching the "don't exit on back" behavior
    // the app wants everywhere except from an overlay.
    BackHandler { onMinimize() }

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
                    onStatisticsClick = { onNavigate("statistics") },
                )
            },
        ) { innerPadding ->
            if (groups.isNotEmpty()) {
                val connectedServer = mainViewModel.findServerCache(selectedGuid)
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
                        onCheckConnection = { onAction(MainAction.TestCurrentServer) },
                    )

                    // Only one of these two cards shows at a time: while
                    // disconnected the user picks موقعیت/اتصال/سابسکریپشن
                    // here; once connected this makes way for the live
                    // ping/speed/country panel below.
                    AnimatedVisibility(
                        visible = !isRunning,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        HomeVpnCard(
                            isRunning = isRunning,
                            locationFlag = uiState.locationFlag,
                            autoConnection = uiState.autoConnection,
                            connectedServer = connectedServer,
                            subscriptionName = groups.firstOrNull { it.id == uiState.selectedGroupId }?.remarks.orEmpty(),
                            onOpenLocation = { showLocationPicker = true },
                            onOpenConnection = { showConnectionPicker = true },
                            onOpenSubscriptions = { showSubscriptions = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    ConnectionStatsPanel(
                        isRunning = isRunning,
                        pingText = connectedServer?.testDelayString.orEmpty(),
                        downloadSpeedText = uiState.downloadSpeedText,
                        uploadSpeedText = uiState.uploadSpeedText,
                        connectionDurationText = uiState.connectionDurationText,
                        remoteIp = uiState.remoteIp,
                        remoteCountryName = uiState.remoteCountryName,
                        remoteCountryCode = uiState.remoteCountryCode,
                    )
                }
            }
        }
    }
}
