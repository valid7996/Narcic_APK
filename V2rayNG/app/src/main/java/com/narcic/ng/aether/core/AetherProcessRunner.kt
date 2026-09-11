package com.narcic.ng.aether.core

import android.content.Context
import com.narcic.ng.aether.core.PsiphonController
import com.narcic.ng.aether.shared.data.LogRepository
import com.narcic.ng.aether.shared.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.milliseconds

class AetherProcessRunner(private val context: Context) {

    private val lock = Any()
    private var process: Process? = null
    private var runnerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentAttemptId = AtomicLong(0)
    private var goolOuterValidated = false
    private var dataPlaneOk = false
    // libaether binds the local SOCKS5 listener (and logs "socks5 listening on")
    // BEFORE data-plane validation in MASQUE mode; remember it so the later
    // "tunnel validated ... exposing socks5" line can promote straight to
    // SOCKS_READY instead of stranding the state at DATAPLANE_VALIDATED.
    private var socksListeningSeen = false
    // MASQUE clean-install bootstrap observability: libaether logs its
    // Cloudflare registration/provisioning phase ("no masque identity
    // found", "provisioning dedicated masque account", "enrolling MASQUE
    // key", "provisioned and saved new masque identity", ...). Track the
    // phase so ConnectionController can bound it and the UI is never stuck
    // on "Establishing tunnel" without explanation.
    @Volatile var bootstrapPhase: String? = null
        private set
    private val isReconnecting = AtomicBoolean(false)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.STOPPED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val quickRetryPending = AtomicBoolean(false)

    fun start(config: AetherConfig, bindAddress: String, onCodeRequired: () -> Unit = {}, inputProvider: suspend () -> String = { "" }) {
        synchronized(lock) {
            if (runnerJob?.isActive == true) return

            val attemptId = currentAttemptId.incrementAndGet()
            // New attempt: forget any listener state from a previous process run.
            goolOuterValidated = false
            dataPlaneOk = false
            socksListeningSeen = false
            bootstrapPhase = null
            updateState(ConnectionStatus.STARTING, attemptId)
            runnerJob = scope.launch {
                var retryCount = 0

                while (isActive && (currentAttemptId.get() == attemptId)) {
                    if (!config.smartReconnect && retryCount > 0) {
                        LogRepository.e("Smart Reconnect disabled -> stopping retry")
                        updateState(ConnectionStatus.ERROR, attemptId)
                        break
                    }
                    if (config.smartReconnect && (retryCount >= config.reconnectRetryLimit)) {
                        LogRepository.e("Smart Reconnect limit reached ($retryCount). Stopping...")
                        updateState(ConnectionStatus.ERROR, attemptId)
                        break
                    }

                    if (retryCount > 0) {
                        val waitTime = if (quickRetryPending.compareAndSet(true, false)) {
                            LogRepository.i("Recovering connection (Quick retry after cached gateway loss)...")
                            3000L
                        } else {
                            (retryCount * 1000L).coerceAtMost(10000L)
                        }
                        LogRepository.i("Recovering connection (Retry $retryCount)...")
                        updateState(ConnectionStatus.RECONNECTING, attemptId)
                        delay(waitTime.milliseconds)
                    } else {
                        LogRepository.i("Starting system core...")
                    }

                    if (currentAttemptId.get() != attemptId) break

                    try {
                        LogRepository.i("[AetherDiag] RUNNER runBinary call retry=$retryCount attemptId=$attemptId")
                        val result = runBinary(config, attemptId, bindAddress, onCodeRequired, inputProvider)
                        LogRepository.i("[AetherDiag] RUNNER runBinary returned retry=$retryCount attemptId=$attemptId result=$result runnerStatus=${_connectionStatus.value}")
                        if (currentAttemptId.get() != attemptId) break

                        if (!result) {
                            LogRepository.e("Stability check failed. Retrying...")
                        }
                    } catch (e: CancellationException) {
                        LogRepository.i("[AetherDiag] PATH RUNNER_CATCH_CANCELLATION attemptId=$attemptId retry=$retryCount")
                        throw e
                    } catch (e: Exception) {
                        LogRepository.e("Execution cycle critical error: ${e.localizedMessage}")
                        LogRepository.i("[AetherDiag] PATH RUNNER_CATCH_EXCEPTION attemptId=$attemptId retry=$retryCount type=${e.javaClass.name} msg=${e.localizedMessage}")
                    }

                    retryCount++
                }
            }
        }
    }

