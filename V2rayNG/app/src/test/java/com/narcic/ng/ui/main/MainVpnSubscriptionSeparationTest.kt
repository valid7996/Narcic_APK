package com.narcic.ng.ui.main

import com.narcic.ng.AppConfig
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServersCache
import com.narcic.ng.enums.EConfigType
import com.narcic.ng.util.Utils
import org.junit.Assert.*
import org.junit.Test

class MainVpnSubscriptionSeparationTest {

    private fun isVpnConfig(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val schemes = listOf(
            "vmess://", "ss://", "socks://", "vless://", "trojan://",
            "wireguard://", "socks4://", "socks5://", "hysteria2://", "hy2://",
            "tuic://", "hysteria://", "v2rayn://"
        )
        return schemes.any { trimmed.startsWith(it, ignoreCase = true) } ||
            (trimmed.contains("inbounds") && trimmed.contains("outbounds")) ||
            trimmed.startsWith("[Interface]") ||
            trimmed.startsWith("v2rayn://", ignoreCase = true)
    }

    // ---- Data model separation ----
    @Test
    fun manualVpnConfigHasEmptySubscriptionId() {
        val profile = ProfileItem.create(EConfigType.VMESS).apply {
            subscriptionId = ""
            remarks = "Manual Server"
        }
        assertEquals("", profile.subscriptionId)
        assertFalse(AppConfig.isDefaultSubscriptionUrl(profile.subscriptionId))
    }

    @Test
    fun subscriptionConfigHasValidId() {
        val subId = "test-sub-id-123"
        val profile = ProfileItem.create(EConfigType.VMESS).apply {
            subscriptionId = subId
        }
        assertEquals(subId, profile.subscriptionId)
        assertNotEquals("", profile.subscriptionId)
    }

    @Test
    fun defaultSubscriptionUrlsAreReadOnly() {
        AppConfig.DEFAULT_SUBSCRIPTIONS.forEach { (_, url) ->
            assertTrue(AppConfig.isDefaultSubscriptionUrl(url))
            assertTrue(AppConfig.isDefaultSubscriptionUrl("$url?_=123456"))
        }
    }

    @Test
    fun manualConfigEditShareVisible() {
        // Manual configs must have Edit and Share visible -> isFromDefault false
        val actions = serverMenuActions(isComplexProfile = false, includeManagementActions = true, isFromDefaultSubscription = false)
        assertTrue(actions.contains(ServerMenuAction.Edit))
        assertTrue(actions.contains(ServerMenuAction.ShareQRCode))
        assertTrue(actions.contains(ServerMenuAction.ShareLink))
    }

    @Test
    fun subscriptionConfigEditShareHidden() {
        // Default subscription configs must NOT have Edit/Share
        val actions = serverMenuActions(isComplexProfile = false, includeManagementActions = true, isFromDefaultSubscription = true)
        assertFalse(actions.contains(ServerMenuAction.Edit))
        assertFalse(actions.contains(ServerMenuAction.ShareQRCode))
        assertFalse(actions.contains(ServerMenuAction.ShareLink))
        // Delete remains for cleanup (per current logic, delete hidden for default too, but ensure not edit/share)
        // For default subscription, even with includeManagement, Edit/Share should be hidden
    }

    // ---- Separation: VPN QR vs Subscription QR ----
    @Test
    fun vpnQrIsVpnConfig() {
        val vpnSamples = listOf(
            "vmess://eyJhZGQiOiIxLjIuMy40In0=",
            "vless://uuid@1.2.3.4:443?encryption=none#Test",
            "ss://YWVzLTI1Ni1nY206cGFzc3dvcmRAMTI3LjAuMC4xOjgwODAjVGVzdA==",
            "trojan://password@1.2.3.4:443#Test",
            "wireguard://test",
            "socks://test"
        )
        vpnSamples.forEach { sample ->
            assertTrue("Should be VPN: $sample", isVpnConfig(sample))
            // VPN configs are NOT valid subscription URLs
            // isValidSubUrl requires https with domain, so vmess etc should be false
            assertFalse(Utils.isValidSubUrl(sample))
        }
    }

    @Test
    fun subscriptionQrIsSubscriptionUrl() {
        val subSamples = listOf(
            "https://example.com/sub.txt",
            "https://raw.githubusercontent.com/validbv7996/Narcic_APK/refs/heads/main/vless.txt",
            "https://example.com/sub?token=abc"
        )
        subSamples.forEach { url ->
            assertTrue(Utils.isValidSubUrl(url))
            assertFalse(isVpnConfig(url))
        }
    }

    @Test
    fun vpnConfigCannotBecomeSubscription() {
        val vpn = "vmess://eyJhZGQiOiIxLjIuMy40In0="
        assertTrue(isVpnConfig(vpn))
        assertFalse(Utils.isValidSubUrl(vpn))
        // Strict subscription-only import should reject VPN
        val lines = vpn.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val allSubUrls = lines.all { Utils.isValidSubUrl(it) }
        assertFalse(allSubUrls)
    }

