package com.tanda.core.common.usecase

interface SuspendUseCase<T> : Usecase {
    suspend operator fun invoke(): T
}
