package com.narcic.ng.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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

        // نرسیس اسپوف: configs pointing at the local spoof listener need the
        // engine alive before measuring. The engine's native listener is owned
        // by :NarcisSpoofProcess (the service), so ask the SERVICE to start —
        // not the in-process engine — otherwise two processes race to bind the
        // same port and one spfStart silently loses. Wait for the MMKV
        // isRunning flag (shared, MULTI_PROCESS_MODE) before measuring.
        val isSpoofConfig = NarcisSpoofConfig.isSpoofTarget(config.server, config.serverPort)
        if (isSpoofConfig) {
            if (!NarcisSpoofConfig.enabled()) return retFailure
            val startIntent = Intent(context, NarcisSpoofService::class.java).apply {
                action = "START"
                putExtra("IP", NarcisSpoofConfig.connectIp())
                putExtra("PORT", NarcisSpoofConfig.connectPort())
                putExtra("SNI", NarcisSpoofConfig.fakeSni())
                putExtra("METHOD", NarcisSpoofConfig.method())
            }
            ContextCompat.startForegroundService(context, startIntent)

            // Wait (cancellable) for the shared MMKV session flag to flip on,
            // bounded so a dead engine doesn't stall the whole test batch.
            val deadline = System.currentTimeMillis() + 3_000L
            while (!NarcisSpoofEngine.isRunning && System.currentTimeMillis() < deadline && currentCoroutineContext().isActive) {
                delay(100)
            }
            if (!NarcisSpoofEngine.isRunning) {
                return retFailure
            }
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

    private fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure

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
