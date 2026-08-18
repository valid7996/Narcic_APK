package com.narcic.ng.service

import android.content.Context
import com.narcic.ng.core.CoreConfigManager
import com.narcic.ng.core.CoreNativeManager
import com.narcic.ng.dto.RealPingEvent
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.extension.isComplexType
import com.narcic.ng.extension.isNotNullEmpty
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.handler.SettingsManager
import com.narcic.ng.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    companion object {
        // Ceiling for a single real-ping test (mainly guards Custom/PolicyGroup/ProxyChain
        // configs, which skip the fast TCP pre-check). Generous enough for a slow-but-
        // working server, short enough that one bad config can't stall the whole batch.
        private const val REAL_PING_TIMEOUT_MS = 10_000L
    }

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
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }

        // measureOutboundDelay is a blocking native call with no timeout of its own.
        // Custom/PolicyGroup/ProxyChain configs (and protocols like Hysteria2/WireGuard/h3)
        // skip the quick TCP pre-check above, so an unreachable or slow one can hang here
        // far longer than a normal test. Run it on Dispatchers.IO and bound it with a
        // timeout so a single stuck config occupies a throwaway IO thread instead of a
        // slot in this batch's fixed-size thread pool — otherwise every config still
        // queued behind it never gets a thread to run on and never gets tested.
        return withTimeoutOrNull(REAL_PING_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
            }
        } ?: retFailure
    }

    private fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
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
