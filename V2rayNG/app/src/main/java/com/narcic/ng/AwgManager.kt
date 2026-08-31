package com.narcic.ng.awg

import android.content.Context
import android.net.VpnService
import android.util.Log
import org.amnezia.awg.backend.BackendException
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
        val keyFieldRegex = Regex("(?i)^(\\s*(?:PrivateKey|PublicKey|PresharedKey)\\s*=\\s*)(\\S+)(\\s*)$")
        return rawText
            .removePrefix("\uFEFF") // BOM some editors/bots prepend
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { rawLine ->
                val line = rawLine
                    .replace("\u200B", "").replace("\u200C", "")
                    .replace("\u200D", "").replace("\u2060", "")
                    .trim()
                // Some config sources (bots/websites) emit URL-safe base64 (-/_) for keys
                // instead of standard base64 (+//), which parses as a well-formed-looking
                // string but fails Key.fromBase64's strict alphabet check with
                // KeyFormatException. Normalize just the key's value, never the rest of the
                // line/config (endpoints/hostnames legitimately contain '-').
                val match = keyFieldRegex.find(line)
                if (match != null) {
                    val (prefix, value, suffix) = match.destructured
                    prefix + value.replace('-', '+').replace('_', '/') + suffix
                } else {
                    line
                }
            }
            .trim()
    }

    /**
     * Gets the GoBackend fully ready — native library loaded AND its internal
     * org.amnezia.awg.backend.GoBackend.VpnService already bound — well before the user taps
     * Connect.
     *
     * Verified against the actual upstream source (amnezia-vpn/amneziawg-android,
     * tunnel/src/main/java/org/amnezia/awg/backend/GoBackend.java): the GoBackend constructor
     * only loads the native wg-go library; it does NOT start or bind the internal VpnService.
     * That only happens the first time setState(UP) is called, via
     * `context.startService(Intent(context, VpnService::class))` followed by a hard
     * `vpnService.get(2, TimeUnit.SECONDS)` wait — if the service isn't fully up within exactly
     * 2 seconds (slow/cold-started process, aggressive OEM battery management, etc.) that call
     * throws BackendException(UNABLE_TO_START_VPN), which is exactly the "AmneziaWG connect
     * failed" on the first tap. The service is a completed static future after that, which is
     * why the very next tap connects instantly.
     *
     * Starting the same Intent ourselves here — early, at app startup — means that 2-second
     * window has almost certainly already elapsed by the time the user actually reaches for the
     * Connect button, so the real setState(UP) call finds the service already bound and returns
     * immediately instead of racing the timeout.
     */
    fun preload(context: Context) {
        try {
            ensureBackend(context)
            context.applicationContext.startService(
                android.content.Intent(context.applicationContext, GoBackend.VpnService::class.java)
            )
        } catch (e: Exception) {
            Log.w(TAG, "AmneziaWG backend preload failed, will retry on demand", e)
        }
    }

    private fun ensureBackend(context: Context): GoBackend {
        return backend ?: GoBackend(context.applicationContext).also { backend = it }
    }

    /**
     * Turns the library's structured BadConfigException (confirmed against upstream source:
     * org.amnezia.awg.config.BadConfigException — it carries exactly which field/section was
     * wrong and why) into a precise, actionable message, instead of the caller only ever seeing
     * a bare "org.amnezia.awg.crypto.KeyFormatException". Falls back to the raw message for any
     * other exception type.
     */
    fun friendlyConfigError(e: Exception): String {
        val badConfig = e as? org.amnezia.awg.config.BadConfigException
            ?: (e.cause as? org.amnezia.awg.config.BadConfigException)
            ?: return e.message ?: "AmneziaWG config is invalid"

        val field = badConfig.location.getName().takeIf { it.isNotBlank() } ?: badConfig.section.getName()
        return when (badConfig.reason) {
            org.amnezia.awg.config.BadConfigException.Reason.INVALID_KEY ->
                "$field key is not valid Base64. It must be exactly 44 characters and end with " +
                    "'='. Re-copy it — a missing/extra character or a hidden character from " +
                    "copy-paste is the usual cause."
            org.amnezia.awg.config.BadConfigException.Reason.INVALID_NUMBER ->
                "$field has an invalid number value" +
                    (badConfig.text?.let { ": '$it'" } ?: "")
            org.amnezia.awg.config.BadConfigException.Reason.MISSING_ATTRIBUTE ->
                "$field is required but missing from the config"
            org.amnezia.awg.config.BadConfigException.Reason.MISSING_SECTION ->
                "The config is missing a required [$field] section"
            org.amnezia.awg.config.BadConfigException.Reason.UNKNOWN_ATTRIBUTE ->
                "Unknown field in the config" + (badConfig.text?.let { ": '$it'" } ?: "")
            else -> "$field is invalid" + (badConfig.text?.let { ": '$it'" } ?: "")
        }
    }

    /** Returns null on success, or an error message on failure. */
    fun connect(context: Context, rawConfigText: String): String? {
        val cleanedText = sanitizeConfigText(rawConfigText)
        val config = try {
            Config.parse(BufferedReader(StringReader(cleanedText)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AmneziaWG config", e)
            return friendlyConfigError(e)
        }

        // The internal VpnService binding race (see preload() doc) is the dominant failure mode
        // on a fresh connect, and it resolves itself within a couple seconds once the service
        // finishes starting — it is not a "give up after one retry" situation on a slow device.
        // Retry a few times with backoff rather than failing the user's very first tap.
        var lastError: Exception? = null
        val delaysMs = longArrayOf(0, 300, 800, 1500)
        for ((attempt, delayMs) in delaysMs.withIndex()) {
            if (delayMs > 0) Thread.sleep(delayMs)
            try {
                return startTunnel(context, config)
            } catch (e: BackendException) {
                lastError = e
                if (e.reason == BackendException.Reason.VPN_NOT_AUTHORIZED) {
                    // Retrying won't help: the OS needs the consent dialog re-shown, which is
                    // handled by AwgManager.prepare()/the caller, not by trying again here.
                    Log.e(TAG, "AmneziaWG not authorized", e)
                    break
                }
                Log.w(TAG, "AmneziaWG connect attempt ${attempt + 1}/${delaysMs.size} failed", e)
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "AmneziaWG connect attempt ${attempt + 1}/${delaysMs.size} failed", e)
            }
        }
        Log.e(TAG, "Failed to start AmneziaWG tunnel after ${delaysMs.size} attempts", lastError)
        return lastError?.message ?: "AmneziaWG connect failed"
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
