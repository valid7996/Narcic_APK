package com.narcic.ng.ui.main

import com.narcic.ng.dto.GroupMapItem
import com.narcic.ng.dto.LocateTarget

/**
 * Main UI state
 */
data class MainUiState(
    val groups: List<GroupMapItem> = emptyList(),
    val selectedGroupId: String = "",
    val selectedGuid: String? = null,
    val isRunning: Boolean = false,
    val isTesting: Boolean = false,
    val statusText: String = "",
    val livePingMillis: Long? = null,
    val locateTarget: LocateTarget? = null,
    val confirmRemove: Boolean = false,
    val doubleColumnDisplay: Boolean = false,
    val shareQRCodeBitmap: android.graphics.Bitmap? = null,
    // ---- Home "VPN" card (location / connection / subscription rows) ----
    // "" = خودکار (no country filter); otherwise the pinned flag emoji.
    val locationFlag: String = "",
    // true = اتصال خودکار (auto-pick the fastest match on toggle);
    // false = the connection row/hero use the manually chosen selectedGuid.
    val autoConnection: Boolean = true,
    // Live connection stats — only meaningful while isRunning is true.
    val downloadSpeedText: String = "",
    val uploadSpeedText: String = "",
    val connectionDurationText: String = "",
    val remoteIp: String = "",
    val remoteCountryName: String = "",
    val remoteCountryCode: String = "",
    // Monotonically increasing one-shot trigger: the Activity observes this
    // and performs a real connect (VPN permission + start) whenever it
    // changes, after AutoConnect has picked and selected the best server.
    val autoConnectRequest: Int = 0,
)

/**
 * All possible user interaction intents
 */
sealed interface MainAction {
    data object Initialize : MainAction
    data object RefreshGroups : MainAction
    data object ToggleService : MainAction
    data object AutoConnect : MainAction
    data object TestCurrentServer : MainAction
    data object TestAllServers : MainAction
    data object TestRealAllServers : MainAction

    /**
     * "تست" inside a subscription's connection picker: real-ping only the
     * configs that belong to [groupId], not every subscription.
     */
    data class TestGroupServers(val groupId: String) : MainAction
    data object CancelTesting : MainAction
    data object RemoveAllServers : MainAction
    data object RemoveDuplicateServers : MainAction
    data object RemoveInvalidServers : MainAction
    data object SortByTestResults : MainAction
    data object UpdateSubscriptions : MainAction
    data object UpdateAllSubscriptions : MainAction
    data object ExportAll : MainAction

    data object ImportQRcode : MainAction
    data object ImportClipboard : MainAction
    data object ImportConfigLocal : MainAction
    data class ImportManually(val type: Int) : MainAction
    data object RestartService : MainAction
    data object LocateSelectedServer : MainAction

    // ---- Manual VPN config (Main VPN section) ----
    /** Clipboard/QR for VPN configs -> manual with subscriptionId = "" */
    data class ImportVpnConfig(val configText: String) : MainAction
    data object ImportVpnFromClipboard : MainAction
    // ---- Subscription-only flows ----
    data class ImportSubscriptionFromClipboard(val text: String) : MainAction
    data class ImportSubscriptionFromQr(val text: String) : MainAction

    data class SelectGroup(val groupId: String) : MainAction
    data class SelectServer(val guid: String) : MainAction
    data class RemoveServer(val guid: String) : MainAction
    data class EditServer(val guid: String, val profile: com.narcic.ng.dto.entities.ProfileItem) : MainAction
    data class Search(val query: String) : MainAction
    data class ShareQRCode(val guid: String) : MainAction
    data class ShareClipboard(val guid: String) : MainAction
    data class ShareFullContent(val guid: String) : MainAction
    data class ShareLink(val guid: String) : MainAction
    data object DismissQRCodeDialog : MainAction

    data class ImportBatchConfig(val configText: String) : MainAction

    data class LocateHandled(val target: LocateTarget) : MainAction

    // ---- Home "VPN" card ----
    /** Pin a country filter ("" clears it, back to خودکار). */
    data class SetLocationFilter(val flag: String) : MainAction

    /** Switch the connection row back to خودکار (auto-pick fastest). */
    data object SetAutoConnection : MainAction

    /** Manually pin one server as the connection (from the connection picker). */
    data class SetManualConnection(val guid: String) : MainAction

    /** Refresh (تازه‌سازی) a single subscription, regardless of which tab is selected. */
    data class RefreshSubscription(val subId: String) : MainAction

    /**
     * "افزودن سابسکریپشن": [content] is either one or more subscription
     * links, or one or more raw share links (vless/vmess/trojan/ss/...),
     * pasted directly. [name] is optional — used as the new subscription's
     * remarks when the content isn't itself a link with a #fragment name.
     */
    data class AddSubscriptionFromText(val name: String, val content: String) : MainAction

    /** "سابسکریپشن‌ها" → "گزینه‌ها" → "حذف": delete a subscription and its configs. */
    data class RemoveSubscriptionGroup(val groupId: String) : MainAction
}
