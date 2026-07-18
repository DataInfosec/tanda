package com.tanda.core.common.usecase

import kotlinx.coroutines.flow.Flow

interface ObservableWithArgsUseCase<A, T> : Usecase {
    operator fun invoke(args: A): Flow<T>
}
