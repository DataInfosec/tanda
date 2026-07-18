package com.tanda.biometrics.domain.model

sealed interface Status {
    data object Default : Status
    data class Attached(val id: Int) : Status
    data class Detached(val id: Int) : Status
    data class Initialize(
        val index: Int,
        val progress: Int
    ) : Status
    data class Ready(val index: Int) : Status
    data class Error(val error: Throwable) : Status
}
