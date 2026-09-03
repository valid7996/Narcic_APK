package com.narcic.ng.won

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.compositionLocalOf

/**
 * Glue between the Compose UI and [WonVpnService].
 *
 * The service itself is a straight port of MSN-GUARD's MsnGuardVpnService; this
 * object is new and exists because Narcic's UI is Compose and its connection
 * entry points differ: the W on N card starts THIS service (never
 * CoreVpnService), and Android's VPN consent has to be requested from an
 * Activity, so the actual `VpnService.prepare` call is handed over through
 * [LocalWonVpnPreparer], which MainActivity provides.
 */

/** The six connection modes offered by the W on N card, in UI order. */
enum class WonMode(val key: String, val label: String) {
    MASQUE("masque", "Masque"),
    WIREGUARD("wireguard", "WireGuard"),
    WOW("gool", "WoW"),
    PSIPHON("psiphon", "Psiphon"),
    TOR("tor", "Tor"),
    PSIPHON_OVER_WARP(WonVpnService.CHAIN_PROTOCOL_MARKER.lowercase(), "PSIPHON OVER WARP");

    companion object {
        fun fromKey(key: String?): WonMode? = entries.firstOrNull { it.key == key }
    }
}

object WonController {

    /** Config waiting for the VPN consent dialog to come back OK. */
    @Volatile
    internal var pendingConfig: String? = null

    /** Build the Rust-core / service config JSON for [mode] from current settings. */
    fun configFor(context: Context, mode: WonMode): String =
        WonConfig.json(context, mode.key)

    fun connect(context: Context, configJson: String) {
        val intent = Intent(context, WonVpnService::class.java)
            .setAction(WonVpnService.ACTION_CONNECT)
            .putExtra(WonVpnService.EXTRA_CONFIG, configJson)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect(context: Context) {
        val intent = Intent(context, WonVpnService::class.java)
            .setAction(WonVpnService.ACTION_DISCONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun consumePendingConfig(): String? {
        val config = pendingConfig
        pendingConfig = null
        return config
    }
}

/** Provided by MainActivity: runs VpnService.prepare and connects on OK. */
val LocalWonVpnPreparer = compositionLocalOf<(String) -> Unit> { { _ -> } }
