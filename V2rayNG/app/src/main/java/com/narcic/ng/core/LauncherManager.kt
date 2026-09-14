package com.narcic.ng.core

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.extension.toast
import com.narcic.ng.extension.toastError
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.helper.MessageHelper
import com.narcic.ng.root.RootManager
import com.narcic.ng.service.CoreProxyOnlyService
import com.narcic.ng.service.CoreRootService
import com.narcic.ng.service.CoreVpnService
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils

object LauncherManager {

    fun startServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        if (!releaseForV2RayBlocking(context)) return false
        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
            return false
        }
        return true
    }

    fun startService(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startService from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        if (!releaseForV2RayBlocking(context)) return

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Same as [startService], but awaits the engine handoff on the caller's
     * coroutine instead of blocking the thread. Use from UI code (main thread)
     * so a bounded settle wait can never ANR.
     */
    suspend fun startServiceAwaitingHandoff(context: Context, guid: String? = null) {
        LogUtil.i(AppConfig.TAG, "LauncherManager: startServiceAwaitingHandoff from ${context::class.java.simpleName}")

        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }

        val handoffError = EngineHandoff.releaseFor(EngineHandoff.Engine.V2RAY, context)
        if (handoffError != null) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: engine handoff failed: $handoffError")
            context.toast(handoffError)
            return
        }

        try {
            startContextService(context)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: ${e.message}", e)
            context.toast(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Blocking bounded handoff for thread-based callers (tiles, receivers, daemon). */
    private fun releaseForV2RayBlocking(context: Context): Boolean {
        val handoffError = EngineHandoff.releaseForBlocking(EngineHandoff.Engine.V2RAY, context)
        if (handoffError != null) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: engine handoff failed: $handoffError")
            context.toast(handoffError)
            return false
        }
        return true
    }

    fun stopService(context: Context) {
        //context.toast(R.string.toast_services_stop)
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    @Throws(Exception::class)
    private fun startContextService(context: Context) {
        // VPN engines are mutually exclusive (one TUN interface per app) and
        // the handoff (bounded stop + settle of Aether/AWG) has already run in
        // the callers above before reaching here.

        // Note: isRunning check is removed here to avoid loading Native libraries in the UI process.
        // The check is performed in CoreServiceManager when the service starts in the daemon process.

        val guid = MmkvManager.getSelectServer()
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: No server selected")
                error(context.getString(R.string.app_tile_first_use))
            }

        val config = MmkvManager.decodeServerConfig(guid)
            ?: run {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Failed to decode server config")
                error(context.getString(R.string.toast_config_file_invalid))
            }

        if (!config.configType.isComplexType()
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Invalid server configuration")
            error(context.getString(R.string.toast_config_file_invalid))
        }

        SettingsManager.refreshRuntimeSocksPort()

        if (config.insecure == true && config.pinnedCA256.isNullOrEmpty()) {
            context.toastError(R.string.toast_allow_insecure_deprecated)
            Utils.setClipboard(context, context.getString(R.string.toast_allow_insecure_deprecated))
        }

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }

        val isRootMode = SettingsManager.isRootMode()
        if (isRootMode && !RootManager.isRootAvailable()) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: root mode requires root but none available")
            error(context.getString(R.string.toast_root_required))
        }

        val intent = if (isRootMode) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Root service")
            Intent(context.applicationContext, CoreRootService::class.java)
        } else if (SettingsManager.isVpnMode()) {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting VPN service")
            Intent(context.applicationContext, CoreVpnService::class.java)
        } else {
            LogUtil.i(AppConfig.TAG, "LauncherManager: Starting Proxy service")
            Intent(context.applicationContext, CoreProxyOnlyService::class.java)
        }

        // Engine handoff liveness flag: set BEFORE the service starts so a
        // concurrent handoff from another engine sees V2Ray as owning (or
        // about to own) the TUN — CoreServiceManager keeps it true after a
        // successful start and clears it on stop.
        EngineHandoff.setV2RayAlive(true)

        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "LauncherManager: Missing permission to start foreground service", e)
            throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException"
            ) {
                LogUtil.e(AppConfig.TAG, "LauncherManager: Foreground service start not allowed", e)
                throw IllegalStateException(e.message ?: e.javaClass.simpleName, e)
            }
            throw e
        }
    }
}
