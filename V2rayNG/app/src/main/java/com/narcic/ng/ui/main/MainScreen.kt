package com.narcic.ng.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.R
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.DeleteConfirmDialog
import com.narcic.ng.ui.compose.EngineOption
import com.narcic.ng.ui.compose.EngineSwitch
import com.narcic.ng.ui.compose.GooOverlay
import com.narcic.ng.ui.compose.GroupTabs
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.QRCodeDialog
import com.narcic.ng.ui.compose.SpiderWebCorners
import com.narcic.ng.ui.compose.StatusPill
import com.narcic.ng.ui.compose.accentFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connection screen — "two engines, one screen":
 *  - Top bar: drawer/menu button + title + fetch-subscriptions action.
 *  - StatusPill + EngineSwitch: pick AmneziaWG vs V2Ray. Locked while a
 *    tunnel is up (the two engines never run at the same time).
 *  - ConnectHero: big connect circle, colored with the active engine's
 *    accent (cyan/sky for AWG, violet/indigo for V2Ray).
 *  - ConnectionStatsPanel: ping / IP / live-speed sparkline card. Hidden
 *    while disconnected.
 *  - GroupTabs: "پیش‌فرض" (manually-entered servers) + one tab per
 *    subscription, each showing a live count scoped to the active engine.
 *  - MainServerListSection: single "Test" button + best-5 + full vertical
 *    list, all scoped to the selected group *and* the active engine.
 *  - GooOverlay: full-screen busy state while "تست گروه" is running.
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

    // ---- Active engine (AmneziaWG vs V2Ray) -----------------------------
    // UI-local: which engine's servers are currently *shown*. It starts in
    // sync with whatever server is actually selected in the ViewModel, but
    // the user can freely browse the other engine's tab without that
    // changing the real selection until they tap a server card
    // (SelectServer) -- the ViewModel's notion of "selected server" is
    // untouched by this switch.
    var engineIsAwg by rememberSaveable { mutableStateOf(false) }
    val connectedServer = remember(selectedGuid) { mainViewModel.findServerCache(selectedGuid) }
    LaunchedEffect(selectedGuid) {
        val type = connectedServer?.profile?.configType
        if (type != null) {
            engineIsAwg = type == EConfigType.WIREGUARD || type == EConfigType.AMNEZIAWG
        }
    }
    val accentPair = remember(engineIsAwg) { accentFor(engineIsAwg) }
    val engineLabel = if (engineIsAwg) "AWG" else "V2Ray"

    // Global per-engine totals shown as the small counter badge on each
    // EngineSwitch tab (sums every group's servers, not just the selected
    // one) -- each group's flow is shared/cached in the ViewModel, so this
    // read is cheap even though GroupTabs also reads the same flows below.
    val allServers = groups.flatMap { g -> mainViewModel.serversForGroup(g.id).collectAsStateWithLifecycle().value }
    val awgTotal = allServers.count { s ->
        val t = s.profile.configType
        t == EConfigType.WIREGUARD || t == EConfigType.AMNEZIAWG
    }
    val v2Total = allServers.size - awgTotal

    // ---- "Connecting..." handshake state (UI-local) ----------------------
    // The ViewModel doesn't expose a dedicated handshake flag, only the
    // eventual isRunning flip -- so this just bridges tap-to-actually-up.
    // Auto-clears after a timeout in case a start attempt fails silently.
    var isConnectingLocal by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning) { if (isRunning) isConnectingLocal = false }
    LaunchedEffect(isConnectingLocal) {
        if (isConnectingLocal) {
            delay(20_000)
            isConnectingLocal = false
        }
    }

    // ---- Live download-speed sparkline buffer (28-point rolling window) --
    val speedHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(uiState.downloadSpeedText, isRunning) {
        if (!isRunning) {
            speedHistory.clear()
            repeat(28) { speedHistory.add(0f) }
        } else {
            val v = uiState.downloadSpeedText.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
            speedHistory.add(v)
            if (speedHistory.size > 28) speedHistory.removeAt(0)
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StatusPill(
                        isRunning = isRunning,
                        isConnecting = isConnectingLocal,
                        engineLabel = engineLabel,
                    )
                }

                Spacer(Modifier.height(10.dp))

                EngineSwitch(
                    options = listOf(
                        EngineOption("awg", "AmneziaWG", awgTotal) { tint -> Icon(Icons.Rounded.Shield, null, tint = tint, modifier = Modifier.size(16.dp)) },
                        EngineOption("v2ray", "V2Ray", v2Total) { tint -> Icon(Icons.Rounded.Bolt, null, tint = tint, modifier = Modifier.size(16.dp)) },
                    ),
                    selectedId = if (engineIsAwg) "awg" else "v2ray",
                    accent = accentPair.main,
                    enabled = !isRunning,
                    onSelect = { id -> engineIsAwg = id == "awg" },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(10.dp))

                ConnectHero(
                    isRunning = isRunning,
                    isConnecting = isConnectingLocal,
                    isTesting = uiState.isTesting,
                    statusText = when {
                        isRunning -> "متصل · $engineLabel"
                        isConnectingLocal -> "در حال اتصال..."
                        else -> "قطع شده"
                    },
                    timeText = when {
                        isRunning -> uiState.connectionDurationText.ifBlank { "۰۰:۰۰:۰۰" }
                        isConnectingLocal -> "در حال برقراری تونل..."
                        else -> "برای اتصال لمس کنید"
                    },
                    accent = accentPair.main,
                    accent2 = accentPair.second,
                    onToggle = {
                        when {
                            isRunning -> {
                                isConnectingLocal = false
                                onAction(MainAction.ToggleService)
                            }
                            isConnectingLocal -> {
                                // Tapping again mid-handshake cancels the attempt.
                                isConnectingLocal = false
                                onAction(MainAction.ToggleService)
                            }
                            else -> {
                                isConnectingLocal = true
                                if (uiState.autoConnection) onAction(MainAction.AutoConnect)
                                else onAction(MainAction.ToggleService)
                            }
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
                    engineLabel = engineLabel,
                    accent = accentPair.main,
                    speedHistory = speedHistory,
                )

                Spacer(Modifier.height(10.dp))

                GroupTabs(
                    groups = groups,
                    selectedGroupId = uiState.selectedGroupId,
                    accent = accentPair.main,
                    engineIsAwg = engineIsAwg,
                    serverFlowFor = { id -> mainViewModel.serversForGroup(id) },
                    enabled = !isRunning,
                    onSelect = { id -> onAction(MainAction.SelectGroup(id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                // ---- تست + لیست عمودی سرورها (فیلترشده روی گروه + موتور فعال) ----
                MainServerListSection(
                    mainViewModel = mainViewModel,
                    groups = groups,
                    selectedGroupId = uiState.selectedGroupId,
                    selectedGuid = selectedGuid,
                    isTesting = uiState.isTesting,
                    engineIsAwg = engineIsAwg,
                    accent = accentPair.main,
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

        // Full-screen busy state while a whole group's servers are being
        // tested ("تست" button above) -- blocks interaction with a scrim
        // rather than leaving the list silently reordering underneath.
        GooOverlay(
            visible = uiState.isTesting,
            message = "در حال تست سرورها...",
            accent = accentPair.main,
        )
    }
    }
}
