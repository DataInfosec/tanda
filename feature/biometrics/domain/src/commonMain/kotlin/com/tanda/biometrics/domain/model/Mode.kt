package com.tanda.biometrics.domain.model

sealed interface Mode {
    data object Default : Mode
    data class Process(val type: String) : Mode
    data class Acquired(val type: String) : Mode
    data class Platen(val platen: String) : Mode
    data class Error(val error: Throwable) : Mode
}
