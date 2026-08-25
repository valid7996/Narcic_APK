package com.narcic.ng.ui.main

import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Test

class MainImportMenuTest {

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
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
    fun shareMenuExcludesActionsForDefaultSubscription() {
        // For default subscription, share/edit hidden but delete remains when management included
        // With includeManagement=false (share only), all share actions hidden -> empty
        assertEquals(
            emptyList(),
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isFromDefaultSubscription = true)
        )
    }

    @Test
    fun shareMenuIncludesActionsForManualConfig() {
        val profile = ProfileItem().apply {
            configType = EConfigType.VMESS
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
    fun shareMenuExcludesEditAndShareForDefaultSubEvenWithManagement() {
        val profile = ProfileItem().apply {
            configType = EConfigType.VMESS
            subscriptionId = "__default_subscription__"
        }
        assertEquals(
            listOf(ServerMenuAction.Delete),
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isFromDefaultSubscription = true)
        )
    }

    @Test
    fun shareMenuStillFiltersComplexProfiles() {
        val profile = ProfileItem().apply {
            configType = EConfigType.POLICYGROUP
            subscriptionId = ""
        }
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false, isFromDefaultSubscription = false)
        )
    }
}
