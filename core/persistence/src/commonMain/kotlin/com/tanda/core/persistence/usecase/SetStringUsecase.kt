package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.preference.SharedPreference
import org.koin.core.annotation.Factory

@Factory
class SetStringUsecase(
    private val preference: SharedPreference
) : BlockingWithArgsUseCase<SetStringUsecase.Argument, Unit> {
    override fun invoke(args: Argument) {
        return preference.set(args.key, args.value)
    }

    data class Argument(
        val key: String,
        val value: String
    )
}
