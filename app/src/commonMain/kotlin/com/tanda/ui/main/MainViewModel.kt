package com.tanda.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanda.account.domain.usecase.ObserveTokenUsecase
import com.tanda.biometrics.domain.model.ScannerSessionState
import com.tanda.biometrics.domain.model.DeviceConfiguration
import com.tanda.biometrics.domain.repository.DeviceConfigurationRepository
import com.tanda.biometrics.domain.session.ScannerSessionManager
import com.tanda.core.common.concurrent.Dispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainViewModel(
    private val dispatcher: Dispatcher,
    private val observeTokenUsecase: ObserveTokenUsecase,
    private val deviceConfigurationRepository: DeviceConfigurationRepository,
    private val scannerSessionManager: ScannerSessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow<State>(State.Default)
    private val _effect = MutableSharedFlow<Effect>(
        extraBufferCapacity = 1
    )

    val state: StateFlow<State> = _state.asStateFlow()
    val effect: SharedFlow<Effect> = _effect.asSharedFlow()
    val scannerState: StateFlow<ScannerSessionState> = scannerSessionManager.state

    fun retryScanner() {
        scannerSessionManager.retry()
    }

    fun configureDevice(deviceInstanceId: String, fingerprintToken: String) {
        deviceConfigurationRepository.save(
            DeviceConfiguration(
                deviceInstanceId = deviceInstanceId,
                fingerprintToken = fingerprintToken,
            )
        )
        scannerSessionManager.start()
    }

    operator fun invoke() {
        viewModelScope.launch(dispatcher.io) {
            try {
                _state.tryEmit(State.Loading)
                combine(
                    observeTokenUsecase(),
                    deviceConfigurationRepository.observe(),
                ) { token, configuration ->
                    State.Success(
                        authenticated = !token.isNullOrBlank(),
                        deviceConfigured = configuration != null,
                    )
                }.collect(_state::emit)
            } catch (error: Throwable) {
                _state.tryEmit(State.Error(error))
            }
        }
        viewModelScope.launch {
            observeTokenUsecase.expiration().collect {
                _effect.tryEmit(Effect.SessionExpired)
            }
        }
    }

    sealed interface State {
        data object Default : State
        data object Loading : State
        data class Success(
            val authenticated: Boolean,
            val deviceConfigured: Boolean,
        ) : State
        data class Error(val error: Throwable) : State
    }

    sealed interface Effect {
        data object SessionExpired : Effect
    }
}
