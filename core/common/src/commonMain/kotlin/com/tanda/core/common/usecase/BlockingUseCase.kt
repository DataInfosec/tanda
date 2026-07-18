package com.tanda.core.common.usecase

interface BlockingUseCase<T> : Usecase {
    operator fun invoke(): T
}
