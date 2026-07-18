package com.tanda.core.common.usecase

interface SuspendWithArgsUseCase<A, T> : Usecase {
    suspend operator fun invoke(args: A): T
}
