package com.tanda.biometrics.domain.model

sealed interface Snapshot {
    data object Default : Snapshot
    data class Capture(val image: Image) : Snapshot
}
