package com.narcic.ng.core

import android.content.Context
import com.narcic.ng.AppConfig
import com.narcic.ng.aether.service.AetherVpnService
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Phase 1 engine isolation: bounded, observable handoff between the three
 * mutually exclusive VPN engines (V2Ray / AmneziaWG / Aether, Psiphon chain
 * included in Aether). Android grants one TUN interface per app, so before an
 * engine takes ownership the previous engine must actually finish tearing
 * down — a fire-and-forget stop is not enough: CoreVpnService.establish()
 * fails while Aether still holds the TUN, and device traffic keeps egressing
 * through the old engine's chain.
 *
 * Liveness is signaled through MMKV flags (MULTI_PROCESS_MODE) because the
 * engines live in different processes: the V2Ray services run in
 * :RunSoLibV2RayDaemon, Aether in the main process, AWG in-process.
 *
 * Every wait is bounded; on timeout the caller receives an error message and
 * MUST NOT start its engine (never two TUN engines at once).
 */
object EngineHandoff {

    enum class Engine { V2RAY, AWG, AETHER }

    const val DEFAULT_TIMEOUT_MS = 5000L
    private const val POLL_INTERVAL_MS = 100L

    private const val KEY_V2RAY_ALIVE = "engine_alive_v2ray"
    private const val KEY_AETHER_ALIVE = "engine_alive_aether"

    // region liveness flags — written only by the owning engine's lifecycle

    fun setV2RayAlive(alive: Boolean) {
        MmkvManager.encodeSettings(KEY_V2RAY_ALIVE, alive)
    }

    fun setAetherAlive(alive: Boolean) {
        MmkvManager.encodeSettings(KEY_AETHER_ALIVE, alive)
    }

    fun isV2RayAlive(): Boolean = MmkvManager.decodeSettingsBool(KEY_V2RAY_ALIVE, false)

    fun isAetherAlive(): Boolean = MmkvManager.decodeSettingsBool(KEY_AETHER_ALIVE, false)

    // endregion

    /**
     * Stop and settle every engine except [keep] before it takes TUN ownership.
     * The whole handoff shares ONE deadline, so the total wait is bounded by
     * [timeoutMs] no matter how many engines are being stopped. Returns null
     * on success, or a human-readable error when an engine failed to release
     * within its bounded timeout — the caller MUST NOT start the new engine
     * in that case.
     */
    suspend fun releaseFor(keep: Engine, context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val errors = mutableListOf<String>()
        if (keep != Engine.V2RAY) {
            stopV2Ray(context, deadline)?.let { errors.add(it) }
        }
        if (keep != Engine.AETHER) {
            stopAether(context, deadline)?.let { errors.add(it) }
        }
        if (keep != Engine.AWG) {
            stopAwg()?.let { errors.add(it) }
        }
        return errors.joinToString("\n").ifEmpty { null }
    }

    /** Blocking variant for callers that cannot suspend (QS tile, receivers). */
    fun releaseForBlocking(keep: Engine, context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String? =
        runBlocking { releaseFor(keep, context, timeoutMs) }

    /**
     * Stop the V2Ray daemon and wait until it reports teardown complete
     * (CoreServiceManager clears the liveness flag right after
     * MSG_STATE_STOP_SUCCESS).
     */
    private suspend fun stopV2Ray(context: Context, deadline: Long): String? {
        if (!isV2RayAlive()) return null
        LauncherManager.stopService(context)
        return awaitFlagDown("V2Ray", ::isV2RayAlive, deadline)
    }

    /**
     * Stop Aether (including any active Psiphon chain) and wait until its
     * teardown completes (AetherVpnService clears the liveness flag at the
     * end of stopVpnService / in onDestroy).
     */
    private suspend fun stopAether(context: Context, deadline: Long): String? {
        if (!isAetherAlive()) return null
        val sent = runCatching { AetherVpnService.stopVpn(context) }.getOrDefault(false)
        if (!sent) {
            // Stale flag (service already dead) — clear it so future handoffs
            // are not blocked by a corpse.
            LogUtil.w(AppConfig.TAG, "EngineHandoff: Aether stop intent not delivered; clearing stale flag")
            setAetherAlive(false)
            return null
        }
        return awaitFlagDown("Aether", ::isAetherAlive, deadline)
    }

    /**
     * AWG already settles synchronously inside disconnect() (its own CAS +
     * settle deadline); just run it off the main thread and verify.
     */
    private suspend fun stopAwg(): String? = withContext(Dispatchers.IO) {
        val running = runCatching { com.narcic.ng.awg.AwgManager.isRunning() }.getOrDefault(false)
        if (!running) return@withContext null
        val result = runCatching { com.narcic.ng.awg.AwgManager.disconnect() }
        val error = result.exceptionOrNull()?.message ?: result.getOrNull()
        if (error != null) {
            LogUtil.e(AppConfig.TAG, "EngineHandoff: AWG disconnect failed: $error")
            return@withContext error
        }
        null
    }

    private suspend fun awaitFlagDown(name: String, flag: () -> Boolean, deadline: Long): String? {
        while (System.currentTimeMillis() < deadline) {
            if (!flag()) return null
            delay(POLL_INTERVAL_MS)
        }
        LogUtil.e(AppConfig.TAG, "EngineHandoff: $name did not settle in time")
        return "$name هنوز در حال خاموش شدن است؛ چند لحظه بعد دوباره تلاش کنید"
    }
}
