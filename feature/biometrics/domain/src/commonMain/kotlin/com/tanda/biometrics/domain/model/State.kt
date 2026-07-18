package com.tanda.biometrics.domain.model

sealed interface State {
    data object Default : State
    data class Capture(val image: Image) : State
}
