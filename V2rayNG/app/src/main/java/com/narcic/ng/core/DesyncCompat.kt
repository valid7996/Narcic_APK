package com.narcic.ng.core

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.V2rayConfig
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType

/**
 * Pure helpers for Narcic's embedded, rootless DPI-desync engine (ciadpi/byedpi, MIT licensed).
 *
 * The engine runs as a local SOCKS proxy on 127.0.0.1. [attachOutbound] wires it in front of
 * the main proxy outbound via Xray's `sockopt.dialerProxy`, the same mechanism already used
 * for proxy-chain hops in [com.narcic.ng.core.CoreConfigManager].
 */
object DesyncCompat {
    const val OUTBOUND_TAG = "narcic-desync"

    const val PROFILE_OFF = "Off"
    const val PROFILE_LIGHT = "Light"
    const val PROFILE_BALANCED = "Balanced"
    const val PROFILE_SEVERE = "Severe"
    const val PROFILE_ADAPTIVE = "Adaptive"
    const val PROFILE_CUSTOM = "Custom"

    const val METHOD_SPLIT = "Split"
    const val METHOD_DISORDER = "Disorder"
    const val METHOD_FAKE_SNI = "Fake SNI"
    const val METHOD_OUT_OF_BAND = "Out of Band"
    const val METHOD_DISORDER_OUT_OF_BAND = "Disorder + Out of Band"

    /** Config types the local desync proxy can sit in front of. */
    fun supports(profileItem: ProfileItem): Boolean = when (profileItem.configType) {
        EConfigType.VMESS,
        EConfigType.VLESS,
        EConfigType.SHADOWSOCKS,
        EConfigType.SOCKS,
        EConfigType.HTTP,
        EConfigType.TROJAN -> true

        else -> false
    }

    fun isEnabled(profileItem: ProfileItem): Boolean =
        supports(profileItem) && normalizeProfile(profileItem.desyncProfile) != PROFILE_OFF

    /**
     * Builds the ciadpi argv for [profileItem] listening on 127.0.0.1:[port].
     * Returns null when desync is off, unsupported for this config type, or (for
     * [PROFILE_CUSTOM]) no arguments have been configured.
     */
    fun buildCommandLine(profileItem: ProfileItem, port: Int): List<String>? {
        if (!isEnabled(profileItem) || port !in 1..65535) return null
        val strategy = when (normalizeProfile(profileItem.desyncProfile)) {
            PROFILE_LIGHT -> listOf("--proto=tls", "--split", "1+s", "--tlsrec", "1+s")
            PROFILE_BALANCED -> listOf("--proto=tls", "--disorder", "1", "--tlsrec", "1+s")
            PROFILE_SEVERE -> listOf(
                "--proto=tls", "--split", "1+s", "--disorder", "3+s", "--fake", "-1", "--ttl", "8"
            )

            PROFILE_ADAPTIVE -> listOf(
                "--proto=tls", "--disorder", "1", "--auto=torst,ssl_err", "--timeout", "3", "--tlsrec", "3+s"
            )

            PROFILE_CUSTOM -> shellSplit(profileItem.desyncArgs.orEmpty())
                .takeIf { it.isNotEmpty() } ?: return null

            else -> return null
        }
        return buildList {
            add("ciadpi")
            addAll(strategy)
            addAll(listOf("--ip", "127.0.0.1", "--port", port.toString()))
        }
    }

    data class CustomOptions(
        val method: String = METHOD_SPLIT,
        val position: String = "1",
        val fakeTtl: String = "8",
        val tlsRecordPosition: String = "1",
        val timeoutSeconds: String = "3",
        val fakeSni: String = "",
    )

