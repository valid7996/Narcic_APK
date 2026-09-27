package com.narcic.ng.ui.main

import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Test

class MainImportMenuTest {

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        // Simple profiles (vmess/vless/ss/trojan/socks/wireguard/hysteria2) can
        // be shared via all four methods: QR code, raw link to clipboard, full
        // JSON config to clipboard, and the app-specific narcic:// deep link.
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.ShareLink,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = false, includeManagementActions = false))
    }

    @Test
    fun regularMoreMenuContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = true, includeManagementActions = true))
    }

    @Test
    fun shareMenuNoLongerExcludesActionsForDefaultSubscription() {
        // Default-subscription configs are now editable/shareable like any
        // other config -- isFromDefaultSubscription no longer restricts anything.
        assertEquals(
            listOf(
                ServerMenuAction.ShareQRCode,
                ServerMenuAction.ShareClipboard,
                ServerMenuAction.ShareFullContent,
                ServerMenuAction.ShareLink,
            ),
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isFromDefaultSubscription = true)
        )
    }

    @Test
    fun shareMenuIncludesActionsForManualConfig() {
        // ProfileItem.configType is a constructor val (Aether patch), so the
        // old ProfileItem().apply { configType = ... } no longer compiles;
        // ProfileItem.create() is the companion factory for the same intent.
        val profile = ProfileItem.create(EConfigType.VMESS).apply {
            subscriptionId = ""
        }
        assertEquals(
            listOf(
                ServerMenuAction.ShareQRCode,
                ServerMenuAction.ShareClipboard,
                ServerMenuAction.ShareFullContent,
                ServerMenuAction.ShareLink,
                ServerMenuAction.Edit,
                ServerMenuAction.Delete,
            ),
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isFromDefaultSubscription = false)
        )
    }

    @Test
    fun shareMenuNoLongerExcludesEditAndShareForDefaultSubEvenWithManagement() {
        val profile = ProfileItem.create(EConfigType.VMESS).apply {
            subscriptionId = "__default_subscription__"
        }
        assertEquals(
            listOf(
                ServerMenuAction.ShareQRCode,
                ServerMenuAction.ShareClipboard,
                ServerMenuAction.ShareFullContent,
                ServerMenuAction.ShareLink,
                ServerMenuAction.Edit,
                ServerMenuAction.Delete,
            ),
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isFromDefaultSubscription = true)
        )
    }

    @Test
    fun shareMenuStillFiltersComplexProfiles() {
        val profile = ProfileItem.create(EConfigType.POLICYGROUP).apply {
            subscriptionId = ""
        }
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false, isFromDefaultSubscription = false)
        )
    }
}
