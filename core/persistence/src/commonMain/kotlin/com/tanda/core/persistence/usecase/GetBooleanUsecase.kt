package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.BlockingWithArgsUseCase
import com.tanda.core.persistence.preference.SharedPreference
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class GetBooleanUsecase(
    private val preference: SharedPreference
) : BlockingWithArgsUseCase<String, Boolean> {
    override fun invoke(args: String): Boolean {
        return preference.get(args, typeOf<Boolean>()) ?: false
    }
}
