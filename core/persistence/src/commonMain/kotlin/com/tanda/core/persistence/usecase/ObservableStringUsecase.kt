package com.tanda.core.persistence.usecase

import com.tanda.core.common.usecase.ObservableWithArgsUseCase
import com.tanda.core.persistence.preference.SharedPreference
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory
import kotlin.reflect.typeOf

@Factory
class ObservableStringUsecase(
    private val preference: SharedPreference
) : ObservableWithArgsUseCase<String, String?> {
    override fun invoke(args: String): Flow<String?> {
        return preference.observe(args, typeOf<String>())
    }
}
