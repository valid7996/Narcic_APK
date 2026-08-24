package com.narcic.ng.handler

import com.narcic.ng.dto.entities.DailyTrafficStat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Persists per-day VPN usage (download/upload bytes and connected time) so
 * the in-app Statistics screen can show daily and weekly totals.
 *
 * This object does NOT poll the VPN core itself. The core's traffic
 * counters are reset every time they are read (see
 * CoreServiceManager.queryAllOutboundTrafficStats), so there must be a
 * single reader of that API per process or two independent pollers would
 * silently steal each other's bytes. NotificationManager already polls the
 * core on a screen-aware schedule to drive the live speed notification;
 * it calls [recordUsage] with each interval's delta, and that is the only
 * caller. Do not add a second, independent polling loop against the core.
 */
object TrafficStatsManager {

    private fun dateKeyFormat() = SimpleDateFormat("yyyyMMdd", Locale.US)

    /**
     * Adds one polling interval's worth of usage to today's running totals.
     * Cheap to call often (small MMKV read + write); no-ops for an empty
     * interval so idle polls don't churn storage.
     */
    fun recordUsage(downloadBytes: Long, uploadBytes: Long, connectedMillis: Long) {
        if (downloadBytes <= 0L && uploadBytes <= 0L && connectedMillis <= 0L) return
        val dateKey = todayKey()
        val current = MmkvManager.decodeDailyTrafficStat(dateKey)
        MmkvManager.encodeDailyTrafficStat(
            current.copy(
                dateKey = dateKey,
                downloadBytes = current.downloadBytes + downloadBytes.coerceAtLeast(0L),
                uploadBytes = current.uploadBytes + uploadBytes.coerceAtLeast(0L),
                connectedMillis = current.connectedMillis + connectedMillis.coerceAtLeast(0L),
            )
        )
    }

    /** "yyyyMMdd" key for the current device-local date. */
    fun todayKey(): String = dateKeyFormat().format(Date())

    /** "yyyyMMdd" key for [daysAgo] days before today (0 = today). */
    fun keyForDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dateKeyFormat().format(cal.time)
    }

    /**
     * The last [days] calendar days, oldest first, including days with no
     * recorded usage (as zeroed entries) so the list is always [days] long.
     */
    fun getRecentDays(days: Int): List<DailyTrafficStat> {
        return (days - 1 downTo 0).map { daysAgo ->
            MmkvManager.decodeDailyTrafficStat(keyForDaysAgo(daysAgo))
        }
    }

    /**
     * Short Persian label for a day: "امروز" / "دیروز" for the last two days,
     * otherwise the weekday name (e.g. "دوشنبه").
     */
    fun dayLabel(daysAgo: Int): String = when (daysAgo) {
        0 -> "امروز"
        1 -> "دیروز"
        else -> {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            SimpleDateFormat("EEEE", Locale("fa")).format(cal.time)
        }
    }

    /** Sums a list of daily stats into one combined total. */
    fun sum(stats: List<DailyTrafficStat>): DailyTrafficStat {
        var download = 0L
        var upload = 0L
        var connectedMillis = 0L
        stats.forEach {
            download += it.downloadBytes
            upload += it.uploadBytes
            connectedMillis += it.connectedMillis
        }
        return DailyTrafficStat(
            dateKey = "",
            downloadBytes = download,
            uploadBytes = upload,
            connectedMillis = connectedMillis,
        )
    }
}
