package com.narcic.ng.rsta

import com.narcic.ng.AppConfig
import com.narcic.ng.handler.MmkvManager

/**
 * تنظیمات «نرسیس اسپوف» (Narcis Spoof).
 *
 * A local SNI-spoofing / TLS-fragmentation forwarder, ported from the RSTA Spoof
 * feature (https://github.com/rstagit/rstang, itself built on rstagit/rstaspoof).
 * It listens on [AppConfig.LOOPBACK]:[AppConfig.NARCIS_SPOOF_LISTEN_PORT] and
 * forwards the connection to a real remote IP:port while disguising the TLS
 * ClientHello (fake SNI, fragmentation, TTL tricks, etc.) to evade DPI-based
 * filtering. To use it, create a server entry pointing at that loopback
 * address/port — [CoreServiceManager] will detect it via [isSpoofTarget] and
 * spin the engine up automatically before the real core connects.
 */
object NarcisSpoofConfig {

    val LISTEN_HOST: String = AppConfig.LOOPBACK
    val LISTEN_PORT: Int = AppConfig.NARCIS_SPOOF_LISTEN_PORT

    const val DEFAULT_CONNECT_IP = "104.21.33.58"
    const val DEFAULT_CONNECT_PORT = "443"
    const val DEFAULT_FAKE_SNI = "www.sciencedirect.com"
    const val DEFAULT_METHOD = "combined"
    const val DEFAULT_ENABLED = true

    /** Master on/off switch — when false the engine never starts (auto or manual). */
    fun enabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_NARCIS_SPOOF_ENABLED, DEFAULT_ENABLED)

    /** Obfuscation methods supported by the native engine. */
    val METHODS = listOf(
        "combined",
        "fragment",
        "random_split",
        "seg2delay",
        "sni_triplicate",
        "oob",
        "fake_sni",
        "auto_ttl",
    )

    fun connectIp(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_NARCIS_SPOOF_CONNECT_IP, DEFAULT_CONNECT_IP)
            ?.trim().orEmpty().ifBlank { DEFAULT_CONNECT_IP }

    fun connectPort(): Int =
        MmkvManager.decodeSettingsString(AppConfig.PREF_NARCIS_SPOOF_CONNECT_PORT, DEFAULT_CONNECT_PORT)
            ?.trim()?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: DEFAULT_CONNECT_PORT.toInt()

    fun fakeSni(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_NARCIS_SPOOF_FAKE_SNI, DEFAULT_FAKE_SNI)
            ?.trim().orEmpty().ifBlank { DEFAULT_FAKE_SNI }

    fun method(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_NARCIS_SPOOF_METHOD, DEFAULT_METHOD)
            ?.trim().orEmpty().ifBlank { DEFAULT_METHOD }

    /** True when [server]/[serverPort] point at this engine's local listener. */
    fun isSpoofTarget(server: String?, serverPort: String?): Boolean {
        val s = server?.trim() ?: return false
        if (s != LISTEN_HOST && s.lowercase() != "localhost") return false
        val port = serverPort?.trim()?.toIntOrNull() ?: return false
        return port == LISTEN_PORT
    }
}
