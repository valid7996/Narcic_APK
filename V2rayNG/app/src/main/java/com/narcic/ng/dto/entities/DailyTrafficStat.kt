package com.narcic.ng.dto.entities

/**
 * Aggregated VPN usage for a single calendar day, used by the in-app
 * Statistics screen (daily/weekly traffic + connected time).
 *
 * [dateKey] is a "yyyyMMdd" string (device local time) and doubles as the
 * MMKV storage key, see MmkvManager.encodeDailyTrafficStat/decodeDailyTrafficStat.
 * Values are accumulated incrementally by TrafficStatsManager as the VPN
 * core is polled, they are never derived by summing historical deltas.
 */
data class DailyTrafficStat(
    val dateKey: String = "",
    val downloadBytes: Long = 0L,
    val uploadBytes: Long = 0L,
    val connectedMillis: Long = 0L,
) {
    val totalBytes: Long get() = downloadBytes + uploadBytes
}
