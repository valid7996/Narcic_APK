package com.narcic.ng.aether.shared.core

import com.narcic.ng.aether.platform.PlatformContext
import com.narcic.ng.aether.shared.model.ConnectionStatus
import com.narcic.ng.aether.shared.model.SessionTraffic
import com.narcic.ng.aether.shared.platform.Bridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Standalone mirror of the connection status, driven by Bridge overrides.
// (Ported from AetherST KMP; the Android engine's own controller lives in
// com.narcic.ng.aether.core.ConnectionController — this object only reflects
// status for UI consumers that observe the shared layer.)
object SharedConnectionController {
    private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sessionTraffic = MutableStateFlow(SessionTraffic())
    val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

    private val _isWaitingForCode = MutableStateFlow(false)
    val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            Bridge.statusOverride.collect { s ->
                if (s != null) {
                    _status.value = s
                    if (s == ConnectionStatus.STOPPED || s == ConnectionStatus.ERROR || s == ConnectionStatus.FAILED) {
                        _elapsedSeconds.value = 0L
                        _sessionTraffic.value = SessionTraffic()
                    }
                }
            }
        }
        scope.launch {
            Bridge.elapsedOverride.collect { e ->
                if (e != null) {
                    _elapsedSeconds.value = e
                }
            }
        }
        scope.launch {
            Bridge.trafficOverride.collect { t ->
                if (t != null) {
                    _sessionTraffic.value = t
                }
            }
        }
        scope.launch {
            Bridge.isWaitingForCode.collect { waiting ->
                if (waiting != null) {
                    _isWaitingForCode.value = waiting
                }
            }
        }
    }

    fun getInstance(context: PlatformContext) {}

    fun markStatus(status: ConnectionStatus) {
        _status.value = status
        if (status == ConnectionStatus.STOPPED || status == ConnectionStatus.ERROR || status == ConnectionStatus.FAILED) {
            _elapsedSeconds.value = 0L
            _sessionTraffic.value = SessionTraffic()
            Bridge.elapsedOverride.value = 0L
            Bridge.trafficOverride.value = SessionTraffic()
        }
    }
}
