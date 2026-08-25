package com.narcic.ng.ui.main

sealed class MainServiceEvent {
    data object StateRunning : MainServiceEvent()
    data object StateNotRunning : MainServiceEvent()
    data object StateStartSuccess : MainServiceEvent()
    data class StateStartFailure(val errorMessage: String) : MainServiceEvent()
    data object StateStopSuccess : MainServiceEvent()
    data class MeasureDelaySuccess(val content: String, val delayMillis: Long = -1L) : MainServiceEvent()
    data class MeasureConfigSuccess(val guid: String) : MainServiceEvent()
    data class MeasureConfigNotify(val progress: String) : MainServiceEvent()
    data class MeasureConfigFinish(val finishedCount: String?) : MainServiceEvent()
    data class TrafficUpdate(val downloadBps: Long, val uploadBps: Long) : MainServiceEvent()
}
