package com.narcic.ng.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.narcic.ng.R
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.handler.DefaultConfigSource
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.compose.AccentPair
import com.narcic.ng.ui.compose.AuroraCyan
import com.narcic.ng.ui.compose.AuroraDeep
import com.narcic.ng.ui.compose.AuroraIndigo
import com.narcic.ng.ui.compose.DeleteConfirmDialog
import com.narcic.ng.ui.compose.GooOverlay
import com.narcic.ng.ui.compose.GroupTabs
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import com.narcic.ng.ui.compose.QRCodeDialog
import com.narcic.ng.ui.compose.StatusPill
import com.narcic.ng.ui.compose.accentFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connection screen — the 3-page redesign (امنزیا / وی‌تو‌ری / اتر):
 *  - Top bar: title + fetch-subscriptions + ⋯ "more" sheet + settings gear.
 *  - MainEngineTabBar (bottom): one tab per engine page; selection is
 *    UI-local list filtering only, never the ViewModel's selected server.
 *  - ConnectHero: big connect circle, colored with the active page's accent
 *    (cyan/sky for AWG, violet/indigo for V2Ray, orange for Aether).
 *  - ConnectionDashboard: premium live dashboard (big download number,
 *    smooth area chart, session totals) + the handshake step checklist
 *    while connecting. Hidden while disconnected.
 *  - GroupTabs: "پیش‌فرض" (manually-entered servers) + one tab per
 *    subscription, each showing a live count scoped to the active engine.
 *  - MainServerListSection: single "Test" button + best-5 + full vertical
 *    list, scoped to the selected group *and* the active page (the Aether
 *    page shows only EConfigType.AETHER profiles, and its + opens the
 *    Aether editor directly).
 *  - GooOverlay: full-screen busy state while "تست گروه" is running.
 *  - Every full-screen overlay (subscriptions) registers a BackHandler so
 *    the hardware/gesture back button closes the overlay and returns to
 *    this screen instead of exiting the app. Once no overlay is showing, a
 *    root-level BackHandler takes over and minimizes the app (onMinimize)
 *    instead of finishing the activity.
 *  - The old 4-item bottom nav (سابسکریپشن / وی‌پی‌ان / آمار / تنظیمات) is
 *    replaced by the engine tabs; its other entries moved to the top bar
 *    and the ⋯ sheet.
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
    var showMoreSheet by remember { mutableStateOf(false) }

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

    // ---- Active page (امنزیا / وی‌تو‌ری / اتر) --------------------------
    // Persisted in MMKV so leaving the app from one page and coming back
    // lands on the same page instead of always resetting to وی‌تو‌ری.
    var selectedTab by rememberSaveable {
        mutableStateOf(MmkvManager.decodeSettingsString(PREF_MAIN_SELECTED_TAB) ?: "v2") // "awg" | "v2" | "ae"
    }
    LaunchedEffect(selectedTab) {
        MmkvManager.encodeSettings(PREF_MAIN_SELECTED_TAB, selectedTab)
    }
    val engineIsAwg = selectedTab == "awg"
    val aetherTab = selectedTab == "ae"
    val connectedServer = remember(selectedGuid) { mainViewModel.findServerCache(selectedGuid) }
    LaunchedEffect(selectedGuid) {
        val type = connectedServer?.profile?.configType
        if (type != null) {
            selectedTab = when {
                type == EConfigType.WIREGUARD || type == EConfigType.AMNEZIAWG -> "awg"
                type == EConfigType.AETHER -> "ae"
                else -> "v2"
            }
        }
    }
    val accentPair = when (selectedTab) {
        "ae" -> AccentPair(Nc.BadgeAether, Color(0xFFFB923C))
        else -> accentFor(engineIsAwg)
    }
    val engineLabel = when (selectedTab) {
        "ae" -> "AE"
        "awg" -> "AWG"
        else -> "V2Ray"
    }

    // ---- Strict per-page subscription separation ------------------------
    // A subscription group shows only on the page whose engine it actually
    // carries, decided by the configs inside it (not by hard-coded names):
    // AmneziaWG/WireGuard groups on the امنزیا page, V2Ray-family groups on
    // the وی‌تو‌ری page, Aether-bearing groups on the اتر page. Three rules
    // sharpen it further:
    //   - the three per-engine default groups are pinned to their own page
    //     (so each page always has its manual bucket, even when empty),
    //   - the fixed, pre-seeded Narcic subscriptions stay visible on their
    //     page even before their first fetch,
    //   - any other (user-added) subscription stays hidden until a fetch
    //     actually brought configs for this page's engine.
    val serversByGroup = groups.associate { g ->
        g.id to mainViewModel.serversForGroup(g.id).collectAsStateWithLifecycle().value
    }
    val visibleGroups = remember(groups, serversByGroup, selectedTab) {
        groups.filter { g ->
            val servers = serversByGroup[g.id].orEmpty()
            val hasAwg = servers.any { isAwgType(it.profile.configType) }
            val hasAe = servers.any { it.profile.configType == EConfigType.AETHER }
            val hasV2 = servers.any {
                !isAwgType(it.profile.configType) && it.profile.configType != EConfigType.AETHER
            }
            val matches = when (selectedTab) {
                "ae" -> hasAe
                "awg" -> hasAwg
                else -> hasV2
            }
            when {
                g.remarks == DefaultConfigSource.DEFAULT_GROUP_AWG_NAME -> selectedTab == "awg"
                g.remarks == DefaultConfigSource.DEFAULT_GROUP_AETHER_NAME -> selectedTab == "ae"
                g.remarks == DefaultConfigSource.DEFAULT_GROUP_V2_NAME -> selectedTab == "v2"
                g.remarks == "Narcic NG - WireGuard" -> selectedTab == "awg"
                g.remarks == "Narcic Irancell" || g.remarks == "Narcic NG - JSON" -> selectedTab == "v2"
                else -> matches
            }
        }
    }
    // If switching pages hides the currently-selected group, jump to the
    // first group that's still visible instead of showing a stale list.
    LaunchedEffect(visibleGroups) {
        if (visibleGroups.isNotEmpty() && visibleGroups.none { it.id == uiState.selectedGroupId }) {
            onAction(MainAction.SelectGroup(visibleGroups.first().id))
        }
    }

    // Global per-engine totals were shown on the old EngineSwitch; the
    // 3-page tab bar doesn't use them, so the counters were dropped with it.

    // ---- "Connecting..." handshake state (UI-local) ----------------------
    // The ViewModel doesn't expose a dedicated handshake flag, only the
    // eventual isRunning flip -- so this just bridges tap-to-actually-up.
    // Auto-clears after a timeout in case a start attempt fails silently;
    // the service now also reports MSG_STATE_NOT_RUNNING on a failed start,
    // which clears isRunning-independent state sooner.
    // An Aether profile can spend well over 15s scanning for a working
    // endpoint before the tunnel is actually up (longer still on Stealth/
    // Ironclad scan modes or a slow carrier), so it gets a much longer
    // budget than every other protocol to avoid the button appearing to
    // give up while the service is still legitimately trying.
    var isConnectingLocal by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning) { if (isRunning) isConnectingLocal = false }
    LaunchedEffect(uiState.connectFailedTick) {
        if (uiState.connectFailedTick > 0) isConnectingLocal = false
    }
    LaunchedEffect(isConnectingLocal, connectedServer) {
        if (isConnectingLocal) {
            val isAether = connectedServer?.profile?.configType == EConfigType.AETHER
            delay(if (isAether) 120_000 else 15_000)
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
    // Registered after the root handler so an open "more" sheet wins back.
    BackHandler(enabled = showMoreSheet) { showMoreSheet = false }

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
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    MainTopBar(
                        isLoading = isLoading,
                        onFetchConfig = { onAction(MainAction.UpdateSubscriptions) },
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        onSettingsClick = { onNavigate("settings") },
                        onMoreClick = { showMoreSheet = true },
                    )
                    // Camera-style corner switch: vertical (top→bottom) and
                    // pinned to the left edge, per the redesign spec.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MainEngineSwitch(
                            selectedTab = selectedTab,
                            onSelect = { selectedTab = it },
                        )
                    }
                }
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
                                // Tapping again mid-handshake cancels the attempt:
                                // clear the spinner AND send a hard stop so a
                                // half-started service doesn't keep the user stuck.
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

                // Premium dashboard — big live download number, smooth area
                // chart, session totals; while a handshake is in flight it
                // shows the step checklist instead. Hidden while disconnected.
                ConnectionDashboard(
                    isRunning = isRunning,
                    isConnecting = isConnectingLocal,
                    isAwgProfile = connectedServer?.profile?.let {
                        it.configType == EConfigType.WIREGUARD || it.configType == EConfigType.AMNEZIAWG
                    } == true,
                    isAetherProfile = connectedServer?.profile?.configType == EConfigType.AETHER,
                    pingText = uiState.livePingMillis?.let { it.toString() }
                        ?: connectedServer?.testDelayString?.filter { ch -> ch.isDigit() }.orEmpty(),
                    downloadSpeedText = uiState.downloadSpeedText,
                    uploadSpeedText = uiState.uploadSpeedText,
                    connectionDurationText = uiState.connectionDurationText,
                    remoteIp = uiState.remoteIp,
                    remoteCountryName = uiState.remoteCountryName,
                    remoteCountryCode = uiState.remoteCountryCode,
                    engineLabel = engineLabel,
                    protocolLabel = connectedServer?.profile?.configType?.toString() ?: engineLabel,
                    accent = accentPair.main,
                    speedHistory = speedHistory,
                )

                Spacer(Modifier.height(10.dp))

                GroupTabs(
                    groups = visibleGroups,
                    selectedGroupId = uiState.selectedGroupId,
                    accent = accentPair.main,
                    engineIsAwg = engineIsAwg,
                    aetherOnly = aetherTab,
                    serverFlowFor = { id -> mainViewModel.serversForGroup(id) },
                    enabled = !isRunning,
                    onSelect = { id -> onAction(MainAction.SelectGroup(id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                // Aether page guard: matches the Aether core's own rule —
                // only one Aether profile can run, and scanning/renewing are
                // locked while the session is alive (enforced in
                // ServerAetherViewModel; this is just the heads-up text).
                if (aetherTab) {
                    Text(
                        text = "⚠ فقط یک کانفیگ Aether در هر لحظه می‌تواند اجرا شود؛ هنگام اجرا، اسکن اندپوینت و تعویض کلید WARP قفل می‌شوند.",
                        color = accentPair.main.copy(alpha = .9f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .background(accentPair.main.copy(alpha = .08f))
                            .border(
                                1.dp,
                                accentPair.main.copy(alpha = .35f),
                                androidx.compose.foundation.shape.RoundedCornerShape(13.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }

                // ---- تست + لیست عمودی سرورها (فیلترشده روی گروه + موتور فعال) ----
                MainServerListSection(
                    mainViewModel = mainViewModel,
                    groups = visibleGroups,
                    selectedGroupId = uiState.selectedGroupId,
                    selectedGuid = selectedGuid,
                    isTesting = uiState.isTesting,
                    engineIsAwg = engineIsAwg,
                    aetherOnly = aetherTab,
                    accent = accentPair.main,
                    onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) },
                    onEditServer = { guid, profile -> onAction(MainAction.EditServer(guid, profile)) },
                    onShareAction = { action -> onAction(action) },
                    onRemoveServer = removeServer,
                    onDeleteAllServers = { showDelAllConfirm = true },
                    onAddFromClipboard = { onAction(MainAction.ImportVpnFromClipboard) },
                    onScanVpnQr = { onAction(MainAction.ImportQRcode) },
                    onAddManualConfig = { type -> onAction(MainAction.ImportManually(type)) },
                    onRetest = { onAction(MainAction.TestGroupServers(uiState.selectedGroupId)) },
                    onAutoSelectBest = { onAction(MainAction.AutoConnect) },
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

        // Full-screen smart loading overlay while AmneziaWG is performing background auto-retries
        GooOverlay(
            visible = uiState.isAwgConnecting,
            message = uiState.awgConnectingMessage.ifBlank { "در حال اتصال، لطفاً صبر کنید..." },
            accent = accentPair.main,
        )

        // ── "more" sheet: the entries that used to live in the 4-item
        // bottom nav (سابسکریپشن / آمار) plus the previously-unreachable
        // management screens (logs / backup / routing) and the import drawer.
        if (showMoreSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_subscriptions_24dp), null, tint = accentPair.main) },
                    label = "سابسکریپشن‌ها",
                ) { showMoreSheet = false; showSubscriptions = true }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_stats_24dp), null, tint = accentPair.main) },
                    label = "آمار ترافیک",
                ) { showMoreSheet = false; onNavigate("statistics") }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_menu_24dp), null, tint = accentPair.main) },
                    label = "ورود کانفیگ (کلیپ‌بورد / QR / دستی)",
                ) {
                    showMoreSheet = false
                    coroutineScope.launch { drawerState.open() }
                }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_logcat_24dp), null, tint = accentPair.main) },
                    label = "گزارشات هسته",
                ) { showMoreSheet = false; onNavigate("logcat") }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_backup_24dp), null, tint = accentPair.main) },
                    label = "پشتیبان‌گیری و بازیابی",
                ) { showMoreSheet = false; onNavigate("backup") }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_routing_24dp), null, tint = accentPair.main) },
                    label = "تنظیمات مسیریابی",
                ) { showMoreSheet = false; onNavigate("routing") }
                MoreSheetRow(
                    icon = { Icon(painterResource(com.narcic.ng.R.drawable.ic_settings_24dp), null, tint = accentPair.main) },
                    label = "تنظیمات",
                ) { showMoreSheet = false; onNavigate("settings") }
                Spacer(Modifier.height(14.dp))
            }
        }
        }
    }
}

@Composable
private fun MoreSheetRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp)
    ) {
        icon()
        Spacer(Modifier.width(13.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** AmneziaWG engine == WireGuard-family config types (WIREGUARD + AMNEZIAWG). */
private fun isAwgType(type: EConfigType): Boolean =
    type == EConfigType.WIREGUARD || type == EConfigType.AMNEZIAWG

/** MMKV key keeping the active engine page across app restarts. */
private const val PREF_MAIN_SELECTED_TAB = "cache_main_selected_tab"
