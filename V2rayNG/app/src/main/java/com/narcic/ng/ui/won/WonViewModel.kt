package com.narcic.ng.ui.won

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.narcic.ng.AppConfig
import com.narcic.ng.won.ConnectionLog
import com.narcic.ng.won.TorManager
import com.narcic.ng.won.TunnelStatus
import com.narcic.ng.won.WonController
import com.narcic.ng.won.WonVpnService
import com.narcic.ng.util.Utils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI state of the W on N card, fed entirely by the status broadcasts the ported
 * [WonVpnService] emits (the same extras MSN-GUARD's UI consumed).
 */
data class WonUiState(
    val status: String = WonVpnService.STATUS_DISCONNECTED,
    val detail: String = "",
    val progress: Int = -1,
    val modeLabel: String = "",
    val country: String = "",
    val exitIp: String = "",
    val speedTx: Long = 0,
    val speedRx: Long = 0,
    val monthTx: Long = 0,
    val monthRx: Long = 0,
    val sessionSeconds: Long = 0,
    val logs: List<String> = emptyList(),
) {
    val isRunning: Boolean get() = status == WonVpnService.STATUS_CONNECTED
    val isConnecting: Boolean
        get() = status == WonVpnService.STATUS_CONNECTING ||
            status == WonVpnService.STATUS_STARTING ||
            status == WonVpnService.STATUS_SCANNING
}

/**
 * Plain class (not an AndroidViewModel) so the Compose layer can construct it
 * directly with `remember` — no lifecycle-viewmodel-compose artifact needed.
 * Call [release] from a DisposableEffect when the screen leaves composition.
 */
class WonViewModel(private val app: android.app.Application) {

    private val _uiState = MutableStateFlow(WonUiState())
    val uiState: StateFlow<WonUiState> = _uiState.asStateFlow()

    /** One-shot flag the screen observes to refresh the log while connected. */
    private var logRefreshJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when {
                // Traffic and exit-ip share ACTION_STATUS with the status
                // broadcast but carry disjoint extras (same as MSN-GUARD).
                intent.hasExtra(WonVpnService.EXTRA_TRAFFIC_TX) -> onTraffic(
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_TX, 0),
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_RX, 0),
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_SPEED_TX, 0),
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_SPEED_RX, 0),
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_MONTH_TX, 0),
                    intent.getLongExtra(WonVpnService.EXTRA_TRAFFIC_MONTH_RX, 0),
                )

                intent.hasExtra(WonVpnService.EXTRA_EXIT_IP) ->
                    onExitIp(intent.getStringExtra(WonVpnService.EXTRA_EXIT_IP).orEmpty())

                intent.hasExtra(WonVpnService.EXTRA_STATUS) -> handleStatus(intent)
            }
        }
    }

    init {
        val filter = IntentFilter(WonVpnService.ACTION_STATUS)
        ContextCompat.registerReceiver(
            app,
            receiver,
            filter,
            Utils.receiverFlags(),
        )
        // Tile/service may have connected before this screen ever opened.
        if (TunnelStatus.isActive()) {
            _uiState.update { it.copy(status = WonVpnService.STATUS_CONNECTED) }
        }
        startSessionTimer()
        refreshLogs()
    }

    private fun handleStatus(intent: Intent) {
        val status = intent.getStringExtra(WonVpnService.EXTRA_STATUS) ?: return
        val detail = intent.getStringExtra(WonVpnService.EXTRA_DETAIL).orEmpty()
        val progress = intent.getIntExtra(WonVpnService.EXTRA_PROGRESS, -1)
        _uiState.update { state ->
            state.copy(
                status = status,
                detail = detail.ifBlank { state.detail },
                progress = progress,
            )
        }
        if (status == WonVpnService.STATUS_CONNECTED) {
            refreshLogs()
        }
    }

    /** Live traffic broadcast (per-second poll or core event). */
    fun onTraffic(tx: Long, rx: Long, speedTx: Long, speedRx: Long, monthTx: Long, monthRx: Long) {
        _uiState.update {
            it.copy(
                speedTx = speedTx,
                speedRx = speedRx,
                monthTx = monthTx,
                monthRx = monthRx,
            )
        }
    }

    fun onExitIp(ip: String) {
        _uiState.update { it.copy(exitIp = ip) }
    }

    fun onCountry(country: String) {
        _uiState.update { it.copy(country = country) }
    }

    fun setModeLabel(label: String) {
        _uiState.update { it.copy(modeLabel = label) }
    }

    fun refreshLogs() {
        _uiState.update { it.copy(logs = ConnectionLog.snapshot().takeLast(40)) }
    }

    /** Tell the service to tear the current tunnel down (dial tap / cancel). */
    fun disconnect() {
        WonController.disconnect(app)
    }

    /** Session clock driven off WonVpnService.connectedSinceElapsed() (survives UI recreation). */
    private fun startSessionTimer() {
        logRefreshJob?.cancel()
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
        )
        logRefreshJob = scope.launch {
            while (isActive) {
                val since = WonVpnService.connectedSinceElapsed()
                val seconds = if (since > 0) (android.os.SystemClock.elapsedRealtime() - since) / 1000 else 0
                _uiState.update { it.copy(sessionSeconds = seconds) }
                if (TunnelStatus.isActive()) refreshLogs()
                delay(1_000)
            }
        }
    }

    /** The transport actually carrying the tunnel right now, for the status line. */
    fun currentProtocolLabel(): String {
        val base = _uiState.value.modeLabel
        val torMode = TorManager.activeMode?.label
        return when {
            base.contains("Tor", true) && torMode != null -> "Tor ($torMode)"
            base.isNotBlank() -> base
            else -> "W on N"
        }
    }

    /** Unregister the broadcast receiver and stop the timer. Call from onDispose. */
    fun release() {
        logRefreshJob?.cancel()
        logRefreshJob = null
        try {
            app.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}
