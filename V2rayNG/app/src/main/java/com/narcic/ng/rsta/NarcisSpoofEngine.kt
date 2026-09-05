package com.narcic.ng.rsta

import com.narcic.ng.AppConfig
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.util.LogUtil
import com.rstagit.androidasdd.core.protocol.GoNativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Lifecycle + status manager for the «نرسیس اسپوف» (Narcis Spoof) native engine.
 * Thin Kotlin wrapper around [GoNativeBridge], the JNI bridge to the bundled
 * Go SNI-spoof/TLS-fragmentation library.
 */
object NarcisSpoofEngine {

    private const val LOG_BUFFER_MAX_LINES = 200
    private const val MMKV_SESSION_ID = "narcis_spoof_session_id"
    private const val MMKV_LAST_IP = "narcis_spoof_last_ip"
    private const val MMKV_LAST_PORT = "narcis_spoof_last_port"
    private const val MMKV_LAST_SNI = "narcis_spoof_last_sni"
    private const val MMKV_LAST_METHOD = "narcis_spoof_last_method"
    private const val MMKV_LAST_ERROR = "narcis_spoof_last_error"
    private const val MMKV_LOG_BUFFER = "narcis_spoof_log_buffer"

    /** RFC-1123-ish hostname (labels of letters/digits/hyphen, dot-separated). */
    private val HOSTNAME_REGEX = Regex("""^(?=.{1,253}${'$'})(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63}${'$'}""")

    private var localSessionId: Long = 0L

    private var pollJob: Job? = null
    private val pollScope = CoroutineScope(Dispatchers.IO)

    private val logBuffer = ArrayDeque<String>()
    private val logLock = Any()

    val isRunning: Boolean
        get() = MmkvManager.decodeSettingsLong(MMKV_SESSION_ID, 0L) != 0L

    fun isAvailable(): Boolean = try {
        GoNativeBridge.isAvailable()
    } catch (_: Throwable) {
        false
    }

    /**
     * Validates the connection parameters before they reach the native
     * bridge. Returns null when the inputs are usable, otherwise a short
     * human-readable reason. Mirrors the clamping [NarcisSpoofConfig] applies
     * on its own read path so an intent replay can't smuggle ":0"/empty SNI
     * into spfStart.
     */
    fun validate(ip: String, port: Int, sni: String, method: String): String? {
        if (ip.isBlank()) return "remote IP is empty"
        val ipOk = android.util.Patterns.IP_ADDRESS.matcher(ip).matches() ||
            HOSTNAME_REGEX.matches(ip)
        if (!ipOk) return "remote IP/hostname is not valid: $ip"
        if (port !in 1..65535) return "remote port out of range: $port"
        if (sni.isBlank()) return "fake SNI is empty"
        if (!HOSTNAME_REGEX.matches(sni)) return "fake SNI is not a valid hostname: $sni"
        if (method !in NarcisSpoofConfig.METHODS) return "unknown method: $method"
        return null
    }

    /** Public hook so the service can record rejected intents in the shared log. */
    fun appendPublicLog(line: String) = appendLog(line)

    fun statusSummary(): String {
        if (isRunning) {
            val ip = MmkvManager.decodeSettingsString(MMKV_LAST_IP, "")
            val port = MmkvManager.decodeSettingsInt(MMKV_LAST_PORT, 0)
            val sni = MmkvManager.decodeSettingsString(MMKV_LAST_SNI, "")
            val method = MmkvManager.decodeSettingsString(MMKV_LAST_METHOD, "")
            return "active \u2192 $ip:$port (sni=$sni, method=$method)"
        } else {
            val error = MmkvManager.decodeSettingsString(MMKV_LAST_ERROR, "")
            return if (error.isNullOrEmpty()) "inactive" else "failed: $error"
        }
    }