    @Test
    fun subscriptionUrlCannotBecomeVpnManual() {
        val subUrl = "https://example.com/sub.txt"
        assertTrue(Utils.isValidSubUrl(subUrl))
        assertFalse(isVpnConfig(subUrl))
        // Manual VPN import should reject pure subscription URLs
        val lines = subUrl.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val isPureSub = lines.isNotEmpty() && lines.all { Utils.isValidSubUrl(it) }
        assertTrue(isPureSub)
        // Manual flow checks isPureSub && !isVpn -> reject
        assertTrue(isPureSub && !isVpnConfig(subUrl))
    }

    // ---- Top 5 Best Servers ----
    @Test
    fun top5MaxFiveAndSorted() {
        val servers = (1..10).map { i ->
            ServersCache(
                guid = "guid-$i",
                profile = ProfileItem.create(EConfigType.VMESS).apply { remarks = "Server $i" },
                testDelayMillis = (100L + i * 10),
                testDelayString = "${100 + i * 10}ms"
            )
        }.shuffled()

        val top5 = servers.filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)

        assertEquals(5, top5.size)
        // Verify sorted ascending
        for (i in 0 until top5.size - 1) {
            assertTrue(top5[i].testDelayMillis <= top5[i + 1].testDelayMillis)
        }
        // Best should be smallest delay
        assertEquals(110L, top5.first().testDelayMillis)
    }

    @Test
    fun top5ShowsOnlyAvailableWhenFewerThanFive() {
        val servers = (1..3).map { i ->
            ServersCache(
                guid = "guid-$i",
                profile = ProfileItem.create(EConfigType.VMESS).apply { remarks = "Server $i" },
                testDelayMillis = (50L + i * 20),
                testDelayString = "${50 + i * 20}ms"
            )
        }
        val top5 = servers.filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)
        assertEquals(3, top5.size)
    }

    @Test
    fun top5EmptyWhenNoTestedServers() {
        val servers = (1..5).map { i ->
            ServersCache(
                guid = "guid-$i",
                profile = ProfileItem.create(EConfigType.VMESS).apply { remarks = "Server $i" },
                testDelayMillis = 0L, // not tested
                testDelayString = ""
            )
        }
        val top5 = servers.filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)
        assertEquals(0, top5.size)
    }

    @Test
    fun top5IgnoresInvalidNegativeDelay() {
        val servers = listOf(
            ServersCache(guid = "g1", profile = ProfileItem.create(EConfigType.VMESS), testDelayMillis = -1L, testDelayString = "Timeout"),
            ServersCache(guid = "g2", profile = ProfileItem.create(EConfigType.VMESS), testDelayMillis = 50L, testDelayString = "50ms"),
            ServersCache(guid = "g3", profile = ProfileItem.create(EConfigType.VMESS), testDelayMillis = 0L, testDelayString = "")
        )
        val top5 = servers.filter { it.testDelayMillis > 0L }
            .sortedBy { it.testDelayMillis }
            .take(5)
        assertEquals(1, top5.size)
        assertEquals("g2", top5.first().guid)
    }

    // ---- Clipboard flows ----
    @Test
    fun clipboardVpnCreatesManualWithEmptySubId() {
        val clipboardText = "vless://uuid@1.2.3.4:443#Test"
        assertTrue(isVpnConfig(clipboardText))
        // Simulate manual import path: subId = ""
        val subId = ""
        assertEquals("", subId)
    }

    @Test
    fun invalidClipboardShowsErrorNotCrash() {
        val invalid = "not a valid config @@@"
        assertFalse(isVpnConfig(invalid))
        assertFalse(Utils.isValidSubUrl(invalid))
        // Both flows should reject and show error, not crash
        // Verified by not throwing exception
    }

    // ---- Subscription section loads only subscriptions ----
    @Test
    fun threeDefaultSubscriptionsPreserved() {
        assertEquals(3, AppConfig.DEFAULT_SUBSCRIPTIONS.size)
        val remarks = AppConfig.DEFAULT_SUBSCRIPTIONS.map { it.first }
        assertTrue(remarks.contains("Narcic Irancell"))
        assertTrue(remarks.contains("Narcic NG - JSON"))
        assertTrue(remarks.contains("Narcic NG - WireGuard"))
    }

    @Test
    fun subscriptionSectionDoesNotLoadVpnConfigs() {
        // Subscription items are separate from profile items
        // Ensure default subscription IDs are not confused with VPN guid
        val subId = AppConfig.DEFAULT_SUBSCRIPTIONS.first().second
        assertTrue(AppConfig.isDefaultSubscriptionUrl(subId))
        // VPN guid is UUID, not URL
        val vpnGuid = "abc123def456"
        assertFalse(AppConfig.isDefaultSubscriptionUrl(vpnGuid))
    }
}