    fun normalizeFakeSniList(input: String): String {
        val hasTrailingSeparator = input.lastOrNull()?.let { char ->
            char.isWhitespace() || char == ',' || char == ';' || char == '|'
        } == true
        val values = input
            .split(Regex("[,;|\\s]+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (values.isEmpty()) return ""
        return values.joinToString(", ") + if (hasTrailingSeparator) ", " else ""
    }

    fun parseCustomOptions(arguments: String): CustomOptions {
        val tokens = shellSplit(arguments)
        fun valueAfter(flag: String): String? = tokens.indexOf(flag)
            .takeIf { it >= 0 && it + 1 < tokens.size }
            ?.let { tokens[it + 1] }

        fun valuesAfter(flag: String): List<String> {
            val results = mutableListOf<String>()
            for (i in 0 until tokens.size - 1) {
                if (tokens[i] == flag) results.add(tokens[i + 1])
            }
            return results
        }

        val (method, flag) = when {
            "--disorder" in tokens -> METHOD_DISORDER to "--disorder"
            "--fake" in tokens -> METHOD_FAKE_SNI to "--fake"
            "--oob" in tokens -> METHOD_OUT_OF_BAND to "--oob"
            "--disoob" in tokens -> METHOD_DISORDER_OUT_OF_BAND to "--disoob"
            else -> METHOD_SPLIT to "--split"
        }
        return CustomOptions(
            method = method,
            position = valueAfter(flag)?.removeSuffix("+s") ?: if (method == METHOD_FAKE_SNI) "-1" else "1",
            fakeTtl = valueAfter("--ttl") ?: "8",
            tlsRecordPosition = valueAfter("--tlsrec")?.removeSuffix("+s") ?: "1",
            timeoutSeconds = valueAfter("--timeout") ?: "3",
            fakeSni = valuesAfter("--tls-sni").distinct().joinToString(", "),
        )
    }

    fun buildCustomArguments(options: CustomOptions): String {
        fun integer(value: String, fallback: Int, range: IntRange): Int =
            value.trim().toIntOrNull()?.coerceIn(range) ?: fallback

        val method = normalizeMethod(options.method)
        val isFake = method == METHOD_FAKE_SNI
        val position = if (isFake) {
            integer(options.position, -1, -65535..65535).takeIf { it != 0 } ?: -1
        } else {
            integer(options.position, 1, 1..65535)
        }
        val flag = when (method) {
            METHOD_DISORDER -> "--disorder"
            METHOD_FAKE_SNI -> "--fake"
            METHOD_OUT_OF_BAND -> "--oob"
            METHOD_DISORDER_OUT_OF_BAND -> "--disoob"
            else -> "--split"
        }
        val result = mutableListOf("--proto=tls", flag, if (isFake) "$position" else "$position+s")
        if (isFake) {
            result += listOf("--ttl", integer(options.fakeTtl, 8, 1..255).toString())
            normalizeFakeSniList(options.fakeSni)
                .removeSuffix(", ")
                .split(", ")
                .filter(String::isNotEmpty)
                .forEach { sni -> result += listOf("--tls-sni", sni) }
        }
        result += listOf("--tlsrec", "${integer(options.tlsRecordPosition, 1, 1..65535)}+s")
        result += listOf("--timeout", integer(options.timeoutSeconds, 3, 1..3600).toString())
        return result.joinToString(" ")
    }

    private fun normalizeMethod(value: String): String = when (value) {
        "Fake", METHOD_FAKE_SNI -> METHOD_FAKE_SNI
        "OOB", METHOD_OUT_OF_BAND -> METHOD_OUT_OF_BAND
        "Disorder + OOB", METHOD_DISORDER_OUT_OF_BAND -> METHOD_DISORDER_OUT_OF_BAND
        METHOD_DISORDER -> METHOD_DISORDER
        else -> METHOD_SPLIT
    }

    /**
     * Points the config's main proxy outbound (tag == [AppConfig.TAG_PROXY]) at the local
     * desync proxy listening on [port], adding the SOCKS outbound that represents it.
     * No-op if desync is off/unsupported, or if that outbound already has a dialerProxy
     * (e.g. it is already the entry hop of a manual proxy chain).
     */
    fun attachOutbound(config: V2rayConfig, profileItem: ProfileItem, port: Int): Boolean {
        if (!isEnabled(profileItem) || port !in 1..65535) return false
        val primary = config.outbounds.firstOrNull { it.tag == AppConfig.TAG_PROXY } ?: return false
        val stream = primary.streamSettings ?: V2rayConfig.OutboundBean.StreamSettingsBean().also {
            primary.streamSettings = it
        }
        val sockopt = stream.sockopt ?: V2rayConfig.OutboundBean.StreamSettingsBean.SockoptBean().also {
            stream.sockopt = it
        }
        if (!sockopt.dialerProxy.isNullOrBlank()) return false

        sockopt.dialerProxy = OUTBOUND_TAG
        config.outbounds.removeAll { it.tag == OUTBOUND_TAG }
        config.outbounds.add(
            V2rayConfig.OutboundBean(
                tag = OUTBOUND_TAG,
                protocol = "socks",
                settings = V2rayConfig.OutboundBean.OutSettingsBean(
                    address = "127.0.0.1",
                    port = port,
                ),
            )
        )
        return true
    }

    private fun normalizeProfile(value: String?): String = when (value?.trim()) {
        PROFILE_OFF, null -> PROFILE_OFF
        PROFILE_LIGHT -> PROFILE_LIGHT
        PROFILE_BALANCED -> PROFILE_BALANCED
        PROFILE_SEVERE -> PROFILE_SEVERE
        PROFILE_ADAPTIVE -> PROFILE_ADAPTIVE
        PROFILE_CUSTOM -> PROFILE_CUSTOM
        else -> PROFILE_OFF
    }

    internal fun shellSplit(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun flush() {
            if (current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
            }
        }

        input.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' && quote != '\'' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        flush()
        return result
    }
}
