package com.narcic.ng.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.narcic.ng.AppConfig
import com.narcic.ng.R
import com.narcic.ng.contracts.IDialerService
import com.narcic.ng.contracts.ServiceControl
import com.narcic.ng.dto.OutboundTrafficStat
import com.narcic.ng.dto.V2rayConfig.OutboundBean
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.BrowserDialerMode
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.isNotNullEmpty
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.handler.NotificationManager
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.handler.SpeedtestManager
import com.narcic.ng.helper.MessageHelper
import com.narcic.ng.rsta.NarcisSpoofConfig
import com.narcic.ng.service.DialerNativeService
import com.narcic.ng.service.DialerWebviewService
import com.narcic.ng.service.NarcisSpoofService
import com.narcic.ng.service.NetworkMonitor
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress

object CoreServiceManager {

    private const val AETHER_WARM_UP_MS = 30_000L

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null

    /** Owns the Aether warm-up wait; cancelled on every start and stop so a stale wait cannot report. */
    private val aetherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var aetherWarmUpJob: Job? = null

    /** Set once an Aether exit has stopped the service, so a second report of the same exit is a no-op. */
    @Volatile
    private var aetherExitHandled = false

    @Volatile
    private var isReloading = false

    /** Tun descriptor the core was started with, null in the proxy only and root run modes. */
    private var currentVpnInterface: ParcelFileDescriptor? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_NOT_RUNNING, "")
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?, isReload: Boolean = false) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")

        // نرسیس اسپوف (Narcis Spoof): if this server points at the local
        // spoof listener, start the native forwarder before the core config
        // is built so the outbound has somewhere to dial into.
        if (NarcisSpoofConfig.enabled() && NarcisSpoofConfig.isSpoofTarget(config.server, config.serverPort)) {
            val intent = Intent(service, NarcisSpoofService::class.java).apply {
                action = "START"
                putExtra("IP", NarcisSpoofConfig.connectIp())
                putExtra("PORT", NarcisSpoofConfig.connectPort())
                putExtra("SNI", NarcisSpoofConfig.fakeSni())
                putExtra("METHOD", NarcisSpoofConfig.method())
            }
            ContextCompat.startForegroundService(service, intent)
        } else {
            val intent = Intent(service, NarcisSpoofService::class.java).apply { action = "STOP" }
            service.startService(intent)
        }

        val desyncPort = try {
            DesyncManager.start(config)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to start desync engine", e)
            error("Failed to start desync engine: ${e.message ?: e.javaClass.simpleName}")
        }

        // ── Two-engine chain (CROSS_CHAIN): member ۱ (chainOuterId) is the
        // FIRST hop, member ۲ (chainInnerId) is the EXIT. Traffic:
        // تونل → سرور ۲ → سرور ۱ → اینترنت. Every engine type is supported in
        // EITHER slot (no forced roles):
        //   Aether member  → its own core process + a socks outbound at its port
        //   WG/AWG member  → wireguard outbound built from its config
        //   Xray-family    → its normal outbound
        // All non-exit layers are appended as tagged outbounds chained via
        // dialerProxy; only the exit layer owns the tun. ──
        var content: String
        val aetherWarmup = ArrayList<Pair<String, ProfileItem>>()
        if (config.configType == EConfigType.CROSS_CHAIN) {
            val firstGuid = config.chainOuterId.orEmpty()
            val exitGuid = config.chainInnerId.orEmpty()
            if (firstGuid == exitGuid) {
                error(service.getString(R.string.crosschain_bad_carrier, firstGuid.take(8)))
            }

            fun fragmentOf(member: ProfileItem, memberGuid: String, tag: String): OutboundBean? {
                return when (member.configType) {
                    EConfigType.AETHER -> {
                        aetherWarmup += memberGuid to member
                        OutboundBean(
                            tag = tag,
                            protocol = "socks",
                            settings = OutboundBean.OutSettingsBean(
                                address = "127.0.0.1",
                                port = AetherCore.of(member).port.toInt(),
                            ),
                        )
                    }
                    EConfigType.WIREGUARD -> CoreOutboundBuilder.convert(member)?.apply { this.tag = tag }
                    EConfigType.AMNEZIAWG -> CoreOutboundBuilder.awgOutbound(member)?.apply { this.tag = tag }
                    EConfigType.VMESS, EConfigType.VLESS, EConfigType.SHADOWSOCKS,
                    EConfigType.TROJAN, EConfigType.HTTP -> CoreOutboundBuilder.convert(member)?.apply { this.tag = tag }
                    else -> null
                }
            }

            val first = MmkvManager.decodeServerConfig(firstGuid)
                ?: error(service.getString(R.string.crosschain_bad_carrier, firstGuid.take(8)))
            val exit = MmkvManager.decodeServerConfig(exitGuid)
                ?: error(service.getString(R.string.crosschain_bad_inner, exitGuid.take(8)))

            if (aetherWarmup.isNotEmpty() && !AetherCoreManager.isSupported(service)) {
                error(service.getString(R.string.aether_unsupported_abi))
            }
            aetherExitHandled = false

            // Exit layer: the full config of member ۲ (it owns the tun).
            val exitResult = CoreConfigManager.getV2rayConfig(service, exitGuid, desyncPort)
            if (!exitResult.status) {
                error(exitResult.errorMessage.ifBlank { "Failed to build exit config" })
            }
            val root = com.narcic.ng.util.JsonUtil.parseString(exitResult.content)?.takeIf { it.isJsonObject }
                ?: error("Failed to parse exit config")
            val outbounds = root.getAsJsonArray("outbounds")
                ?: error("Exit config has no outbounds")

            // Carrier layer: appended as a tagged outbound, then the exit's
            // first outbound is pointed at it via dialerProxy.
            val firstFragment = fragmentOf(first, firstGuid, "chain1")
                ?: error(service.getString(R.string.crosschain_bad_carrier, first.remarks))
            val entry = outbounds.firstOrNull() as? JsonObject
            if (entry != null) {
                val stream = entry.getAsJsonObject("streamSettings")
                    ?: JsonObject().also { entry.add("streamSettings", it) }
                val sockopt = stream.getAsJsonObject("sockopt")
                    ?: JsonObject().also { stream.add("sockopt", it) }
                sockopt.addProperty("dialerProxy", "chain1")
            }
            outbounds.add(com.google.gson.JsonParser.parseString(com.google.gson.Gson().toJson(firstFragment)).asJsonObject)
            content = root.toString()
        } else {
            val result = CoreConfigManager.getV2rayConfig(service, guid, desyncPort)
            LogUtil.d(AppConfig.TAG, result.content)
            if (!result.status) {
                error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
            }
            content = result.content
        }

        cancelAetherWarmUp()
        when {
            config.configType == EConfigType.AETHER -> {
                aetherExitHandled = false
            }
            aetherWarmup.isNotEmpty() -> {
                aetherExitHandled = false
            }
            else -> {
                AetherCoreManager.stop()
            }
        }

        launchNativeCore(service, guid, config, aetherWarmup, content, vpnInterface, isReload)
    }

    @Throws(Exception::class)
    private fun launchNativeCore(
        service: Service,
        guid: String,
        config: ProfileItem,
        aetherWarmup: List<Pair<String, ProfileItem>>,
        content: String,
        vpnInterface: ParcelFileDescriptor?,
        isReload: Boolean,
    ) {
        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }
        coreController.startLoop(content, tunFd)

        if (!isRunning()) {
            error("Core failed to start")
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        when {
            config.configType == EConfigType.AETHER ->
                announceAetherWarmUp(service, guid, config, isReload)
            aetherWarmup.isNotEmpty() ->
                aetherWarmup.first().let { (g, m) -> announceAetherWarmUp(service, g, m, isReload) }
            !isReload ->
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }
        NotificationManager.startSpeedNotification()
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
    }

    /**
     * Owns the Aether startup end to end, off the calling thread: xray is already running by the
     * time this is called, but an Aether profile carries no traffic until the core it depends on
     * has scanned and connected -- and, with Psiphon or Tor inside the tunnel, until that carrier
     * has connected too, which [AetherCoreManager.awaitListening] already waits on via the core's
     * own ready word. The start-success signal is held back for all of that, so the UI keeps
     * showing its normal "starting" state instead of reporting a connection that cannot carry
     * traffic yet.
     */
    private fun announceAetherWarmUp(service: Service, guid: String, config: ProfileItem, isReload: Boolean) {
        aetherWarmUpJob = aetherScope.launch {
            AetherCoreManager.start(service, AetherCore.of(config), afterProbes = false) { onAetherExit(guid) }

            var listening = false
            while (isActive && !listening && AetherCoreManager.isRunning) {
                listening = AetherCoreManager.awaitListening(AETHER_WARM_UP_MS)
            }
            if (!isActive) return@launch
            if (listening) {
                if (!isReload) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
                }
            } else if (!AetherCoreManager.isRunning) {
                onAetherExit(guid)
            }
        }
    }

    private fun isAetherWarmingUp(): Boolean = aetherWarmUpJob?.isActive == true

    private fun cancelAetherWarmUp() {
        aetherWarmUpJob?.cancel()
        aetherWarmUpJob = null
    }

    /**
     * Stops the service once the Aether core is gone while Xray still runs. Reached from the
     * warm-up wait above, and from [AetherCoreManager]'s own exit callback once warm-up succeeded.
     */
    private fun onAetherExit(guid: String) {
        val control = serviceControl?.get() ?: return
        val service = control.getService()
        if (aetherExitHandled || AetherCoreManager.isRunning || !isRunning() || serviceControl?.get() !== control) return
        aetherExitHandled = true
        LogUtil.w(
            AppConfig.TAG,
            "StartCore-Manager: Aether core exited while running, stopping ${service.javaClass.simpleName}, guid=$guid"
        )
        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, service.getString(R.string.aether_core_stopped))
        control.stopService()
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null
        cancelAetherWarmUp()
        AetherCoreManager.stop()
        DesyncManager.stop()

        run {
            val intent = Intent(service, NarcisSpoofService::class.java).apply { action = "STOP" }
            service.startService(intent)
        }

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
    private fun startNetworkMonitor(service: Service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks -> serviceControl?.get()?.setUnderlyingNetworks(networks) },
            onHandover = { reloadCore() },
        ).also { it.register() }
    }

    /**
     * Restarts the core in place after the upstream network changed: the service, the notification
     * and the VPN interface all stay up, so nothing of this is visible.
     *
     * The config is rebuilt on purpose, outbound server domains are resolved while building it and
     * an address resolved on a network that is gone can be unusable on the new one.
     *
     * @return True if the core is running again.
     */
    private fun reloadCore(): Boolean {
        if (isReloading) return false
        val service = getService() ?: return false
        if (!isRunning()) return false

        return try {
            val tunFd = currentVpnInterface

            isReloading = true
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")

            coreController.stopLoop()
            launchCore(service, tunFd, isReload = true)

            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload finished")
            true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to reload core: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            false
        } finally {
            isReloading = false
        }
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay() {
        if (!isRunning()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch

            // The same budget every other profile's probe gets; a tunnel still scanning past it is reported, not waited for.
            if (currentConfig?.configType == EConfigType.AETHER && !AetherCoreManager.awaitListening(AetherDelayTester.TEST_BUDGET_MS)) {
                val reason = if (AetherCoreManager.isRunning) R.string.aether_core_connecting else R.string.aether_core_stopped
                MessageHelper.sendMeasureDelayResult(service, service.getString(reason), -1L)
                return@launch
            }

            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
            }
            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }

            val result = if (time >= 0) {
                service.getString(R.string.connection_test_available, time)
            } else {
                service.getString(R.string.connection_test_error, errorStr)
            }
            MessageHelper.sendMeasureDelayResult(service, result, time)

            // Only fetch IP info if the delay test was successful
            if (time >= 0) {
                SpeedtestManager.getRemoteIPInfo()?.let { ip ->
                    MessageHelper.sendMeasureDelayResult(service, "$result\n$ip", time)
                }
            }
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop service", e)
                -1
            }
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (isRunning()) {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    if (isOrderedBroadcast) {
                        resultCode = Activity.RESULT_OK
                    }
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    LauncherManager.startService(serviceControl.getService())
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.onScreenOff()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.onScreenOn()
                }
            }
        }
    }
}