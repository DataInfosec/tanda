package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.preference.SharedPreference
import org.koin.core.annotation.Factory

@Factory
class ClearStringUsecase(
    private val preference: SharedPreference
) : BlockingWithArgsUseCase<String, Unit> {
    override fun invoke(args: String) {
        return preference.remove(args)
    }
}
