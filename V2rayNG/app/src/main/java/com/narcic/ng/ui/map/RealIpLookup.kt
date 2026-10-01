package com.narcic.ng.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Looks up the device's REAL public IP (home country) — deliberately bypassing
 * the VPN tunnel by binding the request to an underlying physical network, so
 * the map can draw both "you are here" and "exit country" at the same time.
 */
object RealIpLookup {

    data class Result(
        val countryCode: String?,
        val city: String?,
        val isp: String?,
        val ip: String?,
    )

    suspend fun fetch(context: Context): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
                ?: return@runCatching null
            val physical = cm.allNetworks.firstOrNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } ?: return@runCatching null

            // Network.openConnection (API 26+) binds the request to the
            // physical network, bypassing the VPN tunnel.
            val conn = if (android.os.Build.VERSION.SDK_INT >= 26) {
                physical.openConnection(URL("https://api.ip.sb/geoip")) as HttpsURLConnection
            } else return@runCatching null
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "NarcicNG")
            try {
                val body = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(body)
                Result(
                    countryCode = obj.optString("country_code").takeIf { it.length == 2 }?.uppercase(),
                    city = obj.optString("city").takeIf { it.isNotBlank() },
                    isp = listOf(obj.optString("isp"), obj.optString("org")).firstOrNull { it.isNotBlank() },
                    ip = listOf(obj.optString("ip"), obj.optString("clientIp")).firstOrNull { it.isNotBlank() },
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
