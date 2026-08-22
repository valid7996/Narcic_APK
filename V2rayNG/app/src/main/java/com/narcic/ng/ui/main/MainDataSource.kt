package com.narcic.ng.ui.main

import com.narcic.ng.dto.SubscriptionUpdateResult
import com.narcic.ng.dto.TestServiceMessage
import com.narcic.ng.dto.entities.ProfileItem
import com.narcic.ng.dto.entities.ServerAffiliationInfo
import com.narcic.ng.dto.entities.SubscriptionCache
import com.narcic.ng.dto.entities.SubscriptionItem
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    fun setSelectServer(guid: String)

    fun getLocationFlag(): String
    fun setLocationFlag(flag: String)

    fun getAutoConnection(): Boolean
    fun setAutoConnection(auto: Boolean)

    /**
     * "تنظیم اتصال خودکار": the ping (ms) at/under which خودکار (AutoConnect)
     * should stop testing and connect immediately. 0 means the limit is
     * disabled — AutoConnect tests every config and picks the fastest one.
     */
    fun getAutoConnectPingLimitMillis(): Long

    fun getConfirmRemove(): Boolean
    fun getDoubleColumnDisplay(): Boolean
    fun isGroupAllDisplayEnabled(): Boolean

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?

    fun encodeServerList(guids: List<String>, groupId: String)

    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun removeInvalidServersInGroup(groupId: String): Int

    /** Deletes a subscription group and every config that belongs to it. */
    fun removeSubscription(groupId: String)

    fun clearAllTestDelayResults(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)
    fun getSubsList(): List<String>

    suspend fun importBatchConfig(
        server: String?,
        subscriptionId: String,
        updateUI: Boolean
    ): Pair<Int, Int>

    /**
     * Backing implementation for [MainAction.AddSubscriptionFromText]. Detects
     * whether [content] is one/more subscription links or raw share links:
     *  - Subscription link(s): a new subscription is created per link (named
     *    [name] when given) and fetched immediately.
     *  - Raw share link(s): a single new *local* subscription named [name]
     *    (or a sensible default) is created up front, and every parsed
     *    config is imported into it — never mixed into whatever subscription
     *    happens to be selected on screen.
     * Returns the number of configs and the number of subscriptions created.
     */
    suspend fun createSubscriptionFromText(name: String, content: String): Pair<Int, Int>

    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult

    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun share2QRCode(guid: String): android.graphics.Bitmap?
    fun share2Clipboard(guid: String): Boolean

    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage)
    fun cancelAllPing()
    fun testCurrentServerRealPing()

    fun syncSubscriptions()
    fun initAssets()
}
