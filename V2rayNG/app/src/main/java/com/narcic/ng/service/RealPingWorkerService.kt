package com.narcic.ng.service

import android.content.Context
import com.narcic.ng.core.AetherDelayTester
import com.narcic.ng.core.CoreConfigManager
import com.narcic.ng.core.CoreNativeManager
import com.narcic.ng.dto.RealPingEvent
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.extension.isNotNullEmpty
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.handler.SpeedtestManager
import com.narcic.ng.rsta.NarcisSpoofConfig
import com.narcic.ng.rsta.NarcisSpoofEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = if (onlyTcp) startTcping(guid) else startRealPing(guid)
                    onEvent(RealPingEvent.Result(guid, result))
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    onEvent(RealPingEvent.Progress("$left / $count"))
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                onEvent(RealPingEvent.Finish("0"))
            } catch (_: CancellationException) {
                onEvent(RealPingEvent.Finish("-1"))
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private suspend fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.AETHER) {
            return AetherDelayTester.measure(context, guid, config, SettingsManager.getDelayTestUrl())
        }

        // نرسیس اسپوف: configs pointing at 127.0.0.1:<LISTEN_PORT> need the local
        // spoof engine alive, otherwise the ping always fails and the user can't
        // tell a working config from a dead one. Start it (idempotent — no-op if
        // already running with the same settings) before measuring.
        val isSpoofConfig = NarcisSpoofConfig.isSpoofTarget(config.server, config.serverPort)
        if (isSpoofConfig) {
            if (!NarcisSpoofConfig.enabled()) return retFailure
            NarcisSpoofEngine.start(
                NarcisSpoofConfig.connectIp(),
                NarcisSpoofConfig.connectPort(),
                NarcisSpoofConfig.fakeSni(),
                NarcisSpoofConfig.method()
            )
            // give the native listener a beat to bind before the first dial
            Thread.sleep(150)
        }

        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
            && !isSpoofConfig   // tcping to 127.0.0.1 is meaningless for spoof configs
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
    }

    private suspend fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (config.configType == EConfigType.AETHER) {
            return AetherDelayTester.reachability(config)
        }

        // Spoof configs: a raw TCP connect to 127.0.0.1 only measures the local
        // listener, not the real remote. Route through the real-ping path instead
        // (which boots the engine and measures the actual outbound delay).
        if (NarcisSpoofConfig.isSpoofTarget(config.server, config.serverPort)) {
            return startRealPing(guid)
        }

        if (!config.configType.isComplexType()
            && config.configType != EConfigType.HYSTERIA2
            && config.configType != EConfigType.WIREGUARD
            && config.alpn?.startsWith("h3") != true
            && config.server.isNotNullEmpty()
            && config.serverPort?.toIntOrNull() != null
        ) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)

            return tcpTime
        }

        return retFailure
    }
}
