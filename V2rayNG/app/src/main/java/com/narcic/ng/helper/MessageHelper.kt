package com.narcic.ng.helper

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.narcic.ng.AppConfig
import com.narcic.ng.dto.SubscriptionUpdateMessage
import com.narcic.ng.dto.TestServiceMessage
import com.narcic.ng.service.CoreTestService
import com.narcic.ng.service.SubscriptionUpdateService
import com.narcic.ng.util.LogUtil
import java.io.Serializable

object MessageHelper {


    /**
     * Sends a message to the service.
     *
     * @param ctx The context.
     * @param what The message identifier.
     * @param content The message content.
     */
    fun sendMsg2Service(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_SERVICE, what, content)
    }

    /**
     * Sends an ordered service message and reports whether a daemon receiver handled it.
     * With no running daemon, the initial canceled result reaches [onResult] unchanged.
     */
    internal fun sendMsg2ServiceForResult(
        ctx: Context,
        what: Int,
        content: Serializable,
        onResult: (handled: Boolean) -> Unit,
    ) {
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onResult(resultCode == Activity.RESULT_OK)
            }
        }
        try {
            ctx.sendOrderedBroadcast(
                Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
                    `package` = AppConfig.ANG_PACKAGE
                    putExtra("key", what)
                    putExtra("content", content)
                },
                null,
                resultReceiver,
                null,
                Activity.RESULT_CANCELED,
                null,
                null,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send ordered message to service", e)
            onResult(false)
        }
    }

    /**
     * Sends a message to the UI.
     *
     * @param ctx The context.
     * @param what The message identifier.
     * @param content The message content.
     */
    fun sendMsg2UI(ctx: Context, what: Int, content: Serializable) {
        sendMsg(ctx, AppConfig.BROADCAST_ACTION_ACTIVITY, what, content)
    }

    /**
     * Sends a delay-measurement result to the UI, carrying both the
     * localized display string AND the raw measured delay in milliseconds
     * (-1 if the measurement failed), so the UI can sync its live ping
     * indicator instead of only showing formatted text.
     */
    fun sendMeasureDelayResult(ctx: Context, content: String, delayMillis: Long) {
        try {
            val intent = Intent()
            intent.action = AppConfig.BROADCAST_ACTION_ACTIVITY
            intent.`package` = AppConfig.ANG_PACKAGE
            intent.putExtra("key", AppConfig.MSG_MEASURE_DELAY_SUCCESS)
            intent.putExtra("content", content)
            intent.putExtra("delayMillis", delayMillis)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send measure delay result", e)
        }
    }

    /**
     * Sends a message to the test service.
     *
     * @param ctx The context.
     * @param message The test service message containing key, subscriptionId, and serverGuids.
     */
    fun sendMsg2TestService(ctx: Context, message: TestServiceMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, CoreTestService::class.java)
            intent.putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_MEASURE_CONFIG_START -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }

                AppConfig.MSG_MEASURE_CONFIG_CANCEL -> {
                    // Do not wake up service just to cancel; stop only if it is already running.
                    ctx.stopService(intent)
                }

                else -> {
                    ctx.startService(intent)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message to test service", e)
        }
    }

    /**
     * Sends a message to the subscription service.
     *
     * @param ctx The context.
     * @param message The subscription service message containing key and subId.
     */
    fun sendMsg2SubscriptionService(ctx: Context, message: SubscriptionUpdateMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, SubscriptionUpdateService::class.java)
            intent.putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_SUB_UPDATE_START -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(ctx, intent)
                    } else {
                        ctx.startService(intent)
                    }
                }

                AppConfig.MSG_SUB_UPDATE_CANCEL -> {
                    ctx.stopService(intent)
                }

                else -> {
                    ctx.startService(intent)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message to subscription service", e)
        }
    }

    /**
     * Sends a message with the specified action.
     *
     * @param ctx The context.
     * @param action The action string.
     * @param what The message identifier.
     * @param content The message content.
     */
    private fun sendMsg(ctx: Context, action: String, what: Int, content: Serializable) {
        try {
            val intent = Intent()
            intent.action = action
            intent.`package` = AppConfig.ANG_PACKAGE
            intent.putExtra("key", what)
            intent.putExtra("content", content)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send message with action: $action", e)
        }
    }
}