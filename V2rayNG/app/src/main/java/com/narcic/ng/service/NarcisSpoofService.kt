package com.narcic.ng.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.narcic.ng.R
import com.narcic.ng.enums.NotificationChannelType
import com.narcic.ng.helper.NotificationHelper
import com.narcic.ng.rsta.NarcisSpoofEngine
import com.narcic.ng.util.LogUtil
import com.narcic.ng.AppConfig

/**
 * Foreground service that hosts the «نرسیس اسپوف» (Narcis Spoof) native engine
 * while it's forwarding traffic. Started/stopped by [com.narcic.ng.core.CoreServiceManager]
 * whenever the selected server points at the engine's local listener
 * (see [com.narcic.ng.rsta.NarcisSpoofConfig.isSpoofTarget]), or manually from
 * the settings screen.
 */
class NarcisSpoofService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val ip = intent.getStringExtra("IP") ?: ""
                val port = intent.getIntExtra("PORT", 0)
                val sni = intent.getStringExtra("SNI") ?: ""
                val method = intent.getStringExtra("METHOD") ?: ""

                NotificationHelper.startForeground(
                    this,
                    NotificationChannelType.NARCIS_SPOOF,
                    getString(R.string.title_narcis_spoof_setting),
                    getString(R.string.notify_narcis_spoof_running)
                )

                // Validate before touching the native bridge: a replayed or
                // malformed intent must fail cleanly, not reach spfStart.
                val validationError = NarcisSpoofEngine.validate(ip, port, sni, method)
                if (validationError != null) {
                    LogUtil.e(AppConfig.TAG, "NarcisSpoof: invalid START extras: $validationError")
                    NarcisSpoofEngine.appendPublicLog("rejected START: $validationError")
                    NotificationHelper.stopForeground(this)
                    stopSelf()
                    return START_NOT_STICKY
                }

                val ok = NarcisSpoofEngine.start(ip, port, sni, method)
                if (!ok) {
                    // Engine refused — do not leave an orphaned foreground
                    // notification claiming the forwarder is running.
                    NotificationHelper.stopForeground(this)
                    stopSelf()
                }
            }

            "STOP" -> {
                NarcisSpoofEngine.stop()
                NotificationHelper.stopForeground(this)
                stopSelf()
            }

            else -> {
                // Restart delivery (null intent) or unknown action: this is a
                // specialUse FGS — startForeground must run promptly, but with
                // no valid work to do the right move is to come to foreground
                // briefly and immediately stop so the system contract is met
                // without a stale notification.
                NotificationHelper.startForeground(
                    this,
                    NotificationChannelType.NARCIS_SPOOF,
                    getString(R.string.title_narcis_spoof_setting),
                    getString(R.string.notify_narcis_spoof_running)
                )
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        NarcisSpoofEngine.stop()
        super.onDestroy()
    }
}
