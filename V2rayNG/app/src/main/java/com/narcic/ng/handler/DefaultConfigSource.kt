package com.narcic.ng.handler

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.SubscriptionItem

/**
 * Ensures the app always has exactly the default Narcic subscriptions
 * listed in [AppConfig.DEFAULT_SUBSCRIPTION_URLS] — creating any that are
 * missing and removing any old default subscription that is no longer in
 * that list. The actual fetch is left to the app's own standard,
 * already-tested subscription update pipeline (MainAction.UpdateSubscriptions),
 * so there is only ever ONE code path that fetches and refreshes the
 * server list.
 */
object DefaultConfigSource {

    /**
     * @return true if a fetch should be triggered afterwards (at least one
     * default subscription was just created or was disabled and got
     * re-enabled).
     */
    fun ensureSubscriptionExists(): Boolean {
        val defaultUrls = AppConfig.DEFAULT_SUBSCRIPTION_URLS
        val subscriptions = MmkvManager.decodeSubscriptions()

        // Drop any previously-seeded default subscription that is no longer
        // part of the current default list (e.g. the old single Narcic NG
        // GitHub-config subscription, or a since-removed link).
        subscriptions
            .filter { it.subscription.url !in defaultUrls }
            .forEach { MmkvManager.removeSubscription(it.guid) }

        val remaining = MmkvManager.decodeSubscriptions()
        var needsFetch = false

        defaultUrls.forEachIndexed { index, url ->
            val existing = remaining.find { it.subscription.url == url }
            if (existing != null) {
                if (!existing.subscription.enabled) {
                    needsFetch = true
                }
                existing.subscription.enabled = true
                existing.subscription.autoUpdate = true
                existing.subscription.updateInterval = 720 // 12 hours
                MmkvManager.encodeSubscription(existing.guid, existing.subscription)
            } else {
                val subItem = SubscriptionItem().apply {
                    remarks = "Narcic ${index + 1}"
                    this.url = url
                    enabled = true
                    autoUpdate = true
                    updateInterval = 720 // 12 hours
                }
                MmkvManager.encodeSubscription("", subItem)
                needsFetch = true
            }
        }

        // If the previously active tab pointed at a subscription that was
        // just removed above, fall back to the unfiltered default tab
        // instead of pointing at a now-nonexistent group.
        val activeId = MmkvManager.decodeSettingsString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()
        if (activeId.isNotEmpty() && MmkvManager.decodeSubscriptions().none { it.guid == activeId }) {
            MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, "")
        }

        return needsFetch
    }
}
