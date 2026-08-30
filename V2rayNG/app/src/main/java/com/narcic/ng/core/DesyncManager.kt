package com.narcic.ng.core

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils

/**
 * Runs the embedded, rootless desync engine (see [DesyncCompat]) in the app's process.
 *
 * Call [start] before building the Xray config so [DesyncCompat.attachOutbound] has a
 * live port to dial through, and [stop] when the core loop stops.
 */
object DesyncManager {
    @Volatile
    private var port: Int = 0
    private var engine: DesyncNativeBridge? = null
    private var worker: Thread? = null

    fun activePort(): Int = port

    /** Starts the engine for [profile] on a free local port. Returns 0 if desync is off/unsupported. */
    @Synchronized
    fun start(profile: ProfileItem): Int {
        stopLocked()
        if (!DesyncCompat.isEnabled(profile)) return 0

        val selectedPort = Utils.findRandomFreePort()
        val command = DesyncCompat.buildCommandLine(profile, selectedPort)
            ?: error("No desync command is available for profile '${profile.desyncProfile}'")

        val bridge = DesyncNativeBridge()
        try {
            bridge.prepare(command.toTypedArray())
        } catch (error: Throwable) {
            LogUtil.e(AppConfig.TAG, "Desync engine rejected the command line", error)
            throw error
        }

        engine = bridge
        port = selectedPort
        worker = Thread({
            val result = try {
                bridge.runLoop()
            } catch (e: Throwable) {
                LogUtil.e(AppConfig.TAG, "Desync engine failed", e)
                -1
            }
            synchronized(this) {
                if (engine === bridge) {
                    engine = null
                    worker = null
                    port = 0
                }
            }
            if (result != 0) {
                LogUtil.e(AppConfig.TAG, "Desync engine stopped with code $result")
            }
        }, "Narcic-Desync").apply {
            isDaemon = true
            start()
        }

        LogUtil.i(AppConfig.TAG, "Desync engine started on 127.0.0.1:$selectedPort (${profile.desyncProfile})")
        return selectedPort
    }

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        val bridge = engine
        engine = null
        worker = null
        port = 0
        if (bridge != null) {
            try {
                bridge.stop()
            } catch (e: Throwable) {
                LogUtil.e(AppConfig.TAG, "Failed to stop desync engine", e)
            }
        }
    }
}
