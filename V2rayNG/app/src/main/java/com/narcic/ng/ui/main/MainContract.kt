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
    /** Bumped on every MSG_STATE_START_FAILURE so the home screen's local
     *  "connecting..." spinner can clear immediately instead of waiting out
     *  its guess-timeout when the service reports a real, prompt failure. */
    val connectFailedTick: Int = 0,
    val isTesting: Boolean = false,
    val isAwgConnecting: Boolean = false,
    val awgConnectingMessage: String = "",
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
    val remoteCity: String = "",
    val remoteIsp: String = "",
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
    /**
     * Quick-setup for "Warp on Warp" (WoW): opens the existing generic
     * Proxy Chain screen pre-configured to chain two WireGuard/AmneziaWG
     * profiles together (one WireGuard tunnel dialed through another),
     * instead of making the user find the generic Proxy Chain entry and
     * work out which profile types are chainable on their own.
     */
    data object ImportWarpOnWarp : MainAction
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

    // When the user taps the connection card on the home screen:
    //  - autoConnection == true -> toggle the whole service (connects via the auto-picked server)
    //  - autoConnection == false -> open the bottom-sheet connection picker so they can select a server
    data object SetAutoConnection : MainAction
    data class SetManualConnection(val guid: String) : MainAction

    // ---- Subscriptions screen actions ----
    data class RefreshSubscription(val subId: String) : MainAction
    data class AddSubscriptionFromText(val name: String, val content: String) : MainAction
    data class RemoveSubscriptionGroup(val groupId: String) : MainAction
    data class EditSubscription(val groupId: String, val name: String, val url: String) : MainAction
}
