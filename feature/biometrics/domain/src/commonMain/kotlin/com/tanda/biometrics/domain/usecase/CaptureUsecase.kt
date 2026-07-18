package com.tanda.biometrics.domain.usecase

import com.tanda.biometrics.domain.model.Option
import com.tanda.biometrics.domain.model.Posture
import com.tanda.biometrics.domain.repository.ScannerRepository
import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class CaptureUsecase(
    private val repository: ScannerRepository
) : BlockingWithArgsUseCase<CaptureUsecase.Argument, Unit> {
    override fun invoke(args: Argument) {
        return repository.capture(
            posture = args.posture,
            index = args.index,
            option = args.option
        )
    }

    data class Argument(
        val posture: Posture,
        val index: Int,
        val option: Option
    )
}