    @Synchronized
    fun start(connectIp: String, connectPort: Int, fakeSni: String, method: String): Boolean {
        // Defensive re-validation: the service path validates too, but start()
        // is also callable directly (CoreServiceManager, RealPingWorker).
        validate(connectIp, connectPort, fakeSni, method)?.let { reason ->
            val err = "rejected: $reason"
            LogUtil.e(AppConfig.TAG, "NarcisSpoof: $err")
            MmkvManager.encodeSettings(MMKV_LAST_ERROR, err)
            appendLog(err)
            return false
        }

        val currentIp = MmkvManager.decodeSettingsString(MMKV_LAST_IP, "")
        val currentPort = MmkvManager.decodeSettingsInt(MMKV_LAST_PORT, 0)
        val currentSni = MmkvManager.decodeSettingsString(MMKV_LAST_SNI, "")
        val currentMethod = MmkvManager.decodeSettingsString(MMKV_LAST_METHOD, "")

        // Cross-process idempotency: another process (main app, VPN daemon,
        // test daemon) may already be running this engine with the same
        // settings — the MMKV session id is the shared source of truth.
        // localSessionId only tracks a session started in THIS process.
        if (localSessionId == 0L && MmkvManager.decodeSettingsLong(MMKV_SESSION_ID, 0L) != 0L &&
            currentIp == connectIp && currentPort == connectPort &&
            currentSni == fakeSni && currentMethod == method
        ) {
            // Already running elsewhere with identical settings; adopting it
            // locally would double-stop it later, so just report success.
            return true
        }

        if (localSessionId != 0L) {
            if (currentIp == connectIp && currentPort == connectPort &&
                currentSni == fakeSni && currentMethod == method
            ) {
                return true
            }
            stop()
        }

        if (!isAvailable()) {
            val err = "Native engine unavailable. Load error: ${GoNativeBridge.getLoadError()?.message ?: "unknown"}"
            LogUtil.e(AppConfig.TAG, "NarcisSpoof: $err")
            MmkvManager.encodeSettings(MMKV_LAST_ERROR, err)
            appendLog(err)
            return false
        }

        return try {
            val remote = "$connectIp:$connectPort"
            LogUtil.i(AppConfig.TAG, "NarcisSpoof: Starting engine for $remote (sni=$fakeSni, method=$method)")

            val id = GoNativeBridge.spfStart(NarcisSpoofConfig.listenPort, remote, fakeSni, method)
            if (id == 0L) {
                val err = "spfStart returned 0 (failed). Port ${NarcisSpoofConfig.listenPort} might be in use or native bridge failed."
                LogUtil.e(AppConfig.TAG, "NarcisSpoof: $err for $remote.")
                MmkvManager.encodeSettings(MMKV_LAST_ERROR, err)
                appendLog(err)
                false
            } else {
                localSessionId = id
                clearLog()
                MmkvManager.encodeSettings(MMKV_SESSION_ID, id)
                MmkvManager.encodeSettings(MMKV_LAST_IP, connectIp)
                MmkvManager.encodeSettings(MMKV_LAST_PORT, connectPort)
                MmkvManager.encodeSettings(MMKV_LAST_SNI, fakeSni)
                MmkvManager.encodeSettings(MMKV_LAST_METHOD, method)
                MmkvManager.encodeSettings(MMKV_LAST_ERROR, "")

                appendLog("started: listen=127.0.0.1:${NarcisSpoofConfig.listenPort} -> $remote sni=$fakeSni method=$method")
                startPolling()
                true
            }
        } catch (t: Throwable) {
            val err = t.message ?: t.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "NarcisSpoof: failed to start", t)
            MmkvManager.encodeSettings(MMKV_LAST_ERROR, err)
            appendLog("failed to start: $err")
            false
        }
    }

    @Synchronized
    fun stop() {
        val id = localSessionId
        localSessionId = 0L
        MmkvManager.encodeSettings(MMKV_SESSION_ID, 0L)

        stopPolling()
        if (id != 0L) {
            try {
                GoNativeBridge.spfStop(id)
                appendLog("stopped")
            } catch (t: Throwable) {
                LogUtil.e(AppConfig.TAG, "NarcisSpoof: failed to stop cleanly", t)
            }
        }
    }

    fun version(): String = try {
        if (isAvailable()) GoNativeBridge.spfVersion() ?: "unknown" else "unavailable"
    } catch (_: Throwable) {
        "unknown"
    }

    /**
     * Log lines are persisted through MMKV (multi-process mode) because the
     * engine runs in :NarcisSpoofProcess while the settings screen runs in the
     * main process — an in-memory buffer would always look empty there.
     */
    fun recentLogLines(): List<String> =
        MmkvManager.decodeSettingsString(MMKV_LOG_BUFFER, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun appendLog(line: String) {
        synchronized(logLock) {
            logBuffer.addLast(line)
            while (logBuffer.size > LOG_BUFFER_MAX_LINES) {
                logBuffer.removeFirst()
            }
            MmkvManager.encodeSettings(MMKV_LOG_BUFFER, logBuffer.joinToString("\n"))
        }
    }

    private fun clearLog() {
        synchronized(logLock) {
            logBuffer.clear()
            MmkvManager.encodeSettings(MMKV_LOG_BUFFER, "")
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = pollScope.launch {
            while (isRunning) {
                try {
                    val line = GoNativeBridge.spfPollLog()
                    if (!line.isNullOrEmpty()) {
                        appendLog(line)
                    }
                } catch (_: Throwable) {
                    // ignore
                }
                delay(500)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
