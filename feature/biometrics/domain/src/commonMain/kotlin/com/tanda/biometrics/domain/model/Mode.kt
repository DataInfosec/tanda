package com.tanda.biometrics.domain.model

sealed interface Mode {
    data object Default : Mode
    data class Process(val type: String) : Mode
    data class Complete(val type: String) : Mode
    data class Ready(val platen: String) : Mode
    data class Error(val error: Throwable) : Mode
}
