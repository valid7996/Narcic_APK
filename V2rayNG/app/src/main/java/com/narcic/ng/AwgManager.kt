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

    /**
     * Cleans up text pasted from Telegram bots / websites / other apps before it ever reaches
     * the AmneziaWG config parser. Stray carriage returns, trailing spaces, a leading BOM, or
     * invisible zero-width characters copied along with a key are all "well-formed enough" to
     * look fine to the human eye but break base64 key decoding with
     * org.amnezia.awg.crypto.KeyFormatException. This is defensive and idempotent — safe to
     * call on text that's already clean.
     */
    fun sanitizeConfigText(rawText: String): String {
        return rawText
            .removePrefix("\uFEFF") // BOM some editors/bots prepend
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { line ->
                line
                    .replace("\u200B", "").replace("\u200C", "")
                    .replace("\u200D", "").replace("\u2060", "")
                    .trim()
            }
            .trim()
    }

    /**
     * Creates (and starts binding) the GoBackend well before the user taps Connect.
     * GoBackend binds to its internal AmneziaWG VpnService asynchronously; calling
     * setState(UP) before that binding finishes throws on the very first attempt right
     * after the object is constructed — which is why the first tap failed with
     * "AmneziaWG connect failed" while the second tap (backend already bound) worked.
     * Call this once, early (e.g. app startup), so the backend is ready by the time the
     * user actually connects.
     */
    fun preload(context: Context) {
        try {
            ensureBackend(context)
        } catch (e: Exception) {
            Log.w(TAG, "AmneziaWG backend preload failed, will retry on demand", e)
        }
    }

    private fun ensureBackend(context: Context): GoBackend {
        return backend ?: GoBackend(context.applicationContext).also { backend = it }
    }

    /** Returns null on success, or an error message on failure. */
    fun connect(context: Context, rawConfigText: String): String? {
        val cleanedText = sanitizeConfigText(rawConfigText)
        val config = try {
            Config.parse(BufferedReader(StringReader(cleanedText)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AmneziaWG config", e)
            return e.message ?: "AmneziaWG config is invalid"
        }

        val wasBackendAlreadyReady = backend != null
        return try {
            startTunnel(context, config)
        } catch (e: Exception) {
            Log.w(TAG, "AmneziaWG connect attempt failed, retrying once", e)
            // Most first-attempt failures are the backend's internal VpnService still binding
            // (see preload() doc above). Give it a moment and retry once before giving up.
            if (wasBackendAlreadyReady) {
                // Backend was already up, so this wasn't a binding race — don't mask a real error.
                Log.e(TAG, "Failed to start AmneziaWG tunnel", e)
                return e.message ?: "AmneziaWG connect failed"
            }
            try {
                Thread.sleep(400)
                startTunnel(context, config)
            } catch (retryError: Exception) {
                Log.e(TAG, "Failed to start AmneziaWG tunnel after retry", retryError)
                retryError.message ?: "AmneziaWG connect failed"
            }
        }
    }

    private fun startTunnel(context: Context, config: Config): String? {
        val goBackend = ensureBackend(context)
        val tunnel = SimpleTunnel(TUNNEL_NAME)
        currentTunnel = tunnel
        goBackend.setState(tunnel, Tunnel.State.UP, config)
        return null
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

    /**
     * Total tunnel rx/tx bytes since it came up, or null if not running / not available yet.
     * Backed by org.amnezia.awg.backend.Backend#getStatistics, confirmed against upstream
     * source (tunnel/src/main/java/org/amnezia/awg/backend/Statistics.java): totalRx()/totalTx()
     * sum bytes across all peers tracked for the tunnel.
     */
    fun getStatistics(): org.amnezia.awg.backend.Statistics? {
        val goBackend = backend ?: return null
        val tunnel = currentTunnel ?: return null
        return try {
            goBackend.getStatistics(tunnel)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read AmneziaWG statistics", e)
            null
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
