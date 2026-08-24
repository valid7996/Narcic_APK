package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.SubscriptionItem

/**
 * Ensures the app always has subscriptions pointing to the Narcic NG
 * GitHub config repository (see [AppConfig.DEFAULT_SUBSCRIPTIONS]). Only
 * creates/updates the subscription entries — it never fetches anything
 * itself, and it never turns on their auto-update flag. Each of the three
 * default repos stays out of the 12h auto-update queue until the customer
 * explicitly picks that specific repo from "مخزن موجود" (see
 * MainRepository.createSubscriptionFromText, which flips autoUpdate=true
 * for that one entry and fetches it).
 */
object DefaultConfigSource {

    /**
     * @return true if at least one subscription was just created. Callers
     * are not required to act on this — kept for callers that want to know
     * whether the default list changed.
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
                // Keep the label/URL fresh, but never touch autoUpdate here —
                // that flag belongs to the customer's own choice, made via the
                // "مخزن موجود" picker, and must survive across app launches.
                existing.subscription.remarks = label
                existing.subscription.url = fetchUrl
                existing.subscription.enabled = true
                existing.subscription.updateInterval = 720 // 12 hours, applied once autoUpdate is turned on
                MmkvManager.encodeSubscription(existing.guid, existing.subscription)
                guid = existing.guid
            } else {
                val subItem = SubscriptionItem().apply {
                    remarks = label
                    url = fetchUrl
                    enabled = true
                    autoUpdate = false // stays off until the customer picks this repo
                    updateInterval = 720 // 12 hours, applied once autoUpdate is turned on
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
