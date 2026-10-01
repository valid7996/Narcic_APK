package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.IPAPIInfo
import com.narcic.ng.dto.UrlContentRequest
import com.narcic.ng.util.HttpUtil
import com.narcic.ng.util.JsonUtil
import com.narcic.ng.util.LogUtil
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.Locale

object SpeedtestManager {

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (closeEx: IOException) {
                }
            }
        }
        return -1
    }

    /**
     * Fetches and parses the raw IP-API response. Shared by [getRemoteIPInfo]
     * (used by the "Test Speed" status text) and [getRemoteIPInfoDetailed]
     * (used by the main screen's live IP/country display).
     *
     * When AmneziaWG is the active engine, there is no local HTTP/SOCKS proxy to point at —
     * AmneziaWG is a raw system-level VpnService tunnel (like the OS's own VPN clients), so the
     * app's own network traffic already flows through the tunnel once it's up. Routing through
     * com.narcic.ng's local Xray proxy port in that case would either hit a closed port (Xray
     * isn't running) or the wrong tunnel entirely, which is why the exit IP/country never
     * resolved for AmneziaWG connections. httpPort=0 tells HttpUtil to connect directly.
     */
    private fun fetchRemoteIpApiInfo(): IPAPIInfo? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val useAwg = com.narcic.ng.awg.AwgManager.isRunning()
        val proxyUsername = if (useAwg) null else SettingsManager.getSocksUsername()
        val proxyPassword = if (useAwg) null else SettingsManager.getSocksPassword()
        val httpPort = if (useAwg) 0 else SettingsManager.getHttpPort()
        if (!useAwg && httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        return JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java)
    }

    fun getRemoteIPInfo(): String? {
        val ipInfo = fetchRemoteIpApiInfo() ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return "(${country ?: "unknown"}) ${ip ?: "unknown"}"
    }

    /** Exit IP, ISO-3166-1 alpha-2 country code, city and ISP, for the main dashboard. */
    data class RemoteIpDetails(
        val ip: String,
        val countryCode: String?,
        val city: String?,
        val isp: String?,
    )

    fun getRemoteIPInfoDetailed(): RemoteIpDetails? {
        val ipInfo = fetchRemoteIpApiInfo() ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() } ?: return null

        val countryCode = listOf(
            ipInfo.country_code,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }
            ?: ipInfo.country?.takeIf { it.length == 2 }

        val city = listOf(ipInfo.city)
            .firstOrNull { !it.isNullOrBlank() }

        val isp = listOf(ipInfo.isp, ipInfo.org)
            .firstOrNull { !it.isNullOrBlank() }

        return RemoteIpDetails(
            ip = ip,
            countryCode = countryCode?.uppercase(Locale.US),
            city = city,
            isp = isp,
        )
    }
}
