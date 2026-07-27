package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Finger
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class CaptureUsecase(
    private val repository: ScannerRepository
) : SuspendWithArgsUseCase<CaptureUsecase.Argument, Unit> {
    override suspend fun invoke(args: Argument) {
        return repository.capture(
            finger = args.finger,
            index = args.index,
            option = args.option
        )
    }

    data class Argument(
        val finger: Finger,
        val index: Int,
        val option: Option
    )
}
