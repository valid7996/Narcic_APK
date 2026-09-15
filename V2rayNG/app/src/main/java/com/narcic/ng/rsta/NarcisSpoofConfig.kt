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

    /** Default listen port, also the fallback if the dynamic port read fails.
     *  Aether's Cloak defaults to the same port, so the effective port is
     *  allocated dynamically (see [listenPort]) and persisted in MMKV. */
    const val DEFAULT_LISTEN_PORT = AppConfig.NARCIS_SPOOF_LISTEN_PORT
    private const val MMKV_LISTEN_PORT = "narcis_spoof_listen_port"

    /**
     * Effective local listen port. Resolved once per process: tries the last
     * persisted port first, then the default, and on bind-conflict risk falls
     * back to any free port. Persisted so [isSpoofTarget] and the engine see
     * the same value in every process.
     */
    val listenPort: Int by lazy {
        val persisted = MmkvManager.decodeSettingsInt(MMKV_LISTEN_PORT, 0)
        val candidates = buildList {
            if (persisted in 1024..65535) add(persisted)
            add(DEFAULT_LISTEN_PORT)
        }
        for (candidate in candidates) {
            if (isPortFree(candidate)) {
                if (candidate != persisted) {
                    MmkvManager.encodeSettings(MMKV_LISTEN_PORT, candidate)
                }
                return@lazy candidate
            }
        }
        // Neither the persisted nor the default port is free — grab any free
        // port so the engine still works (MMKV updated, isSpoofTarget follows).
        val free = runCatching {
            java.net.ServerSocket(0).use { it.localPort }
        }.getOrDefault(DEFAULT_LISTEN_PORT)
        MmkvManager.encodeSettings(MMKV_LISTEN_PORT, free)
        free
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        java.net.ServerSocket(port).use { true }
    }.getOrDefault(false)

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

    /** True when [server]/[serverPort] point at this engine's local listener.
     *  Also accepts the IPv6 loopback (::1) so those server entries get the
     *  engine started too. */
    fun isSpoofTarget(server: String?, serverPort: String?): Boolean {
        val s = server?.trim() ?: return false
        if (s != LISTEN_HOST && s.lowercase() != "localhost" && s != "::1") return false
        val port = serverPort?.trim()?.toIntOrNull() ?: return false
        return port == listenPort
    }
}