    private suspend fun runBinary(config: AetherConfig, attemptId: Long, bindAddress: String, onCodeRequired: () -> Unit, inputProvider: suspend () -> String): Boolean = coroutineScope {
        var proc: Process? = null
        try {
            dataPlaneOk = false
            socksListeningSeen = false
            val binaryFile = BinaryManager.prepareBinary(context)
            if (currentAttemptId.get() != attemptId) return@coroutineScope true

            val commandList = mutableListOf<String>()
            commandList.add(binaryFile.absolutePath)
            commandList.add("--bind")
            commandList.add(bindAddress)

            val routingFile = writeRoutingFile(config)
            if (routingFile != null) {
                commandList.add("--routes")
                commandList.add(routingFile.absolutePath)
            }

            val effectiveIp = config.effectiveIpMode()
            commandList.add(
                when (effectiveIp) {
                    AetherIpMode.IPV4 -> "-4"
                    AetherIpMode.IPV6 -> "-6"
                    else -> "--dual"
                },
            )

            if (config.h2Mode) commandList.add("--h2")
            if (config.echEnabled) commandList.add("--ech")
            if (config.echEnabled) commandList.add("auto")
            
            if (config.httpProxyEnabled) {
                val httpBindHost = bindAddress.substringBefore(':')
                commandList.add("--http-proxy")
                commandList.add("$httpBindHost:${config.httpPort}")
            }

            if (config.h2Fragment) {
                commandList.add("--fragment")
                commandList.add("--fragment-size")
                commandList.add(config.fragmentSize)
                commandList.add("--fragment-delay")
                commandList.add(config.fragmentDelay)
            }
            if (config.noDataCheck) commandList.add("--no-data-check")
            if (config.quickReconnect) commandList.add("--quick-reconnect") else commandList.add("--no-quick-reconnect")

            val wiwOuter = if (config.protocol == AetherProtocol.GOOL) config.wiwOuter.trim() else ""
            val wiwInner = if (config.protocol == AetherProtocol.GOOL) config.wiwInner.trim() else ""
            val hasWiwManual = wiwOuter.isNotEmpty() || wiwInner.isNotEmpty()
            val effectivePeerForCmd = if (!hasWiwManual && (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) && config.wgPeer.isNotEmpty()) config.wgPeer else if (!hasWiwManual) config.peer else ""
            if (effectivePeerForCmd.isNotEmpty()) {
                commandList.add("--peer")
                commandList.add(effectivePeerForCmd)
            }
            if (!hasWiwManual && (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) && effectivePeerForCmd.isNotEmpty()) {
                commandList.add("--wg-peer")
                commandList.add(effectivePeerForCmd)
            }
            if (config.protocol == AetherProtocol.GOOL) {
                if (wiwOuter.isNotEmpty()) {
                    commandList.add("--wiw-outer")
                    commandList.add(wiwOuter)
                }
                if (wiwInner.isNotEmpty()) {
                    commandList.add("--wiw-inner")
                    commandList.add(wiwInner)
                }
                if (!hasWiwManual && config.wiwScan && effectivePeerForCmd.isEmpty()) {
                    commandList.add("--wiw-scan")
                }
            }

            if ((config.protocol == AetherProtocol.WG) || (config.protocol == AetherProtocol.GOOL)) {
                commandList.add("--keepalive")
                commandList.add(if (config.keepaliveEnabled) config.keepalive.toString() else "0")
            }

            if (config.tlsGroups.isNotEmpty()) {
                commandList.add("--tls-groups")
                commandList.add(config.tlsGroups)
            }

            commandList.add("--validate-secs")
            commandList.add(config.validateSecs.toString())
            
            commandList.add("--reconnect-secs")
            commandList.add(config.reconnectSecs.toString())

            if (config.noProfileRetry) commandList.add("--no-profile-retry")

            if (config.protocol == AetherProtocol.ZERO_TRUST) {
                if (config.teamName.isNotEmpty()) {
                    commandList.add("--team")
                    commandList.add(config.teamName)
                }
                when {
                    config.accessToken.isNotEmpty() -> {
                        commandList.add("--access-token")
                        commandList.add(config.accessToken)
                    }
                    config.accessId.isNotEmpty() || config.accessSecret.isNotEmpty() -> {
                        commandList.add("--access-id")
                        commandList.add(config.accessId)
                        commandList.add("--access-secret")
                        commandList.add(config.accessSecret)
                    }
                    config.accessEmail.isNotEmpty() -> {
                        commandList.add("--access-email")
                        commandList.add(config.accessEmail)
                    }
                }
                if (config.useGateway) {
                    commandList.add("--gateway")
                }
            }

            if (config.dnsEnabled && config.dnsList.isNotEmpty()) {
                commandList.add("--dns")
                commandList.add(config.dnsList)
            }

            if (config.upstreamProxyEnabled && config.upstreamProxy.isNotEmpty()) {
                commandList.add("--upstream")
                commandList.add(config.upstreamProxy)
                if (config.upstreamProxy.startsWith("http://", ignoreCase = true)) commandList.add("--h2")
            }

            val pb = ProcessBuilder(commandList)
            pb.directory(context.filesDir)

            val env = pb.environment()
            env["AETHER_PROTOCOL"] = config.protocol.rawValue
            env["AETHER_NOIZE"] = config.noise.rawValue
            env["AETHER_SCAN"] = config.scanMode.rawValue
            env["AETHER_IP"] = config.effectiveIpMode().rawValue
            env["AETHER_SOCKS"] = bindAddress

            routingFile?.let { env["AETHER_ROUTES_FILE"] = it.absolutePath }

            if (config.h2Mode) env["AETHER_MASQUE_HTTP2"] = "1"
            if (config.echEnabled) env["AETHER_ECH"] = "auto"
            
            if (config.httpProxyEnabled) {
                val httpBindHost = bindAddress.substringBefore(':')
                env["AETHER_HTTP_PROXY"] = "$httpBindHost:${config.httpPort}"
            }

            if (config.h2Fragment) {
                env["AETHER_MASQUE_H2_FRAGMENT"] = "1"
                env["AETHER_MASQUE_H2_FRAGMENT_SIZE"] = config.fragmentSize
                env["AETHER_MASQUE_H2_FRAGMENT_DELAY"] = config.fragmentDelay
            }

            if (config.noDataCheck) {
                env["AETHER_MASQUE_NO_DATA_CHECK"] = "1"
                env["AETHER_WG_NO_DATA_CHECK"] = "1"
            }

            if (config.quickReconnect) env["AETHER_QUICK_RECONNECT"] = "1" else env["AETHER_QUICK_RECONNECT"] = "0"

            if (config.protocol == AetherProtocol.WG || config.protocol == AetherProtocol.GOOL) {
                if (!hasWiwManual) {
                    if (config.wgPeer.isNotEmpty()) env["AETHER_WG_PEER"] = config.wgPeer else if (config.peer.isNotEmpty()) env["AETHER_WG_PEER"] = config.peer
                    if (config.peer.isNotEmpty()) env["AETHER_PEER"] = config.peer
                }
                if (config.protocol == AetherProtocol.GOOL) {
                    if (wiwOuter.isNotEmpty()) env["AETHER_WIW_OUTER_PEER"] = wiwOuter
                    if (wiwInner.isNotEmpty()) env["AETHER_WIW_INNER_PEER"] = wiwInner
                    if (!hasWiwManual && config.wiwScan && effectivePeerForCmd.isEmpty()) env["AETHER_WIW_PEERS"] = "auto"
                }
            } else {
                if (config.peer.isNotEmpty()) env["AETHER_PEER"] = config.peer
            }

            env["AETHER_WG_KEEPALIVE"] = if (config.keepaliveEnabled) config.keepalive.toString() else "0"
            env["AETHER_WG_ENDPOINT_COOLDOWN_SECS"] = config.wgEndpointCooldownSecs.toString()
            env["AETHER_MASQUE_VALIDATE_SECS"] = config.validateSecs.toString()
            env["AETHER_WG_VALIDATE_SECS"] = config.validateSecs.toString()
            env["AETHER_MASQUE_RECONNECT_SECS"] = config.reconnectSecs.toString()
            env["AETHER_WG_RECONNECT_SECS"] = config.reconnectSecs.toString()

            if (config.noProfileRetry) env["AETHER_WG_NO_PROFILE_RETRY"] = "1"
            if (config.tlsGroups.isNotEmpty()) env["AETHER_TLS_GROUPS"] = config.tlsGroups
            if (config.masqueMtu > 0) env["AETHER_MASQUE_MTU"] = config.masqueMtu.toString()
            if (config.netstackTcpRx > 0) env["AETHER_NETSTACK_TCP_RX"] = config.netstackTcpRx.toString()
            if (config.netstackTcpTx > 0) env["AETHER_NETSTACK_TCP_TX"] = config.netstackTcpTx.toString()

            if (config.protocol == AetherProtocol.ZERO_TRUST) {
                if (config.teamName.isNotEmpty()) env["AETHER_TEAM"] = config.teamName
                when {
                    config.accessToken.isNotEmpty() -> env["AETHER_ACCESS_TOKEN"] = config.accessToken
                    config.accessId.isNotEmpty() || config.accessSecret.isNotEmpty() -> {
                        env["AETHER_ACCESS_ID"] = config.accessId
                        env["AETHER_ACCESS_SECRET"] = config.accessSecret
                        env["AETHER_ACCESS_CLIENT_ID"] = config.accessId
                        env["AETHER_ACCESS_CLIENT_SECRET"] = config.accessSecret
                    }
                    config.accessEmail.isNotEmpty() -> env["AETHER_ACCESS_EMAIL"] = config.accessEmail
                }
                if (config.useGateway) env["AETHER_GATEWAY"] = "1"
            }
            routingFile?.let {
                try {
                    it.setReadable(false, false)
                    it.setReadable(true, true)
                    it.setWritable(false, false)
                    it.setWritable(true, true)
                } catch (_: Exception) {}
            }
            if (config.dnsEnabled && config.dnsList.isNotEmpty()) env["AETHER_DNS"] = config.dnsList
            if (config.upstreamProxyEnabled && config.upstreamProxy.isNotEmpty()) env["AETHER_UPSTREAM"] = config.upstreamProxy
            env["AETHER_ROUTE_SNIFF"] = if (config.routeSniffing) "1" else "0"
            env["AETHER_ROUTE_SNIFF_MS"] = config.sniffingTimeoutMs.toString()
            env["AETHER_REPROVISION"] = if (config.reprovision) "1" else "0"

            env["AETHER_PERF_PROFILE"] = config.perfProfile.rawValue
            env["AETHER_LOG_LEVEL"] = config.coreLogLevel.rawValue

            pb.redirectErrorStream(true)

            proc = withContext(Dispatchers.IO) { pb.start() }
            LogRepository.i("[AetherDiag] RUNNER process started attemptId=$attemptId binary=${binaryFile.absolutePath}")

            synchronized(lock) {
                if (currentAttemptId.get() != attemptId) {
                    LogRepository.i("[AetherDiag] PATH RUNNER_STALE_AFTER_START attemptId=$attemptId (destroying just-started process)")
                    proc?.destroyForcibly()
                    return@coroutineScope true
                }
                process = proc
            }

            val inputJob = launch {
                val writer = BufferedWriter(OutputStreamWriter(proc!!.outputStream))
                try {
                    while (isActive) {
                        val text = inputProvider()
                        if (text.isNotEmpty()) {
                            writer.write(text)
                            writer.newLine()
                            writer.flush()
                            LogRepository.d("Sent input to binary")
                        }
                    }
                } catch (_: CancellationException) {
                } catch (exception: Exception) {
                    if (currentCoroutineContext().isActive && currentAttemptId.get() == attemptId) {
                        LogRepository.w("Process input pipe closed: ${exception.localizedMessage}")
                    }
                }
            }

            BufferedReader(InputStreamReader(proc!!.inputStream)).use { reader ->
                var line: String?
                while (currentCoroutineContext().isActive && (currentAttemptId.get() == attemptId)) {
                    line = try {
                        reader.readLine()
                    } catch (e: java.io.IOException) {
                        if (currentAttemptId.get() != attemptId) null else throw e
                    } ?: break

                    parseOutputLine(line, attemptId, config.protocol, onCodeRequired)
                }
            }

            inputJob.cancel()
            val exitCode = try { withContext(Dispatchers.IO) { proc.waitFor() } } catch (_: Exception) { -1 }
            if (currentAttemptId.get() == attemptId) {
                LogRepository.i("Core process terminated (Exit code: $exitCode)")
            }
            exitCode == 0
        } catch (e: CancellationException) {
            LogRepository.i("[AetherDiag] PATH RUNNERRUN_CATCH_CANCELLATION attemptId=$attemptId")
            throw e
        } catch (e: Exception) {
            if (currentAttemptId.get() == attemptId) {
                LogRepository.e("Binary runtime error: ${e.localizedMessage}")
                LogRepository.i("[AetherDiag] PATH RUNNERRUN_CATCH_EXCEPTION attemptId=$attemptId type=${e.javaClass.name} msg=${e.localizedMessage}\n${e.stackTraceToString()}")
                return@coroutineScope false
            }
            true
        } finally {
            LogRepository.i("[AetherDiag] PATH RUNNERRUN_FINALLY attemptId=$attemptId procAlive=${proc?.isAlive}")
            synchronized(lock) {
                if (process === proc) process = null
            }
            try { proc?.destroyForcibly() } catch (e: Exception) { LogRepository.w("destroyForcibly failed: ${e.message}") }
        }
    }

