package com.narcic.ng.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.R
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.DeleteConfirmDialog
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.QRCodeDialog
import com.narcic.ng.ui.compose.SpiderWebCorners
import kotlinx.coroutines.launch

/**
 * Connection screen:
 *  - Top bar: a drawer/menu button (top-left) + the title + fetch-subscriptions
 *    action. The drawer holds "Import config" (link/clipboard/QR/local file/
 *    manual-by-protocol) and "Manage configs" (test/sort/export-all + delete
 *    all / duplicate / invalid) — see MainDrawer.kt.
 *  - ConnectHero: big connect circle.
 *  - ConnectionStatsPanel: the "ping / IP / speed" card. Stays hidden while
 *    disconnected and only appears once the user has picked a server from
 *    the list below and actually connected (visible = isRunning).
 *  - MainServerListSection: group tabs ("پیش‌فرض" = manually-entered
 *    servers, like V2rayNg, plus one tab per subscription) with a single
 *    "Test" button and a vertical server list, both scoped to whichever
 *    tab/group is currently selected. Best ping always sorts to the top,
 *    including inside the "Best Servers" (top 5) box.
 *  - Every full-screen overlay (subscriptions) registers a BackHandler so
 *    the hardware/gesture back button closes the overlay and returns to
 *    this screen instead of exiting the app. Once no overlay is showing, a
 *    root-level BackHandler takes over and minimizes the app (onMinimize)
 *    instead of finishing the activity.
 *  - Bottom nav: سابسکریپشن / وی‌پی‌ان / آمار / تنظیمات. Only the VPN tab
 *    renders this Scaffold; the other three either open a full-screen
 *    overlay or launch their own Activity (Statistics, Settings).
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

    // Bottom-nav overlay screens.
    var showSubscriptions by remember { mutableStateOf(false) }
    var showAddSubscription by remember { mutableStateOf(false) }

    // Top-left drawer: "Import config" (link/clipboard/QR/local/manual) +
    // "Manage configs" (test/sort/export-all + bulk delete).
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showDelAllConfirm by remember { mutableStateOf(false) }
    var showDelDuplicateConfirm by remember { mutableStateOf(false) }
    var showDelInvalidConfirm by remember { mutableStateOf(false) }

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

    // "Share > QR Code" from a server row: the ViewModel renders the bitmap
    // into uiState.shareQRCodeBitmap (see MainAction.ShareQRCode); this is
    // what actually shows it to the user. QRCodeDialog no-ops on a null bitmap.
    QRCodeDialog(
        bitmap = uiState.shareQRCodeBitmap,
        onDismiss = { onAction(MainAction.DismissQRCodeDialog) }
    )

    // ---- Drawer's "Manage configs" bulk-delete confirmations ----
    if (showDelAllConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_visible_profiles),
            onConfirm = { showDelAllConfirm = false; onAction(MainAction.RemoveAllServers) },
            onDismiss = { showDelAllConfirm = false },
        )
    }
    if (showDelDuplicateConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_duplicate_profiles),
            onConfirm = { showDelDuplicateConfirm = false; onAction(MainAction.RemoveDuplicateServers) },
            onDismiss = { showDelDuplicateConfirm = false },
        )
    }
    if (showDelInvalidConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_invalid_profiles),
            onConfirm = { showDelInvalidConfirm = false; onAction(MainAction.RemoveInvalidServers) },
            onDismiss = { showDelInvalidConfirm = false },
        )
    }

    if (showAddSubscription) {
        AddSubscriptionDialog(
            onAdd = { name, content ->
                onAction(MainAction.AddSubscriptionFromText(name, content))
                showAddSubscription = false
            },
            onDismiss = { showAddSubscription = false },
        )
    }

    // ---- Full-screen overlay: subscriptions ----
    if (showSubscriptions) {
        BackHandler { showSubscriptions = false }
        SubscriptionsScreen(
            mainViewModel = mainViewModel,
            groups = groups,
            selectedGroupId = uiState.selectedGroupId,
            isAdding = isLoading,
            onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
            onDelete = { id -> showDelSubscriptionConfirm = id },
            onEdit = { groupId, name, url -> onAction(MainAction.EditSubscription(groupId, name, url)) },
            onAddClick = { showAddSubscription = true },
            onBack = { showSubscriptions = false },
            onScanSubscriptionQr = { text -> onAction(MainAction.ImportSubscriptionFromQr(text)) },
        )
        return
    }

    // No overlay is showing (all branches above return early), so this is
    // the root connection screen: back should minimize the app instead of
    // finishing the activity — unless the drawer is open, in which case back
    // just closes the drawer.
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen) { onMinimize() }

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
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                onNavigate = onNavigate,
                onAction = { action ->
                    coroutineScope.launch { drawerState.close() }
                    onAction(action)
                },
                onDelAllConfig = {
                    coroutineScope.launch { drawerState.close() }
                    showDelAllConfirm = true
                },
                onDelDuplicateConfig = {
                    coroutineScope.launch { drawerState.close() }
                    showDelDuplicateConfirm = true
                },
                onDelInvalidConfig = {
                    coroutineScope.launch { drawerState.close() }
                    showDelInvalidConfirm = true
                },
            )
        },
    ) {
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
                    onFetchConfig = { onAction(MainAction.UpdateSubscriptions) },
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
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
            val connectedServer = mainViewModel.findServerCache(selectedGuid)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
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

                // Ping / IP / speed card — stays hidden until the user has
                // picked a server below and actually connected.
                ConnectionStatsPanel(
                    isRunning = isRunning,
                    pingText = uiState.livePingMillis?.let { "${it}ms" }
                        ?: connectedServer?.testDelayString.orEmpty(),
                    downloadSpeedText = uiState.downloadSpeedText,
                    uploadSpeedText = uiState.uploadSpeedText,
                    connectionDurationText = uiState.connectionDurationText,
                    remoteIp = uiState.remoteIp,
                    remoteCountryName = uiState.remoteCountryName,
                    remoteCountryCode = uiState.remoteCountryCode,
                )

                // ---- Group tabs (پیش‌فرض + هر سابسکریپشن) + تست + لیست عمودی سرورها ----
                MainServerListSection(
                    mainViewModel = mainViewModel,
                    groups = groups,
                    selectedGroupId = uiState.selectedGroupId,
                    selectedGuid = selectedGuid,
                    isTesting = uiState.isTesting,
                    onSelectGroup = { id -> onAction(MainAction.SelectGroup(id)) },
                    onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                    onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                    onShareAction = { action -> onAction(action) },
                    onRemoveServer = removeServer,
                    onAddFromClipboard = { onAction(MainAction.ImportVpnFromClipboard) },
                    onScanVpnQr = { onAction(MainAction.ImportQRcode) },
                    onRetest = { onAction(MainAction.TestGroupServers(uiState.selectedGroupId)) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
    }
}
