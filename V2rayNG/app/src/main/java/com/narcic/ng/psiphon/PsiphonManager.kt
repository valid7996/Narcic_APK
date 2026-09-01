package com.narcic.ng.psiphon

import android.content.Context
import android.util.Log
import ca.psiphon.PsiphonTunnel
import com.narcic.ng.AppConfig
import com.narcic.ng.util.LogUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager for running Psiphon with its multi-rung ladder and Upstream SOCKS5 Chain mode
 * (Psiphon-over-V2Ray / Psiphon-over-AmneziaWG / Direct MSN-Guard Mode).
 */
object PsiphonManager : PsiphonTunnel.HostService {

    private const val TAG = "PsiphonManager"
    const val DEFAULT_LOCAL_SOCKS_PORT = 10819
    const val SOCKS_BIND_IP = "127.0.0.1"

    private var psiphonTunnel: PsiphonTunnel? = null
    private val isRunning = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    private val ladderScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var ladderTimer: ScheduledFuture<*>? = null
    private var ladderIndex = 0

    var activeSocksPort = 0
        private set

    var currentEgressRegion: String = ""
        private set

    // Holds the most recently built config; returned via the HostService
    // callback getPsiphonConfig(), which PsiphonTunnel invokes internally
    // when startTunneling() is called.
    private var currentConfigJson: String = "{}"

    // Traffic listeners
    var onConnectedCallback: (() -> Unit)? = null
    var onDisconnectedCallback: (() -> Unit)? = null
    var onTrafficCallback: ((Long, Long) -> Unit)? = null
    var onStatusCallback: ((String) -> Unit)? = null

    // Alternate DNS servers for high censorship (from MSN-GUARD)
    private val PSIPHON_ALTERNATE_DNS = listOf(
        "1.1.1.1:53", "1.0.0.1:53",
        "8.8.8.8:53", "8.8.4.4:53",
        "9.9.9.9:53", "149.112.112.112:53",
        "208.67.222.222:5353", "208.67.220.220:5353"
    )

    private val PROTOCOLS_FRONTED = listOf(
        "FRONTED-MEEK-HTTP-OSSH",
        "FRONTED-MEEK-HTTPS-OSSH"
    )

    private val PROTOCOLS_DIRECT = listOf(
        "QUIC-OSSH",
        "TLS-OSSH",
        "UNFRONTED-MEEK-HTTPS-OSSH",
        "OSSH",
        "SSH"
    )

    data class Strategy(
        val name: String,
        val label: String,
        val timeoutSeconds: Int,
        val configure: (JSONObject) -> Unit
    )

    private val ladder: List<Strategy> = listOf(
        Strategy("A", "Domain-Fronted (CDN)", 60) { config ->
            config.put("InitialLimitTunnelProtocols", JSONArray(PROTOCOLS_FRONTED))
            config.put("InitialLimitTunnelProtocolsCandidateCount", 30)
            config.put("LimitTunnelProtocols", JSONArray(PROTOCOLS_FRONTED))
            config.put("ConnectionWorkerPoolSize", 12)
            config.put("NetworkLatencyMultiplier", 2.0)
        },
        Strategy("D", "All Protocols (Direct)", 45) { config ->
            config.put("ConnectionWorkerPoolSize", 16)
        },
        Strategy("C", "In-Proxy (Peer Relay)", 75) { config ->
            config.put("InproxyEnabled", true)
            config.put("InproxyAllowClient", true)
            config.put("InproxySkipAwaitFullyConnected", true)
            config.put("ConnectionWorkerPoolSize", 16)
            config.put("NetworkLatencyMultiplier", 3.0)
        }
    )

    fun start(
        context: Context,
        upstreamSocksPort: Int = 0,
        targetRegion: String = "",
        onConnected: (() -> Unit)? = null
    ) {
        if (isRunning.get() || isConnecting.get()) {
            Log.w(TAG, "Psiphon already running or connecting")
            return
        }
        isConnecting.set(true)
        this.onConnectedCallback = onConnected
        this.ladderIndex = 0

        startLadderStep(context.applicationContext, upstreamSocksPort, targetRegion)
    }

    private fun startLadderStep(
        context: Context,
        upstreamSocksPort: Int,
        targetRegion: String
    ) {
        val currentStrategy = ladder.getOrNull(ladderIndex) ?: ladder[0]
        onStatusCallback?.invoke("MSN-Guard: پله ${ladderIndex + 1}/${ladder.size} (${currentStrategy.label})")

        val configJson = buildConfig(context, upstreamSocksPort, targetRegion, currentStrategy)
        Log.i(TAG, "Starting Psiphon step $ladderIndex: ${currentStrategy.label}")

        try {
            stopCurrentTunnel()

            currentConfigJson = configJson

            val tunnel = PsiphonTunnel.newPsiphonTunnel(this)
            psiphonTunnel = tunnel
            tunnel.startTunneling(configJson)

            // Setup timer for this ladder rung
            ladderTimer?.cancel(true)
            ladderTimer = ladderScheduler.schedule({
                if (isConnecting.get() && !isRunning.get()) {
                    Log.w(TAG, "Ladder step $ladderIndex timed out (${currentStrategy.timeoutSeconds}s). Escalating...")
                    ladderIndex++
                    if (ladderIndex < ladder.size) {
                        startLadderStep(context, upstreamSocksPort, targetRegion)
                    } else {
                        // All ladder steps exhausted
                        Log.e(TAG, "All MSN-Guard Psiphon ladder steps exhausted")
                        isConnecting.set(false)
                        stop()
                        onStatusCallback?.invoke("اتصال ناموفق بود")
                    }
                }
            }, currentStrategy.timeoutSeconds.toLong(), TimeUnit.SECONDS)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Psiphon tunnel step", e)
            isConnecting.set(false)
        }
    }

