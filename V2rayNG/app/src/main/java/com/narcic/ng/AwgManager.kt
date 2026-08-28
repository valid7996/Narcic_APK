package com.narcic.ng.awg

import android.content.Context
import android.net.VpnService
import android.util.Log
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * Thin bridge between our app and the AmneziaWG native engine (org.amnezia.awg).
 * This is a completely separate connection path from CoreVpnService/Xray-core —
 * used only when the selected profile is an AmneziaWG config (detected by the
 * presence of Jc/Jmin/Jmax/H1-H4/I1-I5 fields in the pasted .conf text).
 *
 * Only one of CoreVpnService or GoBackend.VpnService can be the active Android
 * VPN at a time; callers must make sure the other engine is stopped first.
 */
object AwgManager {

    private const val TAG = "AwgManager"
    private const val TUNNEL_NAME = "narcic-awg"

    private var backend: GoBackend? = null
    private var currentTunnel: SimpleTunnel? = null

    /** True if the raw config text looks like an AmneziaWG config (not a plain WireGuard one). */
    fun isAmneziaWgConfig(rawText: String): Boolean {
        if (!rawText.contains("[Interface]")) return false
        return Regex("(?m)^\\s*(Jc|Jmin|Jmax|H1|H2|H3|H4|I1|I2|I3|I4|I5)\\s*=").containsMatchIn(rawText)
    }

    private fun ensureBackend(context: Context): GoBackend {
        return backend ?: GoBackend(context.applicationContext).also { backend = it }
    }

    /** Returns null on success, or an error message on failure. */
    fun connect(context: Context, rawConfigText: String): String? {
        return try {
            val config = Config.parse(BufferedReader(StringReader(rawConfigText)))
            val goBackend = ensureBackend(context)
            val tunnel = SimpleTunnel(TUNNEL_NAME)
            currentTunnel = tunnel
            goBackend.setState(tunnel, Tunnel.State.UP, config)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AmneziaWG tunnel", e)
            e.message ?: "AmneziaWG connect failed"
        }
    }

    fun disconnect(): String? {
        val goBackend = backend ?: return null
        val tunnel = currentTunnel ?: return null
        return try {
            goBackend.setState(tunnel, Tunnel.State.DOWN, null)
            currentTunnel = null
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AmneziaWG tunnel", e)
            e.message ?: "AmneziaWG disconnect failed"
        }
    }

    fun isRunning(): Boolean {
        val goBackend = backend ?: return false
        val tunnel = currentTunnel ?: return false
        return try {
            goBackend.getState(tunnel) == Tunnel.State.UP
        } catch (e: Exception) {
            false
        }
    }

    /** Standard Android VPN permission check, same pattern as VpnService.prepare(). */
    fun prepare(context: Context) = VpnService.prepare(context)

    private class SimpleTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) {
            Log.i(TAG, "AmneziaWG tunnel state changed: $newState")
        }
    }
}
