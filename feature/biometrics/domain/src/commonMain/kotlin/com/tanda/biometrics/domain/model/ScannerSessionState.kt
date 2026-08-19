package com.tanda.biometrics.domain.model

sealed interface ScannerSessionState {
    data object Stopped : ScannerSessionState
    data object Starting : ScannerSessionState
    data class Initializing(val progress: Int) : ScannerSessionState
    data class Ready(
        val deviceId: Int,
        val deviceIndex: Int,
    ) : ScannerSessionState
    data class Error(val error: Throwable) : ScannerSessionState
}