    private fun buildConfig(
        context: Context,
        upstreamSocksPort: Int,
        targetRegion: String,
        strategy: Strategy
    ): String {
        val json = JSONObject()
        val dataDir = File(context.filesDir, "psiphon_data").apply { mkdirs() }

        json.put("DataStoreDirectory", dataDir.absolutePath)
        json.put("LocalSocksProxyPort", DEFAULT_LOCAL_SOCKS_PORT)
        json.put("ClientVersion", "2.0.39")

        // Server list public keys (embedded from MSN-GUARD)
        json.put("RemoteServerListSignaturePublicKey", "MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM=")
        json.put("ServerEntrySignaturePublicKey", "sHuUVTWaRyh5pZwy4UguSgkwmBe0EHtJJkoF5WrxmvA=")
        json.put("ExchangeObfuscationKey", "DpXzloJk1Hw6aSzmKKky0xcahsEHubch81Mi6K0XMlU=")
        json.put("EmitBytesTransferred", true)
        json.put("DeviceRegion", "IR")
        json.put("EmitDiagnosticNotices", true)

        json.put("DNSResolverPreferredAlternateServers", JSONArray(PSIPHON_ALTERNATE_DNS))
        json.put("DNSResolverPreferAlternateServerProbability", 1.0)
        json.put("DNSResolverAttemptsPerPreferredServer", 2)

        if (targetRegion.isNotBlank()) {
            json.put("EgressRegion", targetRegion.uppercase())
        }

        // Upstream chain proxy if enabled (e.g. SOCKS from V2Ray or WARP)
        if (upstreamSocksPort > 0) {
            json.put("UpstreamProxyURL", "socks5://127.0.0.1:$upstreamSocksPort")
            Log.i(TAG, "Configured Upstream Chain Proxy: socks5://127.0.0.1:$upstreamSocksPort")
        }

        // Apply strategy tuning
        strategy.configure(json)

        return json.toString()
    }

    fun stop() {
        ladderTimer?.cancel(true)
        ladderTimer = null
        isConnecting.set(false)
        stopCurrentTunnel()
        isRunning.set(false)
        activeSocksPort = 0
        onDisconnectedCallback?.invoke()
    }

    private fun stopCurrentTunnel() {
        try {
            psiphonTunnel?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping tunnel", e)
        }
        psiphonTunnel = null
    }

    fun isRunning(): Boolean = isRunning.get()

    // HostService Implementation
    override fun getContext(): Context? = null

    override fun getPsiphonConfig(): String = currentConfigJson

    override fun onDiagnosticMessage(message: String?) {
        LogUtil.d(TAG, "Psiphon notice: $message")
    }

    override fun onAvailableEgressRegions(regions: MutableList<String>?) {
        Log.i(TAG, "Available regions: $regions")
    }

    override fun onConnectedServerRegion(region: String?) {
        currentEgressRegion = region.orEmpty()
        Log.i(TAG, "Connected to region: $region")
    }

    override fun onStartedWaitingForNetworkConnectivity() {
        Log.i(TAG, "Waiting for network connectivity")
    }

    override fun onStoppedWaitingForNetworkConnectivity() {
        Log.i(TAG, "Network connectivity restored")
    }

    override fun onConnecting() {
        Log.i(TAG, "Psiphon connecting...")
    }

    override fun onConnected() {
        Log.i(TAG, "Psiphon connected successfully!")
        ladderTimer?.cancel(true)
        isConnecting.set(false)
        isRunning.set(true)
        activeSocksPort = DEFAULT_LOCAL_SOCKS_PORT
        onConnectedCallback?.invoke()
    }

    override fun onListeningSocksProxyPort(port: Int) {
        Log.i(TAG, "Listening on SOCKS proxy port: $port")
        activeSocksPort = port
    }

    override fun onListeningHttpProxyPort(port: Int) {}

    override fun onUpstreamProxyError(error: String?) {
        Log.e(TAG, "Upstream proxy error: $error")
    }

    override fun onBytesTransferred(sent: Long, received: Long) {
        onTrafficCallback?.invoke(sent, received)
    }

    override fun onExiting() {
        Log.i(TAG, "Psiphon exiting")
    }
}