    private fun isZeroTrustCodePrompt(line: String): Boolean {
        return line.contains("code") && (
            line.contains("enter") || line.contains("login") || line.contains("verif") ||
            line.contains("confirm") || line.contains("otp") || line.contains("one-time") ||
            line.contains("type the") || line.contains("paste") || line.contains("prompt")
        )
    }

    private suspend fun parseOutputLine(line: String, attemptId: Long, protocol: AetherProtocol, onCodeRequired: () -> Unit) {
        if (currentAttemptId.get() != attemptId) return

        val lower = line.lowercase()

        val isBroken = lower.contains("broken pipe") || lower.contains("stream closed")
        if (isBroken) {
            val cause = when {
                lower.contains("upstream") || lower.contains("socks") -> "UPSTREAM_PSIHON_3080_CLOSED"
                lower.contains("h2") || lower.contains("stream") -> "H2_STREAM_PEER_CLOSED"
                else -> "BROKEN_PIPE_UNKNOWN"
            }
            LogRepository.e("[AetherCore] $cause: $line", "AetherCore")
            if (!PsiphonController.isConnected()) {
                LogRepository.e("[AetherCore] Psiphon upstream down; delaying reconnect 5s before retry", "AetherCore")
                delay(5000.milliseconds)
            }
        } else {
            when {
                lower.contains(" error ") || lower.contains("[error]") -> LogRepository.e(line, "AetherCore")
                lower.contains(" warn ") || lower.contains("[warn]") -> LogRepository.w(line, "AetherCore")
                else -> LogRepository.i(line, "AetherCore")
            }
        }

        if (isZeroTrustCodePrompt(lower)) {
            onCodeRequired()
            return
        }

        val isCriticalError = (lower.contains("fatal") || lower.contains("panic")) &&
                !lower.contains("socksbridge") &&
                !lower.contains("connection failed")
        
        val isLost = lower.contains("tunnel lost") || 
                     lower.contains("handshake timeout") || 
                     lower.contains("handshake failed") ||
                     lower.contains("connection refused") ||
                     lower.contains("all gateways failed") ||
                     lower.contains("broken pipe") ||
                     lower.contains("stream closed") ||
                     lower.contains("tunnel ended") ||
                     lower.contains("tunnel exited")

        when {
            lower.contains("scanning") -> {
                goolOuterValidated = false
                dataPlaneOk = false
                quickRetryPending.set(false)
                SocksGate.setReady(SocksReadiness.NOT_READY)
                updateState(ConnectionStatus.STARTING, attemptId)
            }
            // ---- MASQUE clean-install bootstrap/provisioning phase ----
            // Non-terminal: keep the state machine alive and observable.
            // Registration failures are surfaced as a phase string the
            // controller bounds; success clears the phase below. MASQUE-only:
            // Zero Trust enrollment deliberately waits on user input
            // (isWaitingForCode) and must not be bounded by this phase.
            protocol == AetherProtocol.MASQUE && (lower.contains("no masque identity found") || lower.contains("provisioning dedicated masque account")) -> {
                bootstrapPhase = "masque_registering"
                if (_connectionStatus.value == ConnectionStatus.STARTING) updateState(ConnectionStatus.VALIDATING, attemptId)
            }
            protocol == AetherProtocol.MASQUE && (lower.contains("masque identity needs a certificate") || lower.contains("registering a fresh masque account") || lower.contains("enrolling masque key")) -> {
                bootstrapPhase = "masque_enrolling"
                if (_connectionStatus.value == ConnectionStatus.STARTING) updateState(ConnectionStatus.VALIDATING, attemptId)
            }
            protocol == AetherProtocol.MASQUE && (lower.contains("provisioned and saved new masque identity") || lower.contains("loaded existing masque identity") || lower.contains("masque key enrolled")) -> {
                bootstrapPhase = null
                if (_connectionStatus.value == ConnectionStatus.STARTING) updateState(ConnectionStatus.VALIDATING, attemptId)
            }
            // Cloudflare registration/provisioning rejections (identity or
            // API refused, rate-limited). These keep retrying inside
            // libaether; record the phase so the controller can bound it.
            protocol == AetherProtocol.MASQUE && (lower.contains("cloudflare refused this network") || lower.contains("the saved masque identity was refused") ||
                lower.contains("cloudflare no longer accepts the saved identity") || lower.contains("no camouflaged route") ||
                lower.contains("every camouflaged route failed") || lower.contains("too many registrations")) -> {
                bootstrapPhase = "masque_registration_failed"
                LogRepository.w("[AetherCore] MASQUE bootstrap registration problem: $line", "AetherCore")
            }
            lower.contains("validating") -> {
                if (_connectionStatus.value == ConnectionStatus.STARTING) updateState(ConnectionStatus.VALIDATING, attemptId)
            }
            lower.contains("tls established") || lower.contains("tls handshake complete") -> {
                if (protocol == AetherProtocol.MASQUE) updateState(ConnectionStatus.VALIDATING, attemptId)
                else if (protocol == AetherProtocol.WG) updateState(ConnectionStatus.VALIDATING, attemptId)
            }
            lower.contains("connect-ip status: 200") || lower.contains("connect-ip established") -> {
                quickRetryPending.set(false)
                dataPlaneOk = true
                updateState(ConnectionStatus.DATAPLANE_VALIDATED, attemptId)
            }
            protocol == AetherProtocol.GOOL && lower.contains("tunnel validated") -> {
                if (lower.contains("outer") && lower.contains("tunnel validated")) {
                    goolOuterValidated = true
                    updateState(ConnectionStatus.VALIDATING, attemptId)
                } else if (lower.contains("inner") && lower.contains("tunnel validated") && goolOuterValidated) {
                    quickRetryPending.set(false)
                    dataPlaneOk = true
                    updateState(ConnectionStatus.DATAPLANE_VALIDATED, attemptId)
                }
            }
            lower.contains("tunnel validated") || lower.contains("data-plane verification passed") || lower.contains("data plane verification passed") -> {
                if (protocol != AetherProtocol.GOOL) {
                    quickRetryPending.set(false)
                    dataPlaneOk = true
                    // MASQUE: libaether logs "socks5 listening on" BEFORE
                    // validation but never again after "tunnel validated ...
                    // exposing socks5" — promote straight to SOCKS_READY when
                    // the listener is already known-bound (or this very line
                    // says socks5 is being exposed) so the state is not
                    // stranded at DATAPLANE_VALIDATED.
                    if (socksListeningSeen || lower.contains("exposing socks5")) {
                        updateState(ConnectionStatus.SOCKS_READY, attemptId)
                    } else {
                        updateState(ConnectionStatus.DATAPLANE_VALIDATED, attemptId)
                    }
                }
            }
            lower.contains("socks") && lower.contains("listening") -> {
                socksListeningSeen = true
                if (dataPlaneOk) {
                    updateState(ConnectionStatus.SOCKS_READY, attemptId)
                } else {
                    LogRepository.i("[AetherCore] socks listening before data-plane validation; deferring SOCKS_READY", "AetherCore")
                }
            }

            lower.contains("reconnecting") || isLost -> {
                if (isReconnecting.compareAndSet(false, true)) {
                    goolOuterValidated = false
                    dataPlaneOk = false
                    SocksGate.setReady(SocksReadiness.NOT_READY)
                    if (PsiphonController.isConnected() && quickRetryPending.compareAndSet(false, true)) {
                        LogRepository.i("[AetherCore] Cached gateway lost; scheduling single 3s quick retry (Psiphon up)", "AetherCore")
                    }
                    updateState(ConnectionStatus.RECONNECTING, attemptId)
                    scope.launch {
                        delay(100.milliseconds)
                        isReconnecting.set(false)
                    }
                }
            }
            isCriticalError -> {
                val current = _connectionStatus.value
                if (current != ConnectionStatus.RUNNING && current != ConnectionStatus.RECONNECTING) {
                    updateState(ConnectionStatus.ERROR, attemptId)
                }
            }
        }
    }

