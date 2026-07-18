package com.tanda.biometrics.domain.model

sealed interface Event {
    data object Default : Event
    data class Process(val type: String) : Event
    data class Complete(val type: String) : Event
    data class Ready(val platen: String) : Event
    data class Error(val error: Throwable) : Event
}
