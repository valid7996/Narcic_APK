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

    // ───────── per-engine manual-default groups (3-page redesign) ─────────
    // The old single default bucket (subscriptionId == "") mixed every
    // engine, so a page could show another engine's configs. Each engine now
    // owns its own default group; imports and manual saves are routed to it
    // by config type, and the legacy bucket is migrated once, in place.

    const val DEFAULT_GROUP_AWG_NAME = "پیش‌فرض امنزیا"
    const val DEFAULT_GROUP_V2_NAME = "پیش‌فرض وی‌تو‌ری"
    const val DEFAULT_GROUP_AETHER_NAME = "پیش‌فرض اتر"

    private const val KEY_DEFAULT_GROUP_AWG = "cache_default_group_awg"
    private const val KEY_DEFAULT_GROUP_V2 = "cache_default_group_v2"
    private const val KEY_DEFAULT_GROUP_AETHER = "cache_default_group_ae"
    private const val KEY_MIGRATED_ENGINE_DEFAULTS = "cache_migrated_engine_defaults_v1"

    /**
     * Returns (creating on first use) the guid of the default group that
     * owns configs of [type]'s engine. Subscription updates that pass a real
     * subscription id never reach this — only blank-subid imports do.
     */
    fun perEngineDefaultGroupIdFor(type: com.narcic.ng.enums.EConfigType): String {
        val (key, label) = when (type) {
            com.narcic.ng.enums.EConfigType.WIREGUARD,
            com.narcic.ng.enums.EConfigType.AMNEZIAWG -> KEY_DEFAULT_GROUP_AWG to DEFAULT_GROUP_AWG_NAME
            com.narcic.ng.enums.EConfigType.AETHER -> KEY_DEFAULT_GROUP_AETHER to DEFAULT_GROUP_AETHER_NAME
            else -> KEY_DEFAULT_GROUP_V2 to DEFAULT_GROUP_V2_NAME
        }
        val existing = MmkvManager.decodeSettingsString(key)
        if (!existing.isNullOrEmpty() && MmkvManager.decodeSubscriptions().any { it.guid == existing }) {
            return existing
        }
        MmkvManager.encodeSubscription("", SubscriptionItem().apply {
            remarks = label
            url = ""
            enabled = true
            autoUpdate = false
        })
        val guid = MmkvManager.decodeSubscriptions()
            .lastOrNull { it.subscription.remarks == label }?.guid.orEmpty()
        if (guid.isNotEmpty()) MmkvManager.encodeSettings(key, guid)
        return guid
    }

    /**
     * One-shot migration: configs sitting in the legacy shared default
     * bucket (subscriptionId == "") are re-tagged into the default group of
     * their own engine. Nothing is deleted — profiles move in place, so
     * each page shows only its own configs after the first launch.
     */
    fun migrateLegacyDefaultGroup() {
        if (MmkvManager.decodeSettingsBool(KEY_MIGRATED_ENGINE_DEFAULTS, false)) return
        MmkvManager.decodeServerList("").toList().forEach { guid ->
            MmkvManager.decodeServerConfig(guid)?.let { config ->
                config.subscriptionId = perEngineDefaultGroupIdFor(config.configType)
                MmkvManager.encodeServerConfig(guid, config)
            }
        }
        MmkvManager.encodeSettings(KEY_MIGRATED_ENGINE_DEFAULTS, true)
    }

    /**
     * The اتر page's quick-setup dashboard IS the config: opening that page
     * provisions one Aether profile automatically (in the پیش‌فرض اتر
     * group) so the user never has to "add a file" first — they just pick a
     * path, choose auto/custom connection and hit connect. Idempotent:
     * returns the existing implicit profile instead of creating duplicates.
     *
     * @return the profile's guid, or null if creation failed.
     */
    fun ensureImplicitAetherProfile(): String? {
        val groupId = perEngineDefaultGroupIdFor(com.narcic.ng.enums.EConfigType.AETHER)
        // The implicit profile lives in the پیش‌فرض اتر group.
        MmkvManager.decodeServerList(groupId).firstOrNull()?.let { return it }
        // Never create a duplicate: reuse any Aether profile anywhere.
        val groupIds = MmkvManager.decodeSubscriptions().map { it.guid } + ""
        for (subid in groupIds.distinct()) {
            for (guid in MmkvManager.decodeServerList(subid)) {
                MmkvManager.decodeServerConfig(guid)?.takeIf {
                    it.configType == com.narcic.ng.enums.EConfigType.AETHER
                }?.let { return guid }
            }
        }
        val profile = com.narcic.ng.dto.entities.ProfileItem.create(
            com.narcic.ng.enums.EConfigType.AETHER
        ).apply {
            remarks = "Aether"
            subscriptionId = groupId
        }
        return MmkvManager.encodeServerConfig("", profile).takeIf { it.isNotEmpty() }
    }
}