    private fun updateState(state: ConnectionStatus, attemptId: Long = currentAttemptId.get()) {
        if (currentAttemptId.get() == attemptId) {
            _connectionStatus.value = state
        }
    }

    private fun writeRoutingFile(config: AetherConfig): java.io.File? {
        val rules = config.routingRules
        val block = rules.filter { it.mode == RoutingMode.BLOCK }

        if (block.isEmpty()) return null

        return try {
            val file = java.io.File(context.filesDir, "routing.ast")
            val content = StringBuilder()

            if (block.isNotEmpty()) {
                content.append("[block]\n")
                block.forEach { content.append(formatRoutingPattern(it.pattern)).append("\n") }
                content.append("\n")
            }

            file.writeText(content.toString())
            file
        } catch (e: Exception) {
            LogRepository.e("Failed to write routing file: ${e.localizedMessage}")
            null
        }
    }

    private fun formatRoutingPattern(pattern: String): String {
        val trimmed = pattern.trim()
        if (trimmed.startsWith("domain:") || trimmed.startsWith("ip:") || 
            trimmed.startsWith("keyword:") || trimmed.startsWith("regexp:") ||
            trimmed == "private") {
            return trimmed
        }

        val isIp = trimmed.all { it.isDigit() || it == '.' || it == ':' || it == '/' || (it.lowercaseChar() in 'a'..'f') } &&
                (trimmed.contains('.') || trimmed.contains(':'))
        
        return if (isIp) "ip:$trimmed" else "domain:$trimmed"
    }

    fun stop() {
        currentAttemptId.incrementAndGet()
        _connectionStatus.value = ConnectionStatus.STOPPED

        var jobToCancel: Job? = null
        var procToDestroy: Process? = null

        synchronized(lock) {
            jobToCancel = runnerJob
            procToDestroy = process
            runnerJob = null
            process = null
        }

        jobToCancel?.cancel()
        try {
            procToDestroy?.destroyForcibly()
        } catch (_: Exception) {}

        LogRepository.i("System core shutdown initiated.")
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
