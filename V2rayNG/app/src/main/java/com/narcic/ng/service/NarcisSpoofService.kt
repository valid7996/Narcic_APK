package com.narcic.ng.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.narcic.ng.R
import com.narcic.ng.enums.NotificationChannelType
import com.narcic.ng.helper.NotificationHelper
import com.narcic.ng.rsta.NarcisSpoofEngine

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

                NarcisSpoofEngine.start(ip, port, sni, method)
            }

            "STOP" -> {
                NarcisSpoofEngine.stop()
                NotificationHelper.stopForeground(this)
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
