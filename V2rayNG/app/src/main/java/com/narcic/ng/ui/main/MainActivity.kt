package com.narcic.ng.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import com.narcic.ng.AngApplication
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.core.LauncherManager
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.enums.PermissionType
import com.narcic.ng.extension.toast
import com.narcic.ng.extension.toastError
import com.narcic.ng.extension.toastSuccess
import com.narcic.ng.handler.AngConfigManager
import com.narcic.ng.handler.DefaultConfigSource
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.handler.SettingsChangeManager
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.ui.base.HelperBaseComponentActivity
import com.narcic.ng.ui.server.ProfileEditorResult
import com.narcic.ng.ui.server.ServerAmneziaWgActivity
import com.narcic.ng.ui.server.ServerCustomConfigActivity
import com.narcic.ng.ui.server.ServerGroupActivity
import com.narcic.ng.ui.server.ServerHttpActivity
import com.narcic.ng.ui.server.ServerHysteria2Activity
import com.narcic.ng.ui.server.ServerProxyChainActivity
import com.narcic.ng.ui.server.ServerShadowsocksActivity
import com.narcic.ng.ui.server.ServerSocksActivity
import com.narcic.ng.ui.server.ServerTrojanActivity
import com.narcic.ng.ui.server.ServerVlessActivity
import com.narcic.ng.ui.server.ServerVmessActivity
import com.narcic.ng.ui.server.ServerWireguardActivity
import com.narcic.ng.ui.settings.SettingsHubActivity
import com.narcic.ng.ui.statistics.StatisticsActivity
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService && mainViewModel.uiState.value.isRunning) {
                restartV2Ray()
            }
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService && mainViewModel.uiState.value.isRunning) restartV2Ray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the bundled Narcic subscription entries in place, but do NOT
        // auto-fetch/import them on first install. Importing configs and the
        // periodic 12h auto-update are handled by SubscriptionUpdater.sync()
        // (invoked below via MainAction.Initialize), which only activates
        // once the customer has added a subscription of their own.
        DefaultConfigSource.ensureSubscriptionExists()
        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        MainScreen(
            mainViewModel = mainViewModel,
            onMinimize = { moveTaskToBack(false) },
            onAction = { action ->
                when (action) {
                    MainAction.ToggleService -> handleFabAction()
                    MainAction.TestCurrentServer -> handleLayoutTestClick()
                    MainAction.ImportQRcode -> importVpnQRcode()
                    MainAction.ImportClipboard -> importClipboard()
                    MainAction.ImportConfigLocal -> importConfigLocal()
                    is MainAction.ImportManually -> importManually(action.type)
                    MainAction.RestartService -> restartV2Ray()
                    MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
                    is MainAction.SelectServer -> setSelectServer(action.guid)
                    is MainAction.EditServer -> editServer(action.guid, action.profile)
                    is MainAction.ShareClipboard -> shareToClipboard(action.guid)
                    is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
                    is MainAction.ShareLink -> shareLink(action.guid)
                    is MainAction.ShareQRCode -> shareQRCode(action.guid)
                    MainAction.ImportVpnFromClipboard -> importVpnFromClipboard()
                    is MainAction.ImportVpnConfig -> importManualVpnConfig(action.configText)
                    is MainAction.ImportSubscriptionFromClipboard -> mainViewModel.onAction(action)
                    is MainAction.ImportSubscriptionFromQr -> mainViewModel.onAction(action)
                    else -> mainViewModel.onAction(action)
                }
            },
            onNavigate = { route -> navigateTo(route) },
        )
    }

    private fun shareToClipboard(guid: String) {
        // Was previously fire-and-forget with no user feedback at all --
        // the config was copied but nothing on screen told the user it worked.
        if (AngConfigManager.share2Clipboard(this, guid) == 0) {
            toastSuccess(R.string.toast_success)
        } else {
            toastError(R.string.toast_failure)
        }
    }

    private fun shareLink(guid: String) {
        try {
            val configString = AngConfigManager.shareConfig(guid)
            val link = generateShareLink(configString)
            Utils.setClipboard(this, link)
            toast(R.string.toast_link_copied)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to share config as link", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun generateShareLink(configString: String): String {
        val encoded = java.net.URLEncoder.encode(configString, "UTF-8")
        return "narcic://config/$encoded"
    }

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun navigateTo(destination: String) {
        when (destination) {
            "settings" -> settingsActivityLauncher.launch(Intent(this, SettingsHubActivity::class.java))
            "statistics" -> startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }

    private fun handleFabAction() {
        val selectedGuid = mainViewModel.uiState.value.selectedGuid
        val selectedProfile = selectedGuid?.let { MmkvManager.decodeServerConfig(it) }

        if (selectedProfile?.configType == com.narcic.ng.enums.EConfigType.AMNEZIAWG) {
            handleAwgToggle(selectedProfile)
            return
        }

        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            proceedToConnect()
        }
    }

    /** Separate connect/disconnect path for AmneziaWG configs — never touches
     *  CoreVpnService/Xray-core, uses the AmneziaWG engine's own VpnService instead. */
    /** Separate connect/disconnect path for AmneziaWG configs — never touches
     *  CoreVpnService/Xray-core, uses the AmneziaWG engine's own VpnService instead.
     *
     *  IMPORTANT: AwgManager.connect() and disconnect() are blocking calls (sleep + I/O).
     *  They MUST run on Dispatchers.IO. The UI state is optimistically updated on the
     *  main thread before the background work begins so the button feels instant;
     *  the state is corrected if the operation actually fails. */
    private fun handleAwgToggle(profile: ProfileItem) {
        if (com.narcic.ng.awg.AwgManager.isRunning()) {
            // Optimistic: show disconnected immediately so UI feels responsive
            mainViewModel.setExternalRunningState(false)
            lifecycleScope.launch(Dispatchers.IO) {
                val error = com.narcic.ng.awg.AwgManager.disconnect()
                if (error != null) {
                    withContext(Dispatchers.Main) {
                        // Rollback: disconnect failed
                        mainViewModel.setExternalRunningState(true)
                        toast(error)
                    }
                }
            }
            return
        }

        val configText = profile.awgConfigText
        if (configText.isNullOrBlank()) {
            toast("AmneziaWG config is empty")
            return
        }

        val intent = com.narcic.ng.awg.AwgManager.prepare(this)
        if (intent != null) {
            awgPermissionLauncher.launch(intent)
            pendingAwgConfigText = configText
        } else {
            startAwgTunnel(configText)
        }
    }

    private var pendingAwgConfigText: String? = null

    private val awgPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val configText = pendingAwgConfigText
        pendingAwgConfigText = null
        if (result.resultCode == RESULT_OK && configText != null) {
            startAwgTunnel(configText)
        } else {
            toast(R.string.home_permission_denied)
        }
    }

    /**
     * Starts the AWG tunnel on a background thread.
     * AwgManager.connect() is blocking (retries with sleep) — must NOT run on Main thread.
     * State is optimistically set to true before the call; rolled back on failure.
     */
    private fun startAwgTunnel(configText: String) {
        // Optimistic update so button responds immediately
        mainViewModel.setExternalRunningState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val error = com.narcic.ng.awg.AwgManager.connect(this@MainActivity, configText)
            withContext(Dispatchers.Main) {
                if (error != null) {
                    mainViewModel.setExternalRunningState(false)
                    toast(error)
                }
                // error == null -> already optimistically true, nothing to do
            }
        }
    }
    private fun proceedToConnect() {
        if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.uiState.value.isRunning) LauncherManager.stopService(this)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            startV2Ray()
        }
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            // Manual configurations should have empty subscriptionId, not the selected group ID
            // This ensures edit/pencil and share actions are visible for manual configs
            putExtra("subscriptionId", "")
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    // Main VPN QR: VPN config -> manual (subscriptionId = "")
    private fun importVpnQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                // Validate is VPN config, reject subscription URLs for this flow
                if (Utils.isValidSubUrl(scanResult.trim()) && !isVpnConfig(scanResult)) {
                    toastError(R.string.toast_failure)
                } else {
                    mainViewModel.onAction(MainAction.ImportVpnConfig(scanResult))
                }
            }
        }
    }

    // Subscription QR: subscription URL only
    private fun importSubscriptionQRcode(scanResult: String) {
        // Strict: only subscription URLs
        if (isVpnConfig(scanResult) || !Utils.isValidSubUrl(scanResult.trim())) {
            toastError(R.string.toast_failure)
            return
        }
        mainViewModel.onAction(MainAction.ImportSubscriptionFromQr(scanResult))
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importVpnFromClipboard() {
        try {
            val text = Utils.getClipboard(this)
            if (text.isBlank()) {
                toast(R.string.toast_none_data_clipboard)
                return
            }
            if (text.trim().lines().all { Utils.isValidSubUrl(it.trim()) } && !isVpnConfig(text)) {
                toastError(R.string.toast_failure)
                return
            }
            mainViewModel.onAction(MainAction.ImportVpnConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import VPN config from clipboard", e)
            toastError(R.string.toast_failure)
        }
    }

    private fun importManualVpnConfig(configText: String) {
        // Direct manual import with empty subscriptionId, never inherits selected group
        mainViewModel.onAction(MainAction.ImportVpnConfig(configText))
    }

    private fun isVpnConfig(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val schemes = listOf(
            AppConfig.VMESS, AppConfig.SHADOWSOCKS, AppConfig.SOCKS, AppConfig.VLESS,
            AppConfig.TROJAN, AppConfig.WIREGUARD, AppConfig.HYSTERIA2, AppConfig.HY2,
            "ss://", "vmess://", "vless://", "trojan://", "wireguard://", "socks://", "socks4://", "socks5://", "hysteria2://", "hy2://", "tuic://", "hysteria://"
        )
        return schemes.any { trimmed.startsWith(it, ignoreCase = true) } ||
            (trimmed.contains("inbounds") && trimmed.contains("outbounds")) ||
            trimmed.startsWith("[Interface]") ||
            trimmed.startsWith("v2rayn://")
    }

    private fun shareQRCode(guid: String) {
        mainViewModel.onAction(MainAction.ShareQRCode(guid))
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    mainViewModel.onAction(MainAction.ImportBatchConfig(reader.readText()))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != android.content.Intent.ACTION_VIEW) return
        
        val data = intent.data
        if (data == null || !data.scheme.equals("narcic", ignoreCase = true)) return
        
        val encodedConfig = data.path ?: return
        if (!encodedConfig.startsWith("/config/")) return
        
        val configString = encodedConfig.substring("/config/".length)
        val decodedConfig = try {
            java.net.URLDecoder.decode(configString, "UTF-8")
        } catch (e: Exception) {
            toastError(R.string.toast_invalid_link)
            return
        }
        
        // Share link is always VPN config -> manual
        mainViewModel.onAction(MainAction.ImportVpnConfig(decodedConfig))
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.AMNEZIAWG -> ServerAmneziaWgActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            // Manual configurations should have empty subscriptionId, not the selected group ID
            // This ensures edit/pencil and share actions are visible for manual configs
            putExtra("subscriptionId", "")
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.selectServerManually(guid)
            if (mainViewModel.uiState.value.isRunning) restartV2Ray()
        }
    }

}
