package com.narcic.ng.aether.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.narcic.ng.aether.shared.model.AppInfo

// Ported from AetherST (KMP) to plain Android — no expect/actual, this app
// ships Android only, so the "actual" implementations in Platform.android.kt
// are declared directly here as regular declarations.
class PlatformContext(val context: android.content.Context)

interface VpnController {
    fun startVpn()
    fun stopVpn()
    fun restartVpn()
    fun startProxy()
    fun stopProxy()
    fun restartProxy()
    fun prepareVpn(onPermissionRequired: () -> Unit): Boolean
    fun isVpnPrepared(): Boolean
    fun submitLoginCode(code: String)
}

interface TrafficProvider {
    fun getTxBytes(): Long
    fun getRxBytes(): Long
}

interface AppInfoProvider {
    suspend fun getInstalledApps(): List<AppInfo>
}

interface SystemUtils {
    fun isBatteryOptimized(): Boolean
    fun getFilesDir(): String
    fun getCacheDir(): String
    fun getPackageName(): String
    fun getAppVersion(): String
    fun getAppVersionCode(): Int
    fun exitApp()
    fun execPing(host: String, size: Int, timeoutMs: Int, dontFragment: Boolean = false): Boolean
    fun getInterfaceMtu(): Int
    fun isNetworkConnected(): Boolean
    fun readLastCrashLog(): String?
    fun clearCrashLog()
    fun copyToClipboard(text: String)
    fun requestNotificationPermission()
    fun isNotificationPermissionGranted(): Boolean
    fun requestBatteryOptimization()
    fun openVpnSettings()
    fun exportFile(fileName: String, content: String, onResult: (Boolean) -> Unit)
    fun importFile(onResult: (String?) -> Unit)
    fun shareFile(fileName: String, content: String)
    fun readInternalAsset(fileName: String): String?
    fun setSystemProxy(host: String, port: Int)
    fun clearSystemProxy()
    fun setSystemDns(dnsList: String)
    fun clearSystemDns()
    fun isAdministrator(): Boolean
    fun relaunchAsAdmin()
}

interface Settings {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getStringSet(key: String, defaultValue: Set<String>): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

// Platform entry points — single Android implementation lives in
// Platform.android.kt as the primary definitions (no expect/actual).
fun getVpnController(context: PlatformContext): VpnController = androidGetVpnController(context)
fun getTrafficProvider(context: PlatformContext): TrafficProvider = androidGetTrafficProvider(context)
fun getAppInfoProvider(context: PlatformContext): AppInfoProvider = androidGetAppInfoProvider(context)
fun getSystemUtils(context: PlatformContext): SystemUtils = androidGetSystemUtils(context)
fun getSettings(context: PlatformContext): Settings = androidGetSettings(context)
fun getCurrentTimestamp(): String = androidGetCurrentTimestamp()

val isDesktop: Boolean = false

val isWindows: Boolean = false

fun getDeviceModel(): String = androidGetDeviceModel()

fun getOsVersion(): String = androidGetOsVersion()

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier) = AndroidAppIcon(app, modifier)
