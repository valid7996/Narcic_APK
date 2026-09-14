package com.narcic.ng.ui.userasset

import android.app.Application
import com.narcic.ng.AppConfig
import com.narcic.ng.dto.UrlContentRequest
import com.narcic.ng.dto.entities.AssetUrlCache
import com.narcic.ng.dto.entities.AssetUrlItem
import com.narcic.ng.extension.concatUrl
import com.narcic.ng.handler.MmkvManager
import com.narcic.ng.ui.base.BaseViewModel
import com.narcic.ng.util.HttpUtil
import com.narcic.ng.util.LogUtil
import com.narcic.ng.util.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class UserAssetViewModel(application: Application) : BaseViewModel(application) {
    private val assets = mutableListOf<AssetUrlCache>()
    private val builtInGeoFiles = listOf(AppConfig.GEOSITE_DAT, AppConfig.GEOIP_DAT, AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT)

    private val _assetsFlow = MutableStateFlow<List<AssetUrlCache>>(emptyList())
    val assetsFlow: StateFlow<List<AssetUrlCache>> = _assetsFlow.asStateFlow()

    val itemCount: Int
        get() = assets.size

    fun getAssets(): List<AssetUrlCache> = assets.toList()

    fun getAsset(position: Int): AssetUrlCache? = assets.getOrNull(position)

    fun reload(geoFilesSource: String) {
        val decoded = MmkvManager.decodeAssetUrls()
        assets.clear()
        assets.addAll(buildAssetList(decoded, geoFilesSource))
        _assetsFlow.value = assets.toList()
    }

    private fun buildAssetList(
        decodedAssets: List<AssetUrlCache>?,
        geoFilesSource: String
    ): List<AssetUrlCache> {
        val savedAssets = decodedAssets ?: emptyList()
        val builtInItems = builtInGeoFiles
            .filter { geoFile -> savedAssets.none { it.assetUrl.remarks == geoFile } }
            .map {
                AssetUrlCache(
                    Utils.getUuid(),
                    AssetUrlItem(
                        it,
                        String.format(AppConfig.GITHUB_DOWNLOAD_URL, geoFilesSource).concatUrl(it),
                        locked = true
                    )
                )
            }
        // Force update URL for geoip-only-cn-private.dat
        return (builtInItems + savedAssets).map { cache ->
            if (cache.assetUrl.remarks == AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT) {
                cache.copy(
                    assetUrl = cache.assetUrl.copy(
                        url = AppConfig.GEOIP_ONLY_CN_PRIVATE_URL
                    )
                )
            } else {
                cache
            }
        }
    }

    fun downloadGeoFiles(
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): GeoDownloadResult {
        val snapshot = getAssets()
        var successCount = 0
        val failures = mutableListOf<String>()

        snapshot.forEach { cache ->
            val item = cache.assetUrl
            val portsToTry = if (httpPort == 0) listOf(0) else listOf(httpPort, 0)
            if (portsToTry.any { tryDownload(item, extDir, it, proxyUsername, proxyPassword) }) {
                successCount++
            } else {
                failures.add(item.remarks)
            }
        }

        return GeoDownloadResult(successCount, failures.size, failures)
    }

    private fun tryDownload(
        item: AssetUrlItem,
        extDir: File,
        httpPort: Int,
        proxyUsername: String? = null,
        proxyPassword: String? = null
    ): Boolean {
        val targetTemp = File(extDir, item.remarks + "_temp")
        val target = File(extDir, item.remarks)
        try {
            // Drop a stale temp file from an interrupted previous run first so
            // we never validate/rename half-written leftovers.
            targetTemp.delete()
            if (
                HttpUtil.downloadToFile(
                    UrlContentRequest(
                        url = item.url,
                        timeout = 15000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    ),
                    targetTemp
                )
            ) {
                if (!targetTemp.isPlausibleGeoDat()) {
                    LogUtil.e(
                        AppConfig.TAG,
                        "Downloaded geo asset looks invalid, keeping the old file: ${item.remarks}"
                    )
                    targetTemp.delete()
                    return false
                }
                // Atomic replace with a CHECKED rename: a corrupt or partial
                // download must never take the place of the healthy runtime
                // file, and success is only reported when the swap happened.
                if (!targetTemp.renameTo(target)) {
                    LogUtil.e(AppConfig.TAG, "Rename failed for ${item.remarks}; old file kept")
                    targetTemp.delete()
                    return false
                }
                return true
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to download geo file: ${item.remarks}", e)
        }
        targetTemp.delete()
        return false
    }

    /**
     * Cheap sanity gate for *.dat geo assets: v2ray-geoformat files start with
     * the "// Geo" comment header (the Xray loader rejects files without it)
     * and are orders of magnitude larger than any HTTP error page.
     */
    private fun File.isPlausibleGeoDat(): Boolean {
        if (!isFile || length() < 100_000L) return false
        return runCatching {
            inputStream().use { input ->
                val head = ByteArray(8)
                val read = input.read(head)
                read >= 6 && String(head, 0, read, Charsets.US_ASCII).startsWith("// Geo")
            }
        }.getOrDefault(false)
    }

    data class GeoDownloadResult(
        val successCount: Int,
        val failureCount: Int,
        val failedAssets: List<String>
    )
}