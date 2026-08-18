package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.SubscriptionItem

/**
 * Ensures the app always has subscriptions pointing to the Narcic NG
 * GitHub config repository. Only creates/updates the subscription entries —
 * the actual fetch is left to the app's own standard, already-tested
 * subscription update pipeline (MainAction.UpdateSubscriptions), so there
 * is only ever ONE code path that fetches and refreshes the server list.
 */
object DefaultConfigSource {

    private data class DefaultSub(val remarks: String, val baseUrl: String)

    /** All subscriptions that should always exist, in display order. */
    private val DEFAULT_SUBS = listOf(
        DefaultSub("Narcic NG", AppConfig.DEFAULT_SUBSCRIPTION_URL),
        DefaultSub("Narcic Auto", AppConfig.DEFAULT_SUBSCRIPTION_URL_AUTO),
        DefaultSub("Narcic WireGuard", AppConfig.DEFAULT_SUBSCRIPTION_URL_WIREGUARD),
        DefaultSub("Narcic JSON", AppConfig.DEFAULT_SUBSCRIPTION_URL_JSONHARD),
    )

    /**
     * @return true if a fetch should be triggered afterwards (at least one
     * default subscription was just created, or was disabled and got re-enabled).
     */
    fun ensureSubscriptionExists(): Boolean {
        val subscriptions = MmkvManager.decodeSubscriptions()
        var needsFetch = false
        var activeGuid = ""

        for (def in DEFAULT_SUBS) {
            // Cache-buster: raw.githubusercontent.com CDN caches content for a
            // few minutes, so append a changing query param to always get fresh data.
            val fetchUrl = "${def.baseUrl}?_=${System.currentTimeMillis()}"

            val existing = subscriptions.find { it.subscription.url.substringBefore("?") == def.baseUrl }

            val guid: String
            if (existing != null) {
                if (!existing.subscription.enabled) needsFetch = true
                existing.subscription.url = fetchUrl
                existing.subscription.enabled = true
                existing.subscription.autoUpdate = true
                existing.subscription.updateInterval = 720 // 12 hours
                MmkvManager.encodeSubscription(existing.guid, existing.subscription)
                guid = existing.guid
            } else {
                val subItem = SubscriptionItem().apply {
                    remarks = def.remarks
                    url = fetchUrl
                    enabled = true
                    autoUpdate = true
                    updateInterval = 720 // 12 hours
                }
                MmkvManager.encodeSubscription("", subItem)
                guid = MmkvManager.decodeSubscriptions()
                    .find { it.subscription.url.substringBefore("?") == def.baseUrl }?.guid.orEmpty()
                needsFetch = true
            }

            // Keep track of the original "Narcic NG" subscription's guid so it
            // stays the active tab (see below).
            if (def.baseUrl == AppConfig.DEFAULT_SUBSCRIPTION_URL) {
                activeGuid = guid
            }
        }

        // Always keep the original Narcic NG subscription as the active tab, so
        // the manual fetch button and the test button operate on the right
        // group by default (instead of the empty "Default" tab).
        if (activeGuid.isNotEmpty()) {
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, activeGuid)
        }

        return needsFetch
    }
}
