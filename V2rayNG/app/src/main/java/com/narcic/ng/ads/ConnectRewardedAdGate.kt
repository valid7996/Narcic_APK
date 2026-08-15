package com.narcic.ng.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.narcic.ng.AppConfig
import com.narcic.ng.util.LogUtil
import ir.tapsell.sdk.Tapsell
import ir.tapsell.sdk.TapsellAdRequestListener
import ir.tapsell.sdk.TapsellAdRequestOptions
import ir.tapsell.sdk.TapsellAdShowListener
import ir.tapsell.sdk.TapsellShowOptions

/**
 * Gates a VPN connection behind a Tapsell rewarded video ad: the user must watch the ad
 * to the end before [Callback.onAllowConnect] fires. Used from the Connect button so the
 * app only "connects" after a completed ad view.
 *
 * Behavior:
 *  - Ad requested -> shown -> watched fully (onRewarded(true))  => allow connect
 *  - Ad requested -> shown -> closed early / skipped            => block connect
 *  - No ad available in time (no fill / no network / timeout)   => allow connect
 *    (fails open, so a Tapsell outage or empty inventory never fully locks users out
 *    of the VPN. Flip [FAIL_OPEN_ON_NO_AD] to false for strict "must watch an ad" behavior.)
 */
object ConnectRewardedAdGate {

    private const val TAG = AppConfig.TAG
    private const val REQUEST_TIMEOUT_MS = 8_000L
    private const val FAIL_OPEN_ON_NO_AD = true

    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        /** User may proceed to connect (ad fully watched, or none was available). */
        fun onAllowConnect()

        /** An ad was shown but not watched to completion; do not connect. */
        fun onBlockConnect()
    }

    /**
     * Requests + shows a rewarded ad for [AppConfig.TAPSELL_ZONE_ID_CONNECT], then reports
     * the result via [callback]. Safe to call repeatedly (e.g. every time the Connect
     * button is tapped); each call is independent.
     */
    fun requestAndShow(context: Context, callback: Callback) {
        val zoneId = AppConfig.TAPSELL_ZONE_ID_CONNECT
        if (zoneId.isBlank()) {
            callback.onAllowConnect()
            return
        }

        var settled = false
        fun settle(block: () -> Unit) {
            if (settled) return
            settled = true
            block()
        }

        val timeoutRunnable = Runnable {
            settle {
                LogUtil.e(TAG, "Tapsell ad request timed out before connect")
                if (FAIL_OPEN_ON_NO_AD) callback.onAllowConnect() else callback.onBlockConnect()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, REQUEST_TIMEOUT_MS)

        val requestOptions = TapsellAdRequestOptions(TapsellAdRequestOptions.CACHE_TYPE_CACHED)
        Tapsell.requestAd(
            context,
            zoneId,
            requestOptions,
            object : TapsellAdRequestListener() {
                override fun onAdAvailable(adId: String) {
                    if (settled) return
                    mainHandler.removeCallbacks(timeoutRunnable)
                    showAd(context, zoneId, adId, callback) { settle {} }
                }

                override fun onError(message: String?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    settle {
                        LogUtil.e(TAG, "Tapsell ad request failed before connect: $message")
                        if (FAIL_OPEN_ON_NO_AD) callback.onAllowConnect() else callback.onBlockConnect()
                    }
                }
            }
        )
    }

    private fun showAd(
        context: Context,
        zoneId: String,
        adId: String,
        callback: Callback,
        markSettled: () -> Unit
    ) {
        var rewarded = false
        val showOptions = TapsellShowOptions().apply {
            setBackDisabled(true)
            setImmersiveMode(true)
            setRotationMode(TapsellShowOptions.ROTATION_UNLOCKED)
        }

        Tapsell.showAd(
            context,
            zoneId,
            adId,
            showOptions,
            object : TapsellAdShowListener() {
                override fun onOpened() {
                    // no-op
                }

                override fun onRewarded(completed: Boolean) {
                    rewarded = completed
                }

                override fun onClosed() {
                    markSettled()
                    if (rewarded) callback.onAllowConnect() else callback.onBlockConnect()
                }

                override fun onError(message: String?) {
                    markSettled()
                    LogUtil.e(TAG, "Tapsell ad show failed before connect: $message")
                    if (FAIL_OPEN_ON_NO_AD) callback.onAllowConnect() else callback.onBlockConnect()
                }
            }
        )
    }
}
