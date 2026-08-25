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
                    MainAction.ImportQRcode -> importQRcode()
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
                    else -> mainViewModel.onAction(action)
                }
            },
            onNavigate = { route -> navigateTo(route) },
        )
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

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
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            proceedToConnect()
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
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
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

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
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
        
        mainViewModel.onAction(MainAction.ImportBatchConfig(decodedConfig))
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
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            if (mainViewModel.uiState.value.isRunning) restartV2Ray()
        }
    }

}
