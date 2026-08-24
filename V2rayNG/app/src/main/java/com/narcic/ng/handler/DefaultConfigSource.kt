package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.SubscriptionItem

/**
 * Ensures the app always has subscriptions pointing to the Narcic NG
 * GitHub config repository (see [AppConfig.DEFAULT_SUBSCRIPTIONS]). Only
 * creates/updates the subscription entries — it never fetches anything
 * itself. Nothing is auto-imported on first install: fetching (manual or
 * periodic) is entirely handled by [SubscriptionUpdater], whose periodic
 * pipeline only activates once the customer has added a subscription of
 * their own.
 */
object DefaultConfigSource {

    /**
     * @return true if at least one subscription was just created, or was
     * disabled and got re-enabled. Callers are not required to act on this —
     * kept for callers that want to know whether the default list changed.
     */
    fun ensureSubscriptionExists(): Boolean {
        var needsFetch = false
        var primaryGuid = ""

        AppConfig.DEFAULT_SUBSCRIPTIONS.forEach { (label, baseUrl) ->
            // Cache-buster: raw.githubusercontent.com CDN caches content for a
            // few minutes, so append a changing query param to always get fresh data.
            val fetchUrl = "$baseUrl?_=${System.currentTimeMillis()}"

            val subscriptions = MmkvManager.decodeSubscriptions()
            val existing = subscriptions.find { it.subscription.url.substringBefore("?") == baseUrl }

            val guid: String
            if (existing != null) {
                if (!existing.subscription.enabled) {
                    needsFetch = true
                }
                existing.subscription.remarks = label
                existing.subscription.url = fetchUrl
                existing.subscription.enabled = true
                existing.subscription.autoUpdate = true
                existing.subscription.updateInterval = 720 // 12 hours
                MmkvManager.encodeSubscription(existing.guid, existing.subscription)
                guid = existing.guid
            } else {
                val subItem = SubscriptionItem().apply {
                    remarks = label
                    url = fetchUrl
                    enabled = true
                    autoUpdate = true
                    updateInterval = 720 // 12 hours
                }
                MmkvManager.encodeSubscription("", subItem)
                guid = MmkvManager.decodeSubscriptions()
                    .find { it.subscription.url.substringBefore("?") == baseUrl }?.guid.orEmpty()
                needsFetch = true
            }

            if (primaryGuid.isEmpty()) {
                primaryGuid = guid
            }
        }

        // Always keep the first Narcic NG subscription as the active tab, so
        // the manual fetch button and the test button operate on the right
        // group by default (instead of the empty "Default" tab).
        if (primaryGuid.isNotEmpty()) {
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, primaryGuid)
        }

        return needsFetch
    }
}
