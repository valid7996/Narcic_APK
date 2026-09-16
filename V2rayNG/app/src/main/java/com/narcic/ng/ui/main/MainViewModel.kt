package com.narcic.ng.ui.main

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.LocateTarget
import com.narcic.ng.dto.TestServiceMessage
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.dto.entities.SubscriptionCache
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.extension.matchesPattern
import com.narcic.ng.extension.moveItem
import com.narcic.ng.extension.toSpeedString
import com.narcic.ng.handler.SpeedtestManager
import com.narcic.ng.ui.base.BaseViewModel
import com.narcic.ng.util.CountryFlags
import com.narcic.ng.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.PatternSyntaxException

class MainViewModel(
    application: Application,
    private val dataSource: MainDataSource
) : BaseViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val preloadDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val disconnectedText: String = dataSource.getString(R.string.connection_not_connected)
    private val connectedText: String = dataSource.getString(R.string.connection_connected)

    // ---------- UI state ----------
    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedGroupId = dataSource.getSelectedSubscriptionId(),
            selectedGuid = dataSource.getSelectServer(),
            statusText = disconnectedText,
            confirmRemove = dataSource.getConfirmRemove(),
            doubleColumnDisplay = dataSource.getDoubleColumnDisplay(),
            locationFlag = dataSource.getLocationFlag(),
            autoConnection = dataSource.getAutoConnection()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Keyword filtering ----------
    @Volatile
    private var keywordFilter: String = ""
    private var filterJob: Job? = null

    // ---------- Groups & cache ----------
    private val cacheMutex = Mutex()
    private val groupDataCache = mutableMapOf<String, List<ServersCache>>()
    private val groupPageFlows = ConcurrentHashMap<String, MutableStateFlow<List<ServersCache>>>()
    private val groupLoadMutexes = ConcurrentHashMap<String, Mutex>()
    private val serverOrderPersistenceJobs = mutableMapOf<String, Job>()

    private var setupGroupJob: Job? = null
    private var preloadJob: Job? = null
    private var selectedGroupLoadJob: Job? = null
    private var reloadJob: Job? = null

    // ---------- Live connection stats ----------
    private var connectionTimerJob: Job? = null
    private var ipLookupJob: Job? = null
    private var connectStartElapsedRealtime: Long = 0L

    @Volatile
    private var pendingAutoConnect: Boolean = false

    @Volatile
    private var pendingAutoConnectThresholdMillis: Long = 0L

    @Volatile
    private var testingGroupId: String? = null

    private val initialPageReady = CompletableDeferred<Unit>()

    // ---------- Service events ----------
    init {
        collectServiceEvents()
        setupGroupTab()
    }

    private fun collectServiceEvents() {
        viewModelScope.launch {
            dataSource.mainServiceEvent.collect { event ->
                handleServiceEvent(event)
            }
        }
    }

    private fun handleServiceEvent(event: MainServiceEvent) {
        when (event) {
            MainServiceEvent.StateRunning -> updateRunningState(true, clearTestingText = false)
            MainServiceEvent.StateNotRunning -> updateRunningState(false, clearTestingText = false)
            MainServiceEvent.StateStartSuccess -> {
                toastSuccess(R.string.toast_services_success)
                updateRunningState(true)
            }

            is MainServiceEvent.StateStartFailure -> {
                val error = event.errorMessage
                if (error.isNotBlank()) {
                    toastError(error)
                } else {
                    toastError(R.string.toast_services_failure)
                }
                updateRunningState(false)
                _uiState.update { it.copy(connectFailedTick = it.connectFailedTick + 1) }
            }

            MainServiceEvent.StateStopSuccess -> updateRunningState(false)
            is MainServiceEvent.MeasureDelaySuccess -> {
                _uiState.update {
                    it.copy(
                        statusText = event.content,
                        livePingMillis = if (event.delayMillis >= 0) event.delayMillis else it.livePingMillis
                    )
                }
            }

            is MainServiceEvent.MeasureConfigSuccess -> {
                checkAutoConnectEarlyStop(event.guid)
                viewModelScope.launch(ioDispatcher) {
                    val gid = testingGroupId ?: uiState.value.selectedGroupId
                    cacheMutex.withLock { groupDataCache.remove(gid) }
                    updateGroupUi(gid, loadGroup(gid, forceRefresh = true))
                }
            }

            is MainServiceEvent.MeasureConfigNotify -> {
                _uiState.update {
                    it.copy(
                        statusText = dataSource.getString(
                            R.string.connection_runing_task_left,
                            event.progress
                        )
                    )
                }
            }

            is MainServiceEvent.MeasureConfigFinish -> {
                if (event.finishedCount == "0") {
                    onTestsFinished()
                }
            }

            is MainServiceEvent.TrafficUpdate -> {
                _uiState.update {
                    it.copy(
                        downloadSpeedText = event.downloadBps.toSpeedString(),
                        uploadSpeedText = event.uploadBps.toSpeedString()
                    )
                }
            }
        }
    }

    // ---------- Public state accessors ----------
    fun serversForGroup(groupId: String): StateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }
            .asStateFlow()

    private fun mutableServersForGroup(groupId: String): MutableStateFlow<List<ServersCache>> =
        groupPageFlows.computeIfAbsent(groupId) { MutableStateFlow(emptyList()) }

    private fun currentServers(): List<ServersCache> =
        mutableServersForGroup(uiState.value.selectedGroupId).value

    // Manual servers (subscriptionId = "" -> DEFAULT_SUBSCRIPTION_ID)
    fun manualServersFlow(): StateFlow<List<ServersCache>> =
        serversForGroup(AppConfig.DEFAULT_SUBSCRIPTION_ID)

    fun getTop5BestServers(): List<ServersCache> {
        val all = if (isGroupAllDisplayEnabled()) {
            // When All enabled, manualServersFlow is separate; consider manual only for top5 in main list
            manualServersFlow().value
        } else {
            manualServersFlow().value
        }
        return all.filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)
    }

    private fun isGroupAllDisplayEnabled(): Boolean = dataSource.isGroupAllDisplayEnabled()

    // ---------- Action handler ----------
    fun onAction(action: MainAction) {
        when (action) {
            MainAction.Initialize -> initialize()
            MainAction.RefreshGroups -> setupGroupTab(forceRefresh = true)
            MainAction.TestAllServers -> testAllRealPing(true)
            MainAction.TestRealAllServers -> testAllRealPing()
            is MainAction.TestGroupServers -> testGroupRealPing(action.groupId)
            MainAction.CancelTesting -> cancelAllPing()
            MainAction.RemoveAllServers -> removeAllServerAsync()
            MainAction.RemoveDuplicateServers -> removeDuplicateServerAsync()
            MainAction.RemoveInvalidServers -> removeInvalidServerAsync()
            MainAction.SortByTestResults -> sortByTestResultsAsync()
            MainAction.UpdateSubscriptions -> importConfigViaSub()
            MainAction.UpdateAllSubscriptions -> importConfigViaSub(forceAll = true)
            MainAction.ExportAll -> exportAllAsync()
            MainAction.AutoConnect -> autoConnect()
            is MainAction.SelectGroup -> subscriptionIdChanged(action.groupId)
            is MainAction.SelectServer -> selectServerManually(action.guid)
            is MainAction.RemoveServer -> removeServerAndRefresh(action.guid)
            is MainAction.Search -> filterConfig(action.query)
            is MainAction.ImportBatchConfig -> importBatchConfig(action.configText)
            is MainAction.ImportVpnConfig -> importManualVpnConfig(action.configText)
            MainAction.ImportVpnFromClipboard -> importVpnFromClipboard()
            is MainAction.ImportSubscriptionFromClipboard -> importSubscriptionFromClipboard(action.text)
            is MainAction.ImportSubscriptionFromQr -> importSubscriptionFromQr(action.text)
            is MainAction.LocateHandled -> consumeLocateTarget(action.target)
            is MainAction.SetLocationFilter -> setLocationFilter(action.flag)
            MainAction.SetAutoConnection -> setAutoConnection(true)
            is MainAction.SetManualConnection -> setManualConnection(action.guid)
            is MainAction.RefreshSubscription -> refreshSubscription(action.subId)
            is MainAction.AddSubscriptionFromText -> addSubscriptionFromText(action.name, action.content)
            is MainAction.RemoveSubscriptionGroup -> removeSubscriptionGroup(action.groupId)
            is MainAction.EditSubscription -> editSubscription(action.groupId, action.name, action.url)
            is MainAction.ShareQRCode -> {
                val bitmap = dataSource.share2QRCode(action.guid)
                _uiState.update { it.copy(shareQRCodeBitmap = bitmap) }
            }

            MainAction.DismissQRCodeDialog -> {
                _uiState.update { it.copy(shareQRCodeBitmap = null) }
            }

            MainAction.ToggleService,
            MainAction.TestCurrentServer,
            MainAction.ImportQRcode,
            MainAction.ImportClipboard,
            MainAction.ImportConfigLocal,
            is MainAction.ImportManually,
            MainAction.RestartService,
            MainAction.LocateSelectedServer,
            is MainAction.EditServer,
            is MainAction.ShareClipboard,
            MainAction.ImportWarpOnWarp,
            is MainAction.ShareFullContent, is MainAction.ShareLink -> {
                // Handled by Activity via its onAction lambda
            }
        }
    }

    // ---------- Initialization ----------
    fun initialize() {
        viewModelScope.launch(preloadDispatcher) {
            try {
                initialPageReady.await()
                delay(32L)
                dataSource.initAssets()
                dataSource.syncSubscriptions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Main background initialization failed", error)
            }
        }
    }

    fun refreshUiSettings() {
        _uiState.update {
            it.copy(
                confirmRemove = dataSource.getConfirmRemove(),
                doubleColumnDisplay = dataSource.getDoubleColumnDisplay()
            )
        }
    }

    // ---------- Group & server loading ----------
    private suspend fun buildServersCache(guids: List<String>): List<ServersCache> =
        guids.mapNotNull { guid ->
            currentCoroutineContext().ensureActive()
            val profile = dataSource.decodeServerConfig(guid) ?: return@mapNotNull null
            val affiliation = dataSource.decodeAffiliationInfo(guid)
            ServersCache(
                guid = guid,
                profile = profile.copy(),
                testDelayMillis = affiliation?.testDelayMillis ?: 0L,
                testDelayString = affiliation?.getTestDelayString().orEmpty()
            )
        }

    private suspend fun loadGroup(
        groupId: String,
        forceRefresh: Boolean = false
    ): List<ServersCache> {
        val loadMutex = groupLoadMutexes.computeIfAbsent(groupId) { Mutex() }
        return loadMutex.withLock {
            if (!forceRefresh) {
                cacheMutex.withLock { groupDataCache[groupId]?.let { return@withLock it } }
            }
            val servers = buildServersCache(dataSource.getServerGuidList(groupId))
            currentCoroutineContext().ensureActive()
            cacheMutex.withLock { groupDataCache[groupId] = servers }
            servers
        }
    }

    private fun applyKeywordFilter(servers: List<ServersCache>): List<ServersCache> {
        val keyword = keywordFilter.trim()
        if (keyword.isEmpty()) return servers
        val regex = try {
            Regex(keyword, RegexOption.IGNORE_CASE)
        } catch (_: PatternSyntaxException) {
            return servers
        }
        return servers.filter { cache ->
            val profile = cache.profile
            profile.remarks.matchesPattern(regex, keyword) ||
                    profile.description.orEmpty().matchesPattern(regex, keyword) ||
                    profile.server.orEmpty().matchesPattern(regex, keyword) ||
                    profile.configType.name.matchesPattern(regex, keyword)
        }
    }

    private fun updateGroupUi(groupId: String, servers: List<ServersCache>) {
        mutableServersForGroup(groupId).value = applyKeywordFilter(servers)
    }

    fun getSubscriptions(): List<SubscriptionCache> = dataSource.getSubscriptions()

    fun getSubscriptionItem(groupId: String) = dataSource.getSubscriptionItem(groupId)

    private fun resolveSelectedGroup(groups: List<GroupMapItem>): String {
        val current = uiState.value.selectedGroupId
        val resolved = when {
            groups.isEmpty() -> ""
            groups.any { it.id == current } -> current
            else -> groups.first().id
        }
        if (resolved != current) {
            dataSource.setSelectedSubscriptionId(resolved)
        }
        return resolved
    }

    private fun radialPreloadOrder(groups: List<GroupMapItem>, selectedIndex: Int): List<String> {
        if (groups.isEmpty()) return emptyList()
        val result = ArrayList<String>((groups.size - 1).coerceAtLeast(0))
        for (distance in 1 until groups.size) {
            val right = selectedIndex + distance
            val left = selectedIndex - distance
            if (right in groups.indices) result += groups[right].id
            if (left in groups.indices) result += groups[left].id
        }
        return result
    }

    fun setupGroupTab(forceRefresh: Boolean = false): Job {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()

        return viewModelScope.launch(ioDispatcher) {
            try {
                if (forceRefresh) {
                    cacheMutex.withLock { groupDataCache.clear() }
                }
                val groups = dataSource.getSubscriptions().map {
                    GroupMapItem(id = it.guid, remarks = it.subscription.remarks)
                }
                val selectedGroup = resolveSelectedGroup(groups)
                val validIds = groups.mapTo(HashSet()) { it.id }
                groupPageFlows.keys.removeAll { it !in validIds }
                groupLoadMutexes.keys.removeAll { it !in validIds }

                _uiState.update {
                    it.copy(
                        groups = groups,
                        selectedGroupId = selectedGroup,
                        selectedGuid = dataSource.getSelectServer()
                    )
                }
                groups.forEach { mutableServersForGroup(it.id) }
                // Ensure manual group flow exists and is populated (independent of subscriptions)
                mutableServersForGroup(AppConfig.DEFAULT_SUBSCRIPTION_ID)

                if (groups.isEmpty()) {
                    // Still load manual servers even if no subscriptions
                    val manualServers = loadGroup(AppConfig.DEFAULT_SUBSCRIPTION_ID, forceRefresh)
                    updateGroupUi(AppConfig.DEFAULT_SUBSCRIPTION_ID, manualServers)
                    if (!initialPageReady.isCompleted) initialPageReady.complete(Unit)
                    return@launch
                }

                val selectedServers = loadGroup(selectedGroup, forceRefresh)
                updateGroupUi(selectedGroup, selectedServers)
                // Always keep manual list fresh for Main VPN section
                val manualServersInit = loadGroup(AppConfig.DEFAULT_SUBSCRIPTION_ID, forceRefresh)
                updateGroupUi(AppConfig.DEFAULT_SUBSCRIPTION_ID, manualServersInit)

                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }

                val selectedIndex =
                    groups.indexOfFirst { it.id == selectedGroup }.coerceAtLeast(0)
                val preloadOrder = radialPreloadOrder(groups, selectedIndex)
                preloadJob = viewModelScope.launch(preloadDispatcher) {
                    preloadOrder.forEach { groupId ->
                        ensureActive()
                        delay(32L)
                        val servers = loadGroup(groupId, forceRefresh)
                        updateGroupUi(groupId, servers)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set up group tabs", error)
            } finally {
                if (!initialPageReady.isCompleted) {
                    initialPageReady.complete(Unit)
                }
            }
        }.also { setupGroupJob = it }
    }

    // ---------- Business actions (coroutine-based) ----------
    private fun importBatchConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    // AmneziaWG configs (detected by Jc/Jmin/H1-H4/I1-I5 fields) are stored
                    // as-is and connected via the separate AmneziaWG engine, never through
                    // the normal VLESS/VMess/etc parsers.
                    if (com.narcic.ng.awg.AwgManager.isAmneziaWgConfig(configText)) {
                        val profile = com.narcic.ng.dto.entities.ProfileItem.create(
                            com.narcic.ng.enums.EConfigType.AMNEZIAWG
                        ).apply {
                            remarks = "AmneziaWG"
                            awgConfigText = com.narcic.ng.awg.AwgManager.sanitizeConfigText(configText)
                        }
                        dataSource.encodeServerConfig("", profile)
                        toast(dataSource.getString(R.string.title_import_config_count, 1))
                        setupGroupTab(forceRefresh = true)
                        return@withContext
                    }

                    // Legacy path: kept for narcic:// share-link import and file import.
                    // Force empty subId so it becomes manual (never inherits selected subscription).
                    val (count, countSub) = dataSource.importManualVpnConfig(configText)
                    val effectiveCount = if (count > 0) count else {
                        // If manual import yielded 0, maybe it was a subscription link - try subscription path only if caller explicitly wanted it?
                        // For backward compat, fallback to generic import with empty subId
                        dataSource.importBatchConfig(configText, "", true).first
                    }
                    when {
                        effectiveCount > 0 -> {
                            toast(dataSource.getString(R.string.title_import_config_count, effectiveCount))
                            setupGroupTab(forceRefresh = true)
                        }
                        countSub > 0 -> setupGroupTab(forceRefresh = true)
                        else -> toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importManualVpnConfig(configText: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    // Check if it's an AmneziaWG configuration first
                    if (com.narcic.ng.awg.AwgManager.isAmneziaWgConfig(configText)) {
                        val profile = com.narcic.ng.dto.entities.ProfileItem.create(
                            com.narcic.ng.enums.EConfigType.AMNEZIAWG
                        ).apply {
                            remarks = "AmneziaWG"
                            awgConfigText = com.narcic.ng.awg.AwgManager.sanitizeConfigText(configText)
                        }
                        dataSource.encodeServerConfig("", profile)
                        toast(dataSource.getString(R.string.title_import_config_count, 1))
                        cacheMutex.withLock { groupDataCache.remove(AppConfig.DEFAULT_SUBSCRIPTION_ID) }
                        setupGroupTab(forceRefresh = true)
                        return@withContext
                    }

                    val (count, _) = dataSource.importManualVpnConfig(configText)
                    if (count > 0) {
                        toast(dataSource.getString(R.string.title_import_config_count, count))
                        // Refresh manual group and all groups
                        cacheMutex.withLock { groupDataCache.remove(AppConfig.DEFAULT_SUBSCRIPTION_ID) }
                        setupGroupTab(forceRefresh = true)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import manual VPN config", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importVpnFromClipboard() {
        // Direct clipboard handled in Activity; this is placeholder for ViewModel-initiated clipboard
        // Not used; Activity reads clipboard and calls importManualVpnConfig
    }

    private fun importSubscriptionFromClipboard(text: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.createSubscriptionOnly("", text)
                    if (countSub > 0 || count > 0) {
                        toast("سابسکریپشن اضافه شد")
                        dataSource.syncSubscriptions()
                        setupGroupTab(forceRefresh = true).join()
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import subscription from clipboard", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importSubscriptionFromQr(text: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val (count, countSub) = dataSource.createSubscriptionOnly("", text)
                    if (countSub > 0 || count > 0) {
                        toast("سابسکریپشن اضافه شد")
                        dataSource.syncSubscriptions()
                        setupGroupTab(forceRefresh = true).join()
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import subscription from QR", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun importConfigViaSub(forceAll: Boolean = false) {
        // forceAll=true always fetches every enabled subscription
        // (dataSource.updateConfigViaSubAll), regardless of which tab is
        // active — used for the first-run bootstrap so all bundled default
        // subscriptions get pulled at once. The regular manual "fetch"
        // action still targets only the currently selected tab.
        val subId = if (forceAll) "" else uiState.value.selectedGroupId
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val result = if (subId.isEmpty()) {
                        dataSource.updateConfigViaSubAll()
                    } else {
                        val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                        dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    }
                    when {
                        result.successCount + result.failureCount + result.skipCount == 0 ->
                            toast(R.string.title_update_subscription_no_subscription)

                        result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                            toast(dataSource.getString(R.string.title_update_config_count, result.configCount))

                        else ->
                            toast(dataSource.getString(R.string.title_update_subscription_result, result.configCount, result.successCount, result.failureCount, result.skipCount))
                    }
                    if (result.configCount > 0) {
                        setupGroupTab(forceRefresh = true)
                        refreshSelectedGuid()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun exportAllAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val groupId = uiState.value.selectedGroupId
                    val list = if (groupId.isEmpty() && keywordFilter.isEmpty()) {
                        dataSource.getServerGuidList("")
                    } else {
                        currentServers().map { it.guid }
                    }
                    val ret = dataSource.shareNonCustomConfigsToClipboard(list)
                    if (ret > 0) {
                        toast(dataSource.getString(R.string.title_export_config_count, ret))
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Export failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeAllServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count =
                        if (uiState.value.selectedGroupId.isEmpty() && keywordFilter.isEmpty()) {
                            dataSource.removeAllServer()
                        } else {
                            val guids = currentServers().map { it.guid }
                            guids.forEach { dataSource.removeServer(it) }
                            guids.size
                        }
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                    }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete all failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeDuplicateServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val seen = HashSet<ProfileItem>()
                    val duplicates = ArrayList<String>()
                    currentServers().forEach { server ->
                        val profile = server.profile
                        if (!profile.configType.isComplexType()) {
                            val identity = profile.duplicateIdentity()
                            if (!seen.add(identity)) duplicates += server.guid
                        }
                    }
                    duplicates.forEach { dataSource.removeServer(it) }
                    setupGroupTab(forceRefresh = true)
                    toast(dataSource.getString(R.string.title_del_duplicate_config_count, duplicates.size))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete duplicate failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val count = removeInvalidServerInternal()
                    viewModelScope.launch(ioDispatcher) {
                        cacheMutex.withLock { groupDataCache.clear() }
                        setupGroupTab(forceRefresh = true)
                    }
                    toast(dataSource.getString(R.string.title_del_config_count, count))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Delete invalid failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun removeInvalidServerInternal(): Int {
        val visibleServersOnly =
            uiState.value.selectedGroupId.isNotEmpty() || keywordFilter.isNotBlank()
        return if (visibleServersOnly) {
            currentServers().sumOf { server ->
                dataSource.removeInvalidServerByGuid(server.guid)
            }
        } else {
            dataSource.removeInvalidServersInGroup("")
        }
    }

    private fun sortByTestResultsAsync() {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    sortByTestResultsInternal()
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Sort by test results failed", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    private fun sortByTestResultsInternal() {
        val subs = if (uiState.value.selectedGroupId.isEmpty()) {
            dataSource.getSubsList()
        } else {
            listOf(uiState.value.selectedGroupId)
        }
        subs.forEach { dataSource.sortByTestResultsForSub(it) }
    }

    fun subscriptionIdChanged(id: String) {
        if (_uiState.value.groups.none { it.id == id }) return
        mutableServersForGroup(id)
        if (uiState.value.selectedGroupId != id) {
            dataSource.setSelectedSubscriptionId(id)
            _uiState.update { it.copy(selectedGroupId = id) }
        }
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            try {
                updateGroupUi(id, loadGroup(id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to load selected group: $id", error)
            }
        }
    }

    fun reloadServerList() {
        val groupId = uiState.value.selectedGroupId
        selectedGroupLoadJob?.cancel()
        selectedGroupLoadJob = viewModelScope.launch(ioDispatcher) {
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
        }
    }

    fun reloadAllGroups(groupIds: List<String>) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(preloadDispatcher) {
            val selected = uiState.value.selectedGroupId
            val order = buildList {
                if (selected in groupIds) add(selected)
                addAll(groupIds.filter { it != selected })
                // Always refresh manual group for Main VPN section
                if (AppConfig.DEFAULT_SUBSCRIPTION_ID !in groupIds) add(AppConfig.DEFAULT_SUBSCRIPTION_ID)
            }
            order.forEachIndexed { index, groupId ->
                ensureActive()
                if (index > 0) delay(32L)
                updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) return
        keywordFilter = keyword
        filterJob?.cancel()
        filterJob = viewModelScope.launch(defaultDispatcher) {
            delay(300L)
            val snapshot = cacheMutex.withLock { groupDataCache.toMap() }
            ensureActive()
            snapshot.forEach { (groupId, servers) ->
                ensureActive()
                updateGroupUi(groupId, servers)
            }
        }
    }

    fun updateSelectedGuid(guid: String) {
        dataSource.setSelectServer(guid)
        _uiState.update { it.copy(selectedGuid = guid, livePingMillis = null) }
    }

    /**
     * A user tap on a specific server row in the main list. Unlike
     * updateSelectedGuid() alone, this also turns off "auto-connect to best
     * server" (autoConnection) -- otherwise the tap only changes which row
     * is highlighted, but the next Connect press still runs autoConnect()
     * and silently reconnects to the lowest-ping server instead of the one
     * the user just picked.
     */
    fun selectServerManually(guid: String) {
        if (uiState.value.autoConnection) setAutoConnection(false)
        updateSelectedGuid(guid)
    }

    fun refreshSelectedGuid() {
        _uiState.update { it.copy(selectedGuid = dataSource.getSelectServer()) }
    }

    fun removeServerAndRefresh(guid: String) {
        if (guid == uiState.value.selectedGuid) {
            toast(R.string.toast_action_not_allowed)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            dataSource.removeServer(guid)
            cacheMutex.withLock { groupDataCache.clear() }
            setupGroupTab(forceRefresh = true).join()
        }
    }

    fun moveServer(groupId: String, fromPosition: Int, toPosition: Int) {
        val servers = mutableServersForGroup(groupId).value.toMutableList()
        if (!servers.moveItem(fromPosition, toPosition)) return
        val guids = servers.map { it.guid }
        mutableServersForGroup(groupId).value = servers
        // A drag emits several moves; serialize writes so an older order cannot overwrite a newer one.
        val previousPersistenceJob = serverOrderPersistenceJobs[groupId]
        serverOrderPersistenceJobs[groupId] = viewModelScope.launch(ioDispatcher) {
            previousPersistenceJob?.join()
            dataSource.encodeServerList(guids, groupId)
            cacheMutex.withLock { groupDataCache[groupId] = servers }
        }
    }

    // ---------- Testing ----------
    fun cancelAllPing() {
        dataSource.cancelAllPing()
        testingGroupId = null
        _uiState.update {
            it.copy(
                isTesting = false,
                statusText = if (it.isRunning) connectedText else disconnectedText
            )
        }
    }

    fun testAllRealPing(onlyTcp: Boolean = false) {
        dataSource.cancelAllPing()
        val hasFilter = keywordFilter.isNotEmpty()
        val groupId = uiState.value.selectedGroupId

        // With no active search filter, "test all" spans every subscription
        // — not just the active tab — including any subscription added
        // later, since getServerGuidList("") resolves to the merged list of
        // servers across all subscriptions. An active filter narrows the
        // test to the matching servers within the currently viewed tab.
        val guids = if (hasFilter) {
            currentServers().map { it.guid }
        } else {
            dataSource.getServerGuidList("")
        }

        dataSource.clearAllTestDelayResults(guids)
        if (guids.isEmpty()) {
            _uiState.update { it.copy(isTesting = false) }
            return
        }
        testingGroupId = if (hasFilter) groupId else null
        _uiState.update {
            it.copy(
                isTesting = true,
                statusText = dataSource.getString(R.string.connection_test_testing)
            )
        }
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock {
                if (hasFilter) groupDataCache.remove(groupId) else groupDataCache.clear()
            }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = if (hasFilter) groupId else "",
                    serverGuids = if (hasFilter) guids else emptyList(),
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    /**
     * "تست" inside a subscription's connection picker (image 4/8): real-ping
     * only the configs that belong to [groupId]. Unlike [testAllRealPing],
     * this always stays scoped to that one subscription — it never spans
     * every subscription — which is what makes خودکار (AutoConnect) fast on
     * subscriptions with many configs (see [autoConnect]).
     */
    fun testGroupRealPing(groupId: String, onlyTcp: Boolean = false) {
        dataSource.cancelAllPing()
        val guids = dataSource.getServerGuidList(groupId)
        dataSource.clearAllTestDelayResults(guids)
        if (guids.isEmpty()) {
            _uiState.update { it.copy(isTesting = false) }
            pendingAutoConnect = false
            return
        }
        testingGroupId = groupId
        _uiState.update {
            it.copy(
                isTesting = true,
                statusText = dataSource.getString(R.string.connection_test_testing)
            )
        }
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.remove(groupId) }
            dataSource.sendMsg2TestService(
                TestServiceMessage(
                    key = AppConfig.MSG_MEASURE_CONFIG_START,
                    subscriptionId = groupId,
                    serverGuids = guids,
                    onlyTcp = onlyTcp
                )
            )
        }
    }

    fun testCurrentServerRealPing() {
        _uiState.update {
            it.copy(
                statusText = dataSource.getString(R.string.connection_test_testing)
            )
        }
        dataSource.testCurrentServerRealPing()
    }

    /**
     * When خودکار (AutoConnect) is running a scoped real-ping test with a
     * ping limit configured (تنظیم اتصال خودکار), stop testing and connect
     * the moment any config comes back at/under that limit — instead of
     * always waiting for every config in the subscription to finish, which
     * can take a long time on subscriptions with many configs.
     */
    private fun checkAutoConnectEarlyStop(guid: String) {
        if (!pendingAutoConnect) return
        val thresholdMillis = pendingAutoConnectThresholdMillis
        if (thresholdMillis <= 0L) return
        val delayMillis = dataSource.decodeAffiliationInfo(guid)?.testDelayMillis ?: return
        if (delayMillis !in 1..thresholdMillis) return

        pendingAutoConnect = false
        dataSource.cancelAllPing()
        testingGroupId = null
        val groupId = uiState.value.selectedGroupId
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.remove(groupId) }
            updateGroupUi(groupId, loadGroup(groupId, forceRefresh = true))
            _uiState.update {
                it.copy(
                    isTesting = false,
                    statusText = if (it.isRunning) connectedText else disconnectedText
                )
            }
            requestAutoConnect(guid)
        }
    }

    private fun onTestsFinished() {
        viewModelScope.launch(ioDispatcher) {
            cacheMutex.withLock { groupDataCache.clear() }
            testingGroupId = null
            val groupId = uiState.value.selectedGroupId
            _uiState.update {
                it.copy(
                    isTesting = false,
                    statusText = if (it.isRunning) connectedText else disconnectedText
                )
            }
            if (pendingAutoConnect) {
                pendingAutoConnect = false
                val freshServers = loadGroup(groupId, forceRefresh = true)
                updateGroupUi(groupId, freshServers)
                val best = freshServers.filter { it.testDelayMillis > 0L }.minByOrNull { it.testDelayMillis }
                if (best != null) {
                    requestAutoConnect(best.guid)
                } else {
                    toast("سرور مناسبی برای اتصال خودکار پیدا نشد")
                }
            }
            reloadAllGroups(_uiState.value.groups.map { it.id })
        }
    }

    // ---------- Auto connect ----------
    /**
     * Picks the fastest already-tested server in the current group and
     * requests a real connect. If nothing has been tested yet, runs a real
     * ping test — scoped only to the current subscription, never every
     * subscription — and picks the best result once it finishes.
     *
     * If a ping limit is configured (تنظیم اتصال خودکار), the test stops
     * and connects as soon as any config comes back at/under that limit
     * instead of waiting for the whole subscription to finish testing, so
     * خودکار stays fast even on subscriptions with many configs.
     *
     * When موقعیت (location) has a country pinned (uiState.locationFlag),
     * only servers whose remarks start with that flag are considered — so
     * "خودکار" stays scoped to the chosen country instead of picking any
     * fastest server in the group.
     */
    fun autoConnect() {
        if (uiState.value.isRunning || uiState.value.isTesting) return
        val servers = currentServersForLocationFilter()
        if (servers.isEmpty()) {
            toast(
                if (uiState.value.locationFlag.isEmpty()) "ابتدا یک سرور اضافه کنید"
                else "کانفیگی با این موقعیت پیدا نشد"
            )
            return
        }
        val best = servers.filter { it.testDelayMillis > 0L }.minByOrNull { it.testDelayMillis }
        if (best != null) {
            requestAutoConnect(best.guid)
        } else {
            pendingAutoConnect = true
            pendingAutoConnectThresholdMillis = dataSource.getAutoConnectPingLimitMillis()
            testGroupRealPing(uiState.value.selectedGroupId)
        }
    }

    /** currentServers(), optionally narrowed to the pinned location flag. */
    private fun currentServersForLocationFilter(): List<ServersCache> {
        val flag = uiState.value.locationFlag
        val servers = currentServers()
        if (flag.isEmpty()) return servers
        return servers.filter {
            CountryFlags.extractFlag(it.profile.remarks) == flag
        }
    }

    private fun requestAutoConnect(guid: String) {
        updateSelectedGuid(guid)
        _uiState.update { it.copy(autoConnectRequest = it.autoConnectRequest + 1) }
    }

    // ---------- Home "VPN" card: location / connection / subscription ----------
    private fun setLocationFilter(flag: String) {
        dataSource.setLocationFlag(flag)
        _uiState.update { it.copy(locationFlag = flag) }
    }

    private fun setAutoConnection(auto: Boolean) {
        dataSource.setAutoConnection(auto)
        _uiState.update { it.copy(autoConnection = auto) }
    }

    /** User picked one specific server in the connection picker (image 4/8). */
    private fun setManualConnection(guid: String) {
        setAutoConnection(false)
        updateSelectedGuid(guid)
        if (uiState.value.isRunning) {
            _uiState.update { it.copy(autoConnectRequest = it.autoConnectRequest + 1) }
        }
    }

    /** "گزینه‌ها" → "تازه‌سازی": refresh one subscription regardless of the active tab. */
    private fun refreshSubscription(subId: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val item = dataSource.getSubscriptionItem(subId) ?: return@withContext
                    val result = dataSource.updateConfigViaSub(SubscriptionCache(subId, item))
                    if (result.successCount > 0) {
                        toast(dataSource.getString(R.string.title_update_config_count, result.configCount))
                        cacheMutex.withLock { groupDataCache.remove(subId) }
                        // Wait for the group list/state to actually refresh before
                        // isLoading flips back off, so the UI never looks "stuck".
                        setupGroupTab(forceRefresh = true).join()
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to refresh subscription: $subId", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    /** "+ افزودن سابسکریپشن" (image 9): subscription URLs only - strict separation. */
    private fun addSubscriptionFromText(name: String, content: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    // Strict: subscription section must NOT import ordinary VPN configs as subscriptions
                    val (count, countSub) = dataSource.createSubscriptionOnly(name, content)
                    if (countSub > 0) {
                        toast("سابسکریپشن اضافه شد")
                    } else if (count > 0) {
                        // Should not happen for strict, but handle
                        toast(dataSource.getString(R.string.title_import_config_count, count))
                    } else {
                        toastError(R.string.toast_failure)
                        return@withContext
                    }
                    // Schedules the 12h auto-update task — needed immediately when
                    // this activated one of the default Narcic repos (autoUpdate
                    // just flipped true) or created a new customer subscription.
                    dataSource.syncSubscriptions()
                    // Awaited on purpose: setupGroupTab launches its own fire-and-forget
                    // coroutine, so without join() isLoading could flip back to false —
                    // and the "+ افزودن" dialog close — before uiState.groups actually
                    // contains the new subscription, making it look like it never showed up.
                    setupGroupTab(forceRefresh = true).join()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to add subscription", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    /** "سابسکریپشن‌ها" → ویرایش: rename a subscription and/or change its URL. */
    private fun editSubscription(groupId: String, name: String, url: String) {
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    val ok = dataSource.updateSubscription(groupId, name, url)
                    if (ok) {
                        cacheMutex.withLock { groupDataCache.remove(groupId) }
                        setupGroupTab(forceRefresh = true).join()
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to edit subscription: $groupId", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    /** "سابسکریپشن‌ها" → "گزینه‌ها" → "حذف": delete a subscription and its configs. */
    private fun removeSubscriptionGroup(groupId: String) {
        if (groupId.isEmpty()) return // "all configs" filter card — nothing to delete
        launchLoading {
            withContext(ioDispatcher) {
                try {
                    dataSource.removeSubscription(groupId)
                    cacheMutex.withLock { groupDataCache.clear() }
                    setupGroupTab(forceRefresh = true).join()
                    toastSuccess(R.string.toast_success)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to remove subscription: $groupId", e)
                    toastError(R.string.toast_failure)
                }
            }
        }
    }

    fun triggerLocateSelectedServer() {
        val selected = dataSource.getSelectServer() ?: return
        val profile = dataSource.decodeServerConfig(selected) ?: return
        val groupId = profile.subscriptionId
        val groupIndex =
            _uiState.value.groups.indexOfFirst { it.id == groupId }.takeIf { it >= 0 } ?: return
        viewModelScope.launch(ioDispatcher) {
            val position =
                loadGroup(groupId).indexOfFirst { it.guid == selected }.takeIf { it >= 0 }
                    ?: return@launch
            _uiState.update {
                it.copy(locateTarget = LocateTarget(groupId, groupIndex, position))
            }
        }
    }

    fun getPosition(guid: String): Int = currentServers().indexOfFirst { it.guid == guid }

    /**
     * Looks up a server by GUID across every currently loaded group — not
     * just the one being viewed. Used by the home "اتصال" row, since the
     * manually/auto-selected server can belong to a different subscription
     * than whichever tab is on screen.
     */
    fun findServerCache(guid: String?): ServersCache? {
        if (guid.isNullOrEmpty()) return null
        groupPageFlows.values.forEach { flow ->
            flow.value.firstOrNull { it.guid == guid }?.let { return it }
        }
        return null
    }

    private fun consumeLocateTarget(target: LocateTarget) {
        _uiState.update { state ->
            if (state.locateTarget == target) state.copy(locateTarget = null) else state
        }
    }

    // ---------- Running state ----------
    /** Public entry point for connection paths outside the normal V2ray broadcast
     *  flow (currently: the AmneziaWG engine), to keep the UI in sync. */
    fun setExternalRunningState(running: Boolean) {
        updateRunningState(running)
    }

    /** Sets the full-screen loading state while AWG is doing smart auto-retries in the background */
    fun setAwgConnectingState(isConnecting: Boolean, message: String = "") {
        _uiState.update { state ->
            state.copy(
                isAwgConnecting = isConnecting,
                awgConnectingMessage = message
            )
        }
    }

    private fun updateRunningState(running: Boolean, clearTestingText: Boolean = true) {
        _uiState.update { state ->
            state.copy(
                isRunning = running,
                statusText = if (!clearTestingText && state.isTesting) state.statusText
                else if (running) connectedText else disconnectedText,
                livePingMillis = null
            )
        }
        if (running) {
            startConnectionTimer()
            startRemoteIpLookup()
            startAwgTrafficStatsIfNeeded()
        } else {
            stopConnectionStats()
        }
    }

    private var awgStatsJob: Job? = null

    /**
     * AmneziaWG connections never go through CoreVpnService, so the normal downloadBps/uploadBps
     * events (fed from Xray-core's own traffic broadcast) never fire for them — that's why
     * upload/download always showed blank while connected via AmneziaWG. This polls
     * org.amnezia.awg's own Statistics API directly and derives a bytes/sec rate from the delta
     * between polls, the same way the V2Ray side derives its speed text.
     */
    private fun startAwgTrafficStatsIfNeeded() {
        if (awgStatsJob?.isActive == true) return
        awgStatsJob = viewModelScope.launch(ioDispatcher) {
            var lastRx = -1L
            var lastTx = -1L
            var lastElapsed = SystemClock.elapsedRealtime()
            while (isActive && uiState.value.isRunning) {
                if (com.narcic.ng.awg.AwgManager.isRunning()) {
                    val stats = com.narcic.ng.awg.AwgManager.getStatistics()
                    if (stats != null) {
                        val now = SystemClock.elapsedRealtime()
                        val rx = stats.totalRx()
                        val tx = stats.totalTx()
                        val elapsedSeconds = ((now - lastElapsed).coerceAtLeast(1L)) / 1000.0
                        if (lastRx >= 0 && lastTx >= 0) {
                            val downBps = ((rx - lastRx).coerceAtLeast(0L) / elapsedSeconds).toLong()
                            val upBps = ((tx - lastTx).coerceAtLeast(0L) / elapsedSeconds).toLong()
                            _uiState.update {
                                it.copy(
                                    downloadSpeedText = downBps.toSpeedString(),
                                    uploadSpeedText = upBps.toSpeedString()
                                )
                            }
                        }
                        lastRx = rx
                        lastTx = tx
                        lastElapsed = now
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun startConnectionTimer() {
        if (connectionTimerJob?.isActive == true) return
        connectStartElapsedRealtime = SystemClock.elapsedRealtime()
        connectionTimerJob = viewModelScope.launch {
            while (isActive) {
                val elapsedSeconds =
                    (SystemClock.elapsedRealtime() - connectStartElapsedRealtime) / 1000L
                _uiState.update { it.copy(connectionDurationText = formatDuration(elapsedSeconds)) }
                delay(1000L)
            }
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    /**
     * Fetches the tunnel's exit IP/country once shortly after connecting,
     * retrying on failure (e.g. tunnel still settling) until it succeeds or
     * the connection drops. Reuses the same IP-info lookup already used by
     * the "Test Speed" flow, just surfaced in the main UI as well.
     */
    private fun startRemoteIpLookup() {
        if (ipLookupJob?.isActive == true) return
        ipLookupJob = viewModelScope.launch(ioDispatcher) {
            delay(1500L)
            while (isActive && uiState.value.isRunning) {
                val info = runCatching { SpeedtestManager.getRemoteIPInfoDetailed() }.getOrNull()
                if (info != null) {
                    val countryName = info.countryCode?.let { code ->
                        runCatching { Locale("", code).displayCountry }.getOrNull()
                            ?.takeUnless { it.isBlank() || it.equals(code, ignoreCase = true) }
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            remoteIp = info.ip,
                            remoteCountryCode = info.countryCode.orEmpty(),
                            remoteCountryName = countryName
                        )
                    }
                    break
                }
                delay(5000L)
            }
        }
    }

    private fun stopConnectionStats() {
        connectionTimerJob?.cancel()
        connectionTimerJob = null
        ipLookupJob?.cancel()
        ipLookupJob = null
        awgStatsJob?.cancel()
        awgStatsJob = null
        _uiState.update {
            it.copy(
                connectionDurationText = "",
                downloadSpeedText = "",
                uploadSpeedText = "",
                remoteIp = "",
                remoteCountryName = "",
                remoteCountryCode = ""
            )
        }
    }

    override fun onCleared() {
        setupGroupJob?.cancel()
        preloadJob?.cancel()
        selectedGroupLoadJob?.cancel()
        reloadJob?.cancel()
        filterJob?.cancel()
        connectionTimerJob?.cancel()
        ipLookupJob?.cancel()
        awgStatsJob?.cancel()
        cancelAllPing()
        dataSource.close()
        super.onCleared()
    }

    // ---------- Factory ----------
    class Factory(private val application: Application, private val dataSource: MainDataSource) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, dataSource) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
