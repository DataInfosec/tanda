package com.tanda.account.domain.usecase

import com.tanda.account.domain.repository.AuthenticationRepository
import com.tanda.core.common.usecase.SuspendWithArgsUseCase
import org.koin.core.annotation.Factory

@Factory
class LoginUsecase(
    private val repository: AuthenticationRepository
) : SuspendWithArgsUseCase<LoginUsecase.Argument, String> {
    override suspend fun invoke(args: Argument): String {
        return repository.login(
            login = args.login,
            password = args.password
        )
    }
    
    data class Argument(
        val login: String,
        val password: String,
    )
}
