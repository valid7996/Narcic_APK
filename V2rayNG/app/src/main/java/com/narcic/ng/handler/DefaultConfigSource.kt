package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.SubscriptionItem

/**
 * Ensures the app always has a subscription pointing to the Narcic NG
 * GitHub config repository. Only creates/updates the subscription entry —
 * the actual fetch is left to the app's own standard, already-tested
 * subscription update pipeline (MainAction.UpdateSubscriptions), so there
 * is only ever ONE code path that fetches and refreshes the server list.
 */
object DefaultConfigSource {

    /**
     * @return true if a fetch should be triggered afterwards (subscription
     * was just created or was disabled and got re-enabled).
     */
    fun ensureSubscriptionExists(): Boolean {
        val baseUrl = AppConfig.DEFAULT_SUBSCRIPTION_URL
        // Cache-buster: raw.githubusercontent.com CDN caches content for a
        // few minutes, so append a changing query param to always get fresh data.
        val fetchUrl = "$baseUrl?_=${System.currentTimeMillis()}"

        val subscriptions = MmkvManager.decodeSubscriptions()
        val existing = subscriptions.find { it.subscription.url.substringBefore("?") == baseUrl }

        val guid: String
        val needsFetch: Boolean

        if (existing != null) {
            needsFetch = !existing.subscription.enabled
            existing.subscription.url = fetchUrl
            existing.subscription.enabled = true
            existing.subscription.autoUpdate = true
            existing.subscription.updateInterval = 720 // 12 hours
            MmkvManager.encodeSubscription(existing.guid, existing.subscription)
            guid = existing.guid
        } else {
            val subItem = SubscriptionItem().apply {
                remarks = "Narcic NG"
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

        // Always keep the Narcic NG subscription as the active tab, so the
        // manual fetch button and the test button operate on the right
        // group by default (instead of the empty "Default" tab).
        if (guid.isNotEmpty()) {
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, guid)
        }

        return needsFetch
    }
}
